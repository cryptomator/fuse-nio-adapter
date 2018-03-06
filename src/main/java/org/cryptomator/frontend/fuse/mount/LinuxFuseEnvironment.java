package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class LinuxFuseEnvironment implements FuseEnvironment {

	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final Path DEFAULT_MOUNTROOT_LINUX = USER_HOME.resolve(".Cryptomator");
	private static final String DEFAULT_REVEALCOMMAND_LINUX = "xdg-open";

	private Path mountPoint;
	private ProcessBuilder revealCommand;
	private boolean usesIndividualRevealCommand;

	@Inject
	public LinuxFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
		String rootString = envVars.getOrDefault(EnvironmentVariable.MOUNTPATH, DEFAULT_MOUNTROOT_LINUX.toString());
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
	public List<String> getRevealCommands() {
		return new ArrayList<>(revealCommand.command());
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}

}
