package org.cryptomator.frontend.fuse.mount;

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

public class MacFuseEnvironment implements FuseEnvironment {

	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final Path DEFAULT_MOUNTROOT_MAC = USER_HOME.resolve("Library/Application Support/Cryptomator");

	private ProcessBuilder revealCommand;
	private Path mountPoint;
	private String mountName;

	@Inject
	public MacFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
		String rootString = envVars.getOrDefault(EnvironmentVariable.MOUNTPATH, DEFAULT_MOUNTROOT_MAC.toString());
		try {
			this.mountPoint = Paths.get(rootString).toAbsolutePath();
		} catch (InvalidPathException e) {
			throw new CommandFailedException(e);
		}
		this.mountName = envVars.getOrDefault(EnvironmentVariable.MOUNTNAME, "vault");
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
	public List<String> getRevealCommands() {
		return new ArrayList<>(revealCommand.command());
	}


	@Override
	public void cleanUp() {
	}

	/**
	 * @return <code>true</code> if on OS X and osxfuse is installed.
	 */
	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("mac") && Files.exists(Paths.get("/usr/local/lib/libosxfuse.2.dylib"));
	}

}
