package org.cryptomator.frontend.fuse.mount;

import org.apache.commons.lang3.SystemUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class MacFuseEnvironment implements FuseEnvironment {

	private static final String DEFAULT_MOUNTROOT_MAC = System.getProperty("user.home") + "Library/Application Support/Cryptomator";

	private ProcessBuilder revealCommand;
	private Path mountPoint;
	private String mountName;

	@Inject
	public MacFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
		String rootString = envVars.getOrDefault(EnvironmentVariable.MOUNTPATH, DEFAULT_MOUNTROOT_MAC);
		try {
			this.mountPoint = Paths.get(rootString).toAbsolutePath();
		} catch (InvalidPathException e) {
			throw new CommandFailedException(e);
		}
		this.mountName = envVars.getOrDefault(EnvironmentVariable.MOUNTNAME, "vault");
		this.revealCommand = new ProcessBuilder("open", mountPoint.toString());
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
		mountOptions.add("-ovolname=" + mountName);
		mountOptions.add("-oauto_xattr");
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

	/**
	 * TODO: should we check for more?
	 *
	 * @return
	 */
	@Override
	public boolean isApplicable() {
		return SystemUtils.IS_OS_MAC;
	}

}
