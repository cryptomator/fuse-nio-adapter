package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

class LinuxMounter extends AbstractMounter {

	private static final Logger LOG = LoggerFactory.getLogger(LinuxMounter.class);
	private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public String[] defaultMountFlags() {
		try {
			return new String[]{
					"-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"),
					"-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"),
					"-oauto_unmount"
			};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean isApplicable() {
		return IS_LINUX;
	}

	@Override
	protected Mount createMountObject(FuseNioAdapter fuseNioAdapter, EnvironmentVariables envVars) {
		return new LinuxMount(fuseNioAdapter, envVars);
	}

	private static class LinuxMount extends AbstractMount {

		private LinuxMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars.getMountPoint());
		}

		@Override
		public void unmountInternal() throws FuseMountException {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			ProcessBuilder command = new ProcessBuilder("fusermount", "-u", "--", mountPoint.getFileName().toString());
			command.directory(mountPoint.getParent().toFile());
			Process proc = ProcessUtil.startAndWaitFor(command, 5, TimeUnit.SECONDS);
			assertUmountSucceeded(proc);
			fuseAdapter.setUnmounted();
		}

		@Override
		public void unmountForcedInternal() throws FuseMountException {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			fuseAdapter.unmountForced();
		}

		private void assertUmountSucceeded(Process proc) throws FuseMountException {
			if (proc.exitValue() == 0) {
				return;
			}
			try {
				String stderr = ProcessUtil.toString(proc.getErrorStream(), StandardCharsets.US_ASCII).toLowerCase();
				if (stderr.contains("not mounted") || stderr.contains("no such file or directory")) {
					LOG.info("Already unmounted");
					return;
				} else {
					throw new FuseMountException("Unmount failed. STDERR: " + stderr);
				}
			} catch (IOException e) {
				throw new FuseMountException(e);
			}
		}
	}
}
