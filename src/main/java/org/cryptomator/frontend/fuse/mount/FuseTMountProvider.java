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
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mounts a file system on macOS using fuse-t.
 *
 * @see <a href="https://www.fuse-t.org/">fuse-t website</a>
 */
@Priority(90)
@OperatingSystem(OperatingSystem.Value.MAC)
public class FuseTMountProvider implements MountProvider {

	private static final String DYLIB_PATH = "/usr/local/lib/libfuse-t.dylib";
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public String displayName() {
		return "FUSE-T";
	}

	@Override
	public boolean isSupported() {
		return Files.exists(Paths.get(DYLIB_PATH));
	}

	@Override
	public MountBuilder forPath(Path vfsRoot) {
		return new FuseTMountBuilder(vfsRoot);
	}

	@Override
	public Set<Features> supportedFeatures() {
		return EnumSet.of(Features.DEFAULT_MOUNT_FLAGS, Features.CUSTOM_FLAGS, Features.UNMOUNT_FORCED, Features.READ_ONLY, Features.MOUNT_POINT_EMPTY_DIR);
	}

	// TODO adjust API
	private int defaultPort() {
		return 2049;
	}

	// TODO adjust API to support volumeName
	@Override
	public String getDefaultMountFlags() {
		// https://github.com/macos-fuse-t/fuse-t/wiki#supported-mount-options
		try {
			return "-ovolname=TODO" //
					+ " -ouid=" + Files.getAttribute(USER_HOME, "unix:uid") //
					+ " -ogid=" + Files.getAttribute(USER_HOME, "unix:gid") //
					+ " -orwsize=262144";
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class FuseTMountBuilder extends AbstractMacMountBuilder {

		public FuseTMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		// TODO adjust API
		public MountBuilder setPort(int port) {
			// set -l flag (NFS server listen address)
			return this;
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
				fuse.mount("fuse-nio-adapter", mountPoint, combinedMountFlags());
				return new MacMountedVolume(fuse, mountPoint);
			} catch (org.cryptomator.jfuse.api.MountFailedException e) {
				throw new MountFailedException(e);
			}
		}
	}

}
