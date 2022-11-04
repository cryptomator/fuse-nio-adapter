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
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_TO_EXISTING_DIR;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_TO_SYSTEM_CHOSEN_PATH;
import static org.cryptomator.integrations.mount.MountCapability.READ_ONLY;
import static org.cryptomator.integrations.mount.MountCapability.UNMOUNT_FORCED;
import static org.cryptomator.integrations.mount.MountCapability.VOLUME_ID;

/**
 * Mounts a file system on macOS using macFUSE.
 *
 * @see <a href="https://macfuse.io/">macFUSE website</a>
 */
@Priority(100)
@OperatingSystem(OperatingSystem.Value.MAC)
public class MacFuseMountProvider implements MountService {

	private static final String DYLIB_PATH = "/usr/local/lib/libosxfuse.2.dylib";
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public String displayName() {
		return "macFUSE";
	}

	@Override
	public boolean isSupported() {
		return Files.exists(Paths.get(DYLIB_PATH));
	}

	@Override
	public MountBuilder forFileSystem(Path fileSystemRoot) {
		return new MacFuseMountBuilder(fileSystemRoot);
	}

	@Override
	public Set<MountCapability> capabilities() {
		return EnumSet.of(MOUNT_FLAGS, UNMOUNT_FORCED, READ_ONLY, MOUNT_TO_EXISTING_DIR, MOUNT_TO_SYSTEM_CHOSEN_PATH, VOLUME_ID);
	}

	@Override
	public String getDefaultMountFlags(String volumeName) {
		// see: https://github.com/osxfuse/osxfuse/wiki/Mount-options
		try {
			return "-ovolname=" + volumeName //
					+ " -ouid=" + Files.getAttribute(USER_HOME, "unix:uid") //
					+ " -ogid=" + Files.getAttribute(USER_HOME, "unix:gid") //
					+ " -oatomic_o_trunc" //
					+ " -oauto_xattr" //
					+ " -oauto_cache" //
					+ " -onoappledouble" // vastly impacts performance for some reason...
					+ " -odefault_permissions"; // let the kernel assume permissions based on file attributes etc
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class MacFuseMountBuilder extends AbstractMacMountBuilder {

		private String volumeId;

		public MacFuseMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountBuilder setMountpoint(Path mountPoint) {
			if (mountPoint.startsWith("/Volumes/") && Files.notExists(mountPoint) // DEFAULT_MOUNT_POINT
					|| Files.isDirectory(mountPoint)) { // MOUNT_TO_EXISTING_DIR
				this.mountPoint = mountPoint;
			} else {
				throw new IllegalArgumentException("mount point must be an existing directory");
			}
			return this;
		}

		@Override
		public MountBuilder setVolumeId(String volumeId) {
			this.volumeId = volumeId;
			return this;
		}

		@Override
		public Mount mount() throws MountFailedException {
			Preconditions.checkNotNull(mountFlags);
			if (mountPoint == null) {
				Preconditions.checkNotNull(volumeId);
				mountPoint = Path.of("/Volumes/", volumeId);
			}

			var builder = Fuse.builder();
			builder.setLibraryPath(DYLIB_PATH);
			var filenameTranscoder = FileNameTranscoder.transcoder().withFuseNormalization(Normalizer.Form.NFD);
			var fuseAdapter = ReadWriteAdapter.create(builder.errno(), vfsRoot, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, filenameTranscoder);
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
