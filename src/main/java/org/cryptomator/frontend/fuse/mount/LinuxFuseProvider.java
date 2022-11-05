package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.frontend.fuse.ReadWriteAdapter;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_TO_EXISTING_DIR;

/**
 * Mounts a file system on Linux using libfuse3.
 */
@Priority(100)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class LinuxFuseProvider implements MountService {

	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final String[] LIB_PATHS = {
			"/usr/lib/libfuse3.so", // default
			"/lib/x86_64-linux-gnu/libfuse3.so.3", // debian amd64
			"/lib/aarch64-linux-gnu/libfuse3.so.3" // debiant aarch64
	};

	@Override
	public String displayName() {
		return "FUSE";
	}

	@Override
	public boolean isSupported() {
		return Arrays.stream(LIB_PATHS).map(Path::of).anyMatch(Files::exists);
	}

	@Override
	public Set<MountCapability> capabilities() {
		return EnumSet.of(MOUNT_FLAGS, MOUNT_TO_EXISTING_DIR);
	}

	@Override
	public MountBuilder forFileSystem(Path fileSystemRoot) {
		return new LinuxFuseMountBuilder(fileSystemRoot);
	}

	@Override
	public String getDefaultMountFlags(String mountName) {
		// see: https://man7.org/linux/man-pages/man8/mount.fuse3.8.html
		try {
			return "-oauto_unmount" //
					+ " -ouid=" + Files.getAttribute(USER_HOME, "unix:uid") //
					+ " -ogid=" + Files.getAttribute(USER_HOME, "unix:gid") //
					+ " -oattr_timeout=5"; //
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class LinuxFuseMountBuilder extends AbstractMountBuilder {

		public LinuxFuseMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountBuilder setMountpoint(Path mountPoint) {
			if (Files.isDirectory(mountPoint)) { // MOUNT_TO_EXISTING_DIR
				this.mountPoint = mountPoint;
				return this;
			} else {
				throw new IllegalArgumentException("mount point must be an existing directory");
			}
		}

		@Override
		public Mount mount() throws MountFailedException {
			Preconditions.checkNotNull(mountPoint);
			Preconditions.checkNotNull(mountFlags);

			var libPath = Arrays.stream(LIB_PATHS).map(Path::of).filter(Files::exists).map(Path::toString).findAny().orElseThrow();
			var builder = Fuse.builder();
			builder.setLibraryPath(libPath);
			var fuseAdapter = ReadWriteAdapter.create(builder.errno(), vfsRoot, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, FileNameTranscoder.transcoder());
			var fuse = builder.build(fuseAdapter);
			try {
				fuse.mount("fuse-nio-adapter", mountPoint, mountFlags.toArray(String[]::new));
				return new LinuxFuseMountedVolume(fuse, fuseAdapter, mountPoint);
			} catch (FuseMountFailedException e) {
				throw new MountFailedException(e);
			}
		}

		private static class LinuxFuseMountedVolume extends AbstractMount {
			private boolean unmounted;

			public LinuxFuseMountedVolume(Fuse fuse, FuseNioAdapter fuseNioAdapter, Path mountpoint) {
				super(fuse, fuseNioAdapter, mountpoint);
			}

			@Override
			public void unmount() throws UnmountFailedException {
				ProcessBuilder command = new ProcessBuilder("fusermount", "-u", "--", mountpoint.getFileName().toString());
				command.directory(mountpoint.getParent().toFile());
				try {
					Process p = command.start();
					ProcessHelper.waitForSuccess(p, 10, "`fusermount -u`", UnmountFailedException::new);
					fuse.close();
					unmounted = true;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new UnmountFailedException(e);
				} catch (TimeoutException | IOException e) {
					throw new UnmountFailedException(e);
				}
			}

			@Override
			public void close() throws UnmountFailedException, IOException {
				if (!unmounted) {
					unmount();
				}
				super.close();
			}
		}
	}
}
