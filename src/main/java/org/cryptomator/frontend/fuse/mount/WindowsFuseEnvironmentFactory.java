package org.cryptomator.frontend.fuse.mount;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class WindowsFuseEnvironmentFactory implements FuseEnvironmentFactory {

	@Override
	public FuseEnvironment create(EnvironmentVariables envVars) throws CommandFailedException {
		return new WindowsFuseEnvironment(envVars);
	}

	/**
	 * TODO: we should check more!
	 *
	 * @return
	 */
	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	private static class WindowsFuseEnvironment implements FuseEnvironment {

		private final Path mountPoint;
		private final String mountName;
		private final ProcessBuilder revealCommand;

		private WindowsFuseEnvironment(EnvironmentVariables envVar) throws CommandFailedException {
			String rootString = envVar.get(EnvironmentVariable.MOUNTPATH);
			if (rootString == null) {
				throw new CommandFailedException("No drive Letter given.");
			}
			try {
				this.mountPoint = Paths.get(rootString).toAbsolutePath();
			} catch (InvalidPathException e) {
				throw new CommandFailedException(e);
			}
			this.mountName = envVar.getOrDefault(EnvironmentVariable.MOUNTNAME, "vault");
			this.revealCommand = new ProcessBuilder("explorer", "/root,", mountPoint.toString());
		}

		/**
		 * TODO: measure the performance effect of FileInfoTimeout
		 *
		 * @return
		 */
		@Override
		public String[] getMountParameters() {
			ArrayList<String> mountOptions = new ArrayList<>(8);
			mountOptions.add(("-oatomic_o_trunc"));
			mountOptions.add("-ouid=-1");
			mountOptions.add("-ogid=-1");
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oFileInfoTimeout=5000");
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public Path getMountPoint() {
			return mountPoint;
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
