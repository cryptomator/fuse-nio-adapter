package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.integrations.mount.FileSystemMount;
import org.cryptomator.integrations.mount.FilesystemMountProvider;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountFeature;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

public class WinFspMountProvider implements FilesystemMountProvider {

	private static final Set<MountFeature> FEATURES = Set.of(//
			MountFeature.MOUNT_FLAGS, //
			MountFeature.MOUNT_POINT_DRIVE_LETTER, //
			MountFeature.MOUNT_POINT_EMPTY_DIR, //
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
		return System.getProperty("os.name").toLowerCase().contains("windows") && WinfspUtil.isWinFspInstalled();
	}

	@Override
	public FileSystemMount.Builder forFileSystem(Path vfsRoot) {
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
				"-ouid=-1", "-ogid=-1", "-oVolumePrefix=/localhost/\"" + mountName + "\""); //TODO: research and use correct ones
	}

	static class WinFspMountBuilder implements FileSystemMount.Builder {

		// @formatter:off
		Consumer<Throwable> onExitAction = e -> {};
		// @formatter:on
		Path vfsRoot;
		Path mountPoint;

		String[] mountFlags = new String[]{};

		boolean isReadOnly = false;


		WinFspMountBuilder(Path vfsRoot) {
			this.vfsRoot = vfsRoot;
		}

		@Override
		public FileSystemMount.Builder setMountpoint(Path p) {
			this.mountPoint = p;
			return this;
		}

		@Override
		public FileSystemMount.Builder setOnExitAction(Consumer<Throwable> onExitAction) {
			this.onExitAction = onExitAction;
			return this;
		}

		@Override
		public FileSystemMount.Builder setMountFlags(String customFlags) {
			this.mountFlags = customFlags.split(" "); //TODO: scheme is not sufficient!
			return this;
		}

		@Override
		public FileSystemMount.Builder setReadOnly(boolean mountReadOnly) {
			isReadOnly = mountReadOnly;
			return this;
		}

		@Override
		public FileSystemMount mount() throws MountFailedException {
			var builder = Fuse.builder();
			builder.setLibraryPath(WinfspUtil.getWinFspInstallDir() + "bin\\winfsp-x64.dll");

			var fuseAdapter = AdapterFactory.createReadWriteAdapter(vfsRoot, //
					builder.errno(), //
					AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
					FileNameTranscoder.transcoder());
			try {
				var fuse = builder.build(fuseAdapter);
				if( isReadOnly) {
					adjustMountFlagsToReadOnly();
				}
				fuse.mount("fuse-nio-adapter", mountPoint, mountFlags);
				return new WinfspMount(fuse, fuseAdapter);
			} catch (org.cryptomator.jfuse.api.MountFailedException e) {
				throw new MountFailedException(e);
			}
		}

		//TODO: tests!
		void adjustMountFlagsToReadOnly() {
			//Search Array
			boolean match;
			for (int i = 0; i < mountFlags.length; i++) {
				match = mountFlags[i].startsWith("-oumask=");
				if (match) {
					mountFlags[i] = "-oumask=333";
					return;
				}
			}
			//if not found, "append" option
			var tmp = Arrays.copyOf(mountFlags, mountFlags.length + 1);
			tmp[mountFlags.length] = "-oumask0333";
			this.mountFlags = tmp;
		}

	}

	private record WinfspMount(Fuse fuseBinding, FuseNioAdapter fuseNioAdapter) implements FileSystemMount {

		@Override
		public Path getAccessPoint() {
			return null;
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
