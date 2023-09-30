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
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_TO_EXISTING_DIR;
import static org.cryptomator.integrations.mount.MountCapability.READ_ONLY;
import static org.cryptomator.integrations.mount.MountCapability.UNMOUNT_FORCED;
import static org.cryptomator.integrations.mount.MountCapability.VOLUME_NAME;

/**
 * Mounts a file system on macOS using fuse-t.
 *
 * @see <a href="https://www.fuse-t.org/">fuse-t website</a>
 */
@Priority(90)
@OperatingSystem(OperatingSystem.Value.MAC)
public class FuseTMountProvider implements MountService {

	private static final String DYLIB_PATH = "/usr/local/lib/libfuse-t.dylib";
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public String displayName() {
		return "FUSE-T (Experimental)";
	}

	@Override
	public boolean isSupported() {
		return Files.exists(Paths.get(DYLIB_PATH));
	}

	@Override
	public MountBuilder forFileSystem(Path fileSystemRoot) {
		return new FuseTMountBuilder(fileSystemRoot);
	}

	@Override
	public Set<MountCapability> capabilities() {
		return EnumSet.of(MOUNT_FLAGS, UNMOUNT_FORCED, READ_ONLY, MOUNT_TO_EXISTING_DIR, VOLUME_NAME); // LOOPBACK_PORT is currently broken
	}

	@Override
	public int getDefaultLoopbackPort() {
		return 2049;
	}

	@Override
	public String getDefaultMountFlags() {
		// see: https://github.com/macos-fuse-t/fuse-t/wiki#supported-mount-options
		try {
			return "-orwsize=262144" //
					+ " -ouid=" + Files.getAttribute(USER_HOME, "unix:uid") //
					+ " -ogid=" + Files.getAttribute(USER_HOME, "unix:gid");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class FuseTMountBuilder extends AbstractMacMountBuilder {

		private int port;

		public FuseTMountBuilder(Path vfsRoot) {
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
		public MountBuilder setLoopbackPort(int port) {
			this.port = port;
			return this;
		}

		@Override
		protected Set<String> combinedMountFlags() {
			Set<String> combined = super.combinedMountFlags();
			// TODO: this is currently broken in fuse-t. we need to stick with the standard port
//			if (port != 0) {
//				combined.add("-l 0:" + port);
//			}
			return combined;
		}

		@Override
		public Mount mount() throws MountFailedException {
			Objects.requireNonNull(mountPoint);
			Objects.requireNonNull(mountFlags);

			var builder = Fuse.builder();
			builder.setLibraryPath(DYLIB_PATH);
			var filenameTranscoder = FileNameTranscoder.transcoder().withFuseNormalization(Normalizer.Form.NFD);
			var fuseAdapter = ReadWriteAdapter.create(builder.errno(), vfsRoot, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, filenameTranscoder, false);
			var fuse = builder.build(fuseAdapter);
			try {
				fuse.mount("fuse-nio-adapter", mountPoint, combinedMountFlags().toArray(String[]::new));
				return new MacMountedVolume(fuse, fuseAdapter, mountPoint);
			} catch (FuseMountFailedException e) {
				throw new MountFailedException(e);
			}
		}
	}

}
