package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class LinuxFuseEnvironment implements FuseEnvironment {

	private static final String DEFAULT_MOUNTROOT_LINUX = System.getProperty("user.home") + ".Cryptomator";
	private static final String DEFAULT_REVEALCOMMAND_LINUX = "xdg-open";

	private Path mountPoint;
	private ProcessBuilder revealCommand;
	private boolean usesIndividualRevealCommand;

	@Inject
	public LinuxFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
		String rootString = envVars.getOrDefault(EnvironmentVariable.MOUNTPATH, DEFAULT_MOUNTROOT_LINUX);
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
			mountOptions.add("-ouid=" + getUIdOrGID("uid"));
			mountOptions.add("-ogid=" + getUIdOrGID("gid"));
		} catch (IOException e) {
			e.printStackTrace();
			throw new CommandFailedException(e);
		}
		mountOptions.add("-oauto_unmount");
		mountOptions.add("-ofsname=CryptoFs");
		return mountOptions.toArray(new String[mountOptions.size()]);
	}

	private String getUIdOrGID(String idtype) throws IOException {
		String id;
		String parameter;
		switch (idtype) {
			case "uid":
				parameter = "-u";
				break;
			case "gid":
				parameter = "-g";
				break;
			default:
				throw new IllegalArgumentException("Unkown ID type");
		}
		Process getId = new ProcessBuilder("sh", "-c", "id " + parameter).start();
		Scanner s = new Scanner(getId.getInputStream()).useDelimiter("\\A");
		try {
			getId.waitFor(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		id = s.nextLine();
		return id;
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
