package org.cryptomator.frontend.fuse.mount;

import java.util.List;

/**
 * TODO: Implement it!
 */
public class FallbackEnvironment implements FuseEnvironment {

	@Override
	public void makeEnvironment(EnvironmentVariables envVars) throws CommandFailedException {

	}

	@Override
	public String[] getMountParameters() throws CommandFailedException {
		return new String[0];
	}

	@Override
	public String getMountPoint() {
		return null;
	}

	@Override
	public void revealMountPathInFilesystemmanager() throws CommandFailedException {

	}

	@Override
	public List<String> getRevealCommands() {
		return null;
	}

	@Override
	public void cleanUp() {

	}

	@Override
	public boolean isApplicable() {
		return false;
	}
}
