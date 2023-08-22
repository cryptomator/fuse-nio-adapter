package org.cryptomator.frontend.fuse.mount;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_TO_EXISTING_DIR;

/**
 * Mounts a file system on Linux using libfuse3.
 */
@Priority(100)
@OperatingSystem(OperatingSystem.Value.LINUX)
public class LinuxFuseMountProvider implements MountService {

	private static final Logger LOG = LoggerFactory.getLogger(LinuxFuseMountProvider.class);
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final String[] LIB_PATHS = {
			"/usr/lib/libfuse3.so", // default
			"/lib/x86_64-linux-gnu/libfuse3.so.3", // debian amd64
			"/lib/aarch64-linux-gnu/libfuse3.so.3", // debian aarch64
			"/usr/lib64/libfuse3.so.3", // fedora
			"/app/lib/libfuse3.so" // flatpak
	};
	private static final String UNMOUNT_CMD_NAME = "fusermount3";

	@Override
	public String displayName() {
		return "FUSE";
	}

	@Override
	public boolean isSupported() {
		return Arrays.stream(LIB_PATHS).map(Path::of).anyMatch(Files::exists) && isFusermount3Installed();
	}

	private boolean isFusermount3Installed() {
		try {
			var p = new ProcessBuilder(UNMOUNT_CMD_NAME, "-V").start();
			ProcessHelper.waitForSuccess(p, 2, String.format("`%s -V`", UNMOUNT_CMD_NAME));
			return true;
		} catch (IOException | TimeoutException | InterruptedException | ProcessHelper.CommandFailedException e) {
			if( e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
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
	public String getDefaultMountFlags() {
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
			Objects.requireNonNull(mountPoint);
			Objects.requireNonNull(mountFlags);

			var libPath = Arrays.stream(LIB_PATHS).map(Path::of).filter(Files::exists).map(Path::toString).findAny().orElseThrow();
			var builder = Fuse.builder();
			builder.setLibraryPath(libPath);
			if (mountFlags.contains("-oallow_other") || mountFlags.contains("-oallow_root")) {
				LOG.warn("Mounting with flag -oallow_other or -oallow_root. Ensure that in /etc/fuse.conf option user_allow_other is enabled.");
			}
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
				var mp = mountpoint.getFileName().toString();
				ProcessBuilder command = new ProcessBuilder(UNMOUNT_CMD_NAME, "-u", "--", mp);
				command.directory(mountpoint.getParent().toFile());
				try {
					Process p = command.start();
					ProcessHelper.waitForSuccess(p, 10, String.format("`%s -u -- %s`", UNMOUNT_CMD_NAME, mp));
					fuse.close();
					unmounted = true;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new UnmountFailedException(e);
				} catch (TimeoutException | IOException e) {
					throw new UnmountFailedException(e);
				} catch (ProcessHelper.CommandFailedException e) {
					if (e.stderr.contains(String.format("not mounted", mountpoint)) || e.stderr.contains(String.format("entry for %s not found in", mountpoint))) {
						LOG.info("{} already unmounted. Nothing to do.", mountpoint);
					} else {
						LOG.warn("{} failed with exit code {}:\nSTDOUT: {}\nSTDERR: {}\n", "`fusermount3 -u`", e.exitCode, e.stdout, e.stderr);
						throw new UnmountFailedException(e);
					}
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
