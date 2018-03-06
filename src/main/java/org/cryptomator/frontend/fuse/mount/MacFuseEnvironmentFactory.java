package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MacFuseEnvironmentFactory implements FuseEnvironmentFactory {

	@Override
	public FuseEnvironment create(EnvironmentVariables envVars) throws CommandFailedException {
		return new MacFuseEnvironment(envVars);
	}

	/**
	 * @return <code>true</code> if on OS X and osxfuse is installed.
	 */
	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("mac") && Files.exists(Paths.get("/usr/local/lib/libosxfuse.2.dylib"));
	}

	private static class MacFuseEnvironment implements FuseEnvironment {

		private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

		private final Path mountPoint;
		private final String mountName;
		private final ProcessBuilder revealCommand;

		private MacFuseEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
			String rootString = envVars.get(EnvironmentVariable.MOUNTPATH);
			try {
				this.mountPoint = Paths.get(rootString).toAbsolutePath();
			} catch (InvalidPathException e) {
				throw new CommandFailedException(e);
			}
			this.mountName = envVars.get(EnvironmentVariable.MOUNTNAME);
			this.revealCommand = new ProcessBuilder("open", "\"" + mountPoint.toString() + "\"");
		}

		@Override
		public String[] getMountParameters() throws CommandFailedException {
			ArrayList<String> mountOptions = new ArrayList<>(8);
			mountOptions.add(("-oatomic_o_trunc"));
			try {
				mountOptions.add("-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"));
				mountOptions.add("-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"));
			} catch (IOException e) {
				e.printStackTrace();
				throw new CommandFailedException(e);
			}
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oauto_xattr");
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public String getMountPoint() {
			return mountPoint.toString();
		}

		@Override
		public void revealMountPathInFilesystemmanager() throws CommandFailedException {
			try {
				ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
			} catch (ProcessUtil.CommandTimeoutException e) {
				throw new CommandFailedException(e.getMessage());
			}
		}


		@Override
		public void cleanUp() {
		}

	}

}
