package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.frontend.fuse.ReadWriteAdapter;
import org.cryptomator.frontend.fuse.microsoft.MicrosoftOfficeLockingAdapter;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.mount.Mount;
import org.cryptomator.integrations.mount.MountBuilder;
import org.cryptomator.integrations.mount.MountCapability;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountService;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.cryptomator.integrations.mount.MountCapability.FILE_SYSTEM_NAME;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_AS_DRIVE_LETTER;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_WITHIN_EXISTING_PARENT;
import static org.cryptomator.integrations.mount.MountCapability.READ_ONLY;
import static org.cryptomator.integrations.mount.MountCapability.UNMOUNT_FORCED;
import static org.cryptomator.integrations.mount.MountCapability.VOLUME_NAME;

@Priority(990)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WinFspMountProvider implements MountService {

	private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();

	@Override
	public String displayName() {
		return "WinFsp (Local Drive)";
	}

	@Override
	public boolean isSupported() {
		return WinfspUtil.isWinFspInstalled();
	}

	@Override
	public Set<MountCapability> capabilities() {
		return EnumSet.of(MOUNT_FLAGS, MOUNT_AS_DRIVE_LETTER, MOUNT_WITHIN_EXISTING_PARENT, UNMOUNT_FORCED, READ_ONLY, VOLUME_NAME, FILE_SYSTEM_NAME);
	}

	@Override
	public String getDefaultMountFlags() {
		// see: https://github.com/winfsp/winfsp/blob/84b3f98d383b265ebdb33891fc911eaafb878497/src/dll/fuse/fuse.c#L628
		return "-ouid=-1 -ogid=-1";
	}

	@Override
	public MountBuilder forFileSystem(Path vfsRoot) {
		return new WinFspMountBuilder(vfsRoot);
	}

	protected static class WinFspMountBuilder extends AbstractMountBuilder {

		private static String DEFAULT_FS_NAME = "FUSE-NIO-FS";
		String fsName = DEFAULT_FS_NAME;
		boolean isReadOnly = false;

		WinFspMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountBuilder setMountpoint(Path mountPoint) {
			if (mountPoint.getRoot().equals(mountPoint) // MOUNT_AS_DRIVE_LETTER
					|| Files.isDirectory(mountPoint.getParent()) && Files.notExists(mountPoint)) { // MOUNT_WITHIN_EXISTING_PARENT
				this.mountPoint = mountPoint;
				return this;
			} else {
				throw new IllegalArgumentException("mount point must either be a drive letter or a non-existing node within an existing parent");
			}
		}

		@Override
		public MountBuilder setFileSystemName(String fsName) {
			this.fsName = fsName;
			return this;
		}

		@Override
		public MountBuilder setReadOnly(boolean mountReadOnly) {
			isReadOnly = mountReadOnly;
			return this;
		}

		/**
		 * Combines the {@link #setMountFlags(String) mount flags} with any additional option that might have
		 * been set separately.
		 *
		 * @return Mutable set of all currently set mount options
		 */
		protected Set<String> combinedMountFlags() {
			var combined = super.combinedMountFlags();
			if (isReadOnly) {
				combined.removeIf(flag -> flag.startsWith("-oumask="));
				combined.add("-oumask=0333");
			}
			combined.removeIf(flag -> flag.startsWith("-oExactFileSystemName="));
			combined.add("-oExactFileSystemName=" + fsName);
			if(volumeName != null && !volumeName.isBlank()) {
				combined.removeIf(flag -> flag.startsWith("-ovolname="));
				combined.add("-ovolname=" + volumeName);
			}
			return combined;
		}

		@Override
		public Mount mount() throws MountFailedException {
			var builder = Fuse.builder();
			var libPath = WinfspUtil.getWinFspInstallDir() + "bin\\" + (OS_ARCH.contains("aarch64") ? "winfsp-a64.dll" : "winfsp-x64.dll");
			builder.setLibraryPath(libPath);
			//xattr disabled due to https://github.com/cryptomator/fuse-nio-adapter/issues/86
			var fuseAdapter = ReadWriteAdapter.create(builder.errno(), vfsRoot, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, FileNameTranscoder.transcoder(), false);
			var officeDecorator = new MicrosoftOfficeLockingAdapter(fuseAdapter);
			try {
				var fuse = builder.build(officeDecorator);
				fuse.mount("fuse-nio-adapter", mountPoint, combinedMountFlags().toArray(String[]::new));
				return new WinfspMount(fuse, officeDecorator, mountPoint);
			} catch (FuseMountFailedException e) {
				throw new MountFailedException(e);
			}
		}

	}

	private static class WinfspMount extends AbstractMount {

		public WinfspMount(Fuse fuseBinding, FuseNioAdapter fuseNioAdapter, Path mountpoint) {
			super(fuseBinding, fuseNioAdapter, mountpoint);
		}

		@Override
		public void unmount() throws UnmountFailedException {
			if (fuseNioAdapter.isInUse()) {
				throw new UnmountFailedException("Filesystem in use");
			}
			unmountForced();
		}

		@Override
		public void unmountForced() throws UnmountFailedException {
			try {
				fuse.close();
			} catch (TimeoutException e) {
				throw new UnmountFailedException(e);
			}
		}
	}

}
