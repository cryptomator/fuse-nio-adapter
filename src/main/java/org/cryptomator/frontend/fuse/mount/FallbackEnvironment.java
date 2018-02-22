package org.cryptomator.frontend.fuse.mount;

public class FallbackEnvironment implements FuseEnvironment{

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
	public void cleanUp() {

	}

	@Override
	public boolean isApplicable() {
		return false;
	}
}
