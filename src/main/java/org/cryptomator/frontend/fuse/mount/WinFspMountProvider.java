package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.mount.Mount;
import org.cryptomator.integrations.mount.MountBuilder;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountFeature;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WinFspMountProvider implements MountProvider {

	private static final Set<MountFeature> FEATURES = Set.of(//
			MountFeature.MOUNT_FLAGS, //
			MountFeature.MOUNT_AS_DRIVE_LETTER, //
			MountFeature.MOUNT_WITHIN_EXISTING_PARENT, //
			MountFeature.UNMOUNT_FORCED, //
			MountFeature.ON_EXIT_ACTION, //
			MountFeature.READ_ONLY);

	public WinFspMountProvider() {
	}

	@Override
	public String displayName() {
		return "FUSE (WinFsp)";
	}

	@Override
	public boolean isSupported() {
		return WinfspUtil.isWinFspInstalled();
	}

	@Override
	public MountBuilder forFileSystem(Path vfsRoot) {
		return new WinFspMountBuilder(vfsRoot);
	}

	@Override
	public Set<MountFeature> supportedFeatures() {
		return FEATURES;
	}

	//For all options, see https://github.com/winfsp/winfsp/blob/84b3f98d383b265ebdb33891fc911eaafb878497/src/dll/fuse/fuse.c#L628
	@Override
	public String getDefaultMountFlags(String mountName) {
		return String.join(" ",//
				"-ouid=-1", "-ogid=-1", "-oVolumePrefix=/localhost/" + mountName); //TODO: research and use correct ones
	}

	static class WinFspMountBuilder extends AbstractMountBuilder {

		// @formatter:off
		Consumer<Throwable> onExitAction = e -> {};
		// @formatter:on

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
		public MountBuilder setOnExitAction(Consumer<Throwable> onExitAction) {
			this.onExitAction = onExitAction;
			return this;
		}

		@Override
		public MountBuilder setReadOnly(boolean mountReadOnly) {
			isReadOnly = mountReadOnly;
			return this;
		}

		//TODO: tests!
		/**
		 * Combines the {@link #setMountFlags(String) mount flags} with any additional option that might have
		 * been set separately.
		 *
		 * @return Mutable set of all currently set mount options
		 */
		protected Set<String> combinedMountFlags() {
			var combined = new HashSet<>(mountFlags);
			if (isReadOnly) {
				combined.removeIf(flag -> flag.startsWith("-oumask="));
				combined.add("-oumask=0333");
			}
			return combined;
		}

		@Override
		public Mount mount() throws MountFailedException {
			var builder = Fuse.builder();
			builder.setLibraryPath(WinfspUtil.getWinFspInstallDir() + "bin\\winfsp-x64.dll");

			var fuseAdapter = AdapterFactory.createReadWriteAdapter(vfsRoot, //
					builder.errno(), //
					AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
					FileNameTranscoder.transcoder());
			try {
				var fuse = builder.build(fuseAdapter);
				fuse.mount("fuse-nio-adapter", mountPoint, combinedMountFlags().toArray(String[]::new));
				return new WinfspMount(fuse, fuseAdapter, mountPoint);
			} catch (org.cryptomator.jfuse.api.MountFailedException e) {
				throw new MountFailedException(e);
			}
		}

	}

	private record WinfspMount(Fuse fuseBinding, FuseNioAdapter fuseNioAdapter, Path mountpoint) implements Mount {

		@Override
		public Path getAccessPoint() {
			return mountpoint;
		}

		@Override
		public void reveal(Consumer<Path> cmd) {
			//TODO
		}

		@Override
		public void unmout() throws UnmountFailedException {
			if (fuseNioAdapter.isInUse()) {
				throw new UnmountFailedException("Filesystem in use");
			}
			close();
		}

		@Override
		public void close() throws UnmountFailedException {
			try {
				this.fuseBinding.close();
				this.fuseNioAdapter.close();
			} catch (Exception e) {
				throw new UnmountFailedException(e);
			}
		}
	}

}
