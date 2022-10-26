package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.jfuse.api.Fuse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mounts a file system on macOS using macFUSE.
 *
 * @see <a href="https://macfuse.io/">macFUSE website</a>
 */
@Priority(100)
@OperatingSystem(OperatingSystem.Value.MAC)
public class MacFuseMountProvider implements MountProvider {

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
	public MountBuilder forPath(Path vfsRoot) {
		return new MacFuseMountBuilder(vfsRoot);
	}

	@Override
	public Set<Features> supportedFeatures() {
		return EnumSet.of(Features.DEFAULT_MOUNT_POINT, Features.DEFAULT_MOUNT_FLAGS, Features.CUSTOM_FLAGS, Features.UNMOUNT_FORCED, Features.READ_ONLY, Features.MOUNT_POINT_EMPTY_DIR);
	}

	// TODO adjust API to support volumeName
	@Override
	public String getDefaultMountPoint() {
		return "/Volumes/" + UUID.randomUUID();
	}

	// TODO adjust API to support volumeName
	@Override
	public String getDefaultMountFlags() {
		// see: https://github.com/osxfuse/osxfuse/wiki/Mount-options
		try {
			return "-ovolname=TODO" // TODO
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

		public MacFuseMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountedVolume mount() throws MountFailedException {
			Preconditions.checkNotNull(mountPoint);
			Preconditions.checkNotNull(mountFlags);

			var builder = Fuse.builder();
			builder.setLibraryPath(DYLIB_PATH);
			var fuseAdapter = AdapterFactory.createReadWriteAdapter(vfsRoot, //
					builder.errno(), //
					AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
					FileNameTranscoder.transcoder().withFuseNormalization(Normalizer.Form.NFD));
			var fuse = builder.build(fuseAdapter);
			try {
				fuse.mount("fuse-nio-adapter", mountPoint, mountFlags.toArray(String[]::new));
				return new MacMountedVolume(fuse, mountPoint);
			} catch (org.cryptomator.jfuse.api.MountFailedException e) {
				throw new MountFailedException(e);
			}
		}
	}

}
