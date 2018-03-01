package org.cryptomator.frontend.fuse.mount;

import org.apache.commons.lang3.SystemUtils;

import javax.inject.Inject;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class WindowsFuseEnvironment implements FuseEnvironment {

	private Path root;
	private String mountName;

	@Inject
	public WindowsFuseEnvironment() {
	}

	@Override
	public void makeEnvironment(EnvironmentVariables envVar) throws CommandFailedException {
		String rootString = envVar.get(EnvironmentVariable.MOUNTPATH);
		if (rootString == null) {
			throw new CommandFailedException("No drive Letter given.");
		}
		try {
			root = Paths.get(rootString).toAbsolutePath();
		} catch (InvalidPathException e) {
			throw new CommandFailedException(e);
		}
		mountName = envVar.getOrDefault(EnvironmentVariable.MOUNTNAME, "vault");
	}

	/**
	 * TODO: measure the performance effect of FileInfoTimeout
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
	public String getMountPoint() {
		return root.toString();
	}

	@Override
	public void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not Implemented");
	}

	@Override
	public void cleanUp() {

	}

	/**
	 * TODO: Should we check more?
	 *
	 * @return
	 */
	@Override
	public boolean isApplicable() {
		return SystemUtils.IS_OS_WINDOWS;
	}
}
