package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class LinuxFuseEnvironmentFactory implements FuseEnvironmentFactory {

	@Override
	public FuseEnvironment create(EnvironmentVariables envVars) throws CommandFailedException {
		return new LinuxFuseEnvironment(envVars);
	}

	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}

	private static class LinuxFuseEnvironment implements FuseEnvironment {

		private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
		private static final String DEFAULT_REVEALCOMMAND_LINUX = "xdg-open";

		private final Path mountPoint;
		private final ProcessBuilder revealCommand;
		private final boolean usesIndividualRevealCommand;

		private LinuxFuseEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
			String rootString = envVars.get(EnvironmentVariable.MOUNTPATH);
			try {
				mountPoint = Paths.get(rootString).toAbsolutePath();
			} catch (InvalidPathException e) {
				throw new CommandFailedException(e);
			}
			String[] command = envVars.getOrDefault(EnvironmentVariable.REVEALCOMMAND, DEFAULT_REVEALCOMMAND_LINUX).split("\\s+");
			this.revealCommand = new ProcessBuilder(ObjectArrays.concat(command, mountPoint.toString()));
			this.usesIndividualRevealCommand = envVars.containsKey(EnvironmentVariable.REVEALCOMMAND);
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
			mountOptions.add("-oauto_unmount");
			mountOptions.add("-ofsname=CryptoFs");
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public String getMountPoint() {
			return this.mountPoint.toString();
		}

		@Override
		public void revealMountPathInFilesystemmanager() throws CommandFailedException {
			if (usesIndividualRevealCommand) {
				try {
					revealCommand.start();
				} catch (IOException e) {
					throw new CommandFailedException("Individual RevealCommand failed: " + e.getMessage());
				}
			} else {
				try {
					ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
				} catch (ProcessUtil.CommandTimeoutException e) {
					throw new CommandFailedException(e.getMessage());
				}
			}
		}

		@Override
		public void cleanUp() {
		}

	}
}
