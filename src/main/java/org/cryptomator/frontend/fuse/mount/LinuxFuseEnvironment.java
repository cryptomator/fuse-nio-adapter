package org.cryptomator.frontend.fuse.mount;

import org.apache.commons.lang3.SystemUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class LinuxFuseEnvironment implements FuseEnvironment {

	private static final String DEFAULT_MOUNTROOT_LINUX = System.getProperty("user.home") + ".Cryptomator";

	private Path root;

	@Inject
	public LinuxFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {
		String rootString = envVars.getOrDefault(EnvironmentVariable.MOUNTPATH, DEFAULT_MOUNTROOT_LINUX);
		try {
			root = Paths.get(rootString).toAbsolutePath();
		} catch (InvalidPathException e) {
			throw new CommandFailedException(e);
		}
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
		return this.root.toString();
	}

	@Override
	public void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not implemented.");
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public boolean isApplicable() {
		return SystemUtils.IS_OS_LINUX;
	}
}
