package org.cryptomator.frontend.fuse.mount;

public interface FuseEnvironment {

	void makeEnvironment(EnvironmentVariables envVar) throws CommandFailedException;

	String[] getMountParameters() throws CommandFailedException;

	String getMountPoint();

	/**
	 * TODO: implement it in subclasses!
	 *
	 * @throws CommandFailedException
	 */
	default void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not implemented.");
	}

	void cleanUp() throws CommandFailedException;

	default boolean isApplicable() {
		return false;
	}


}
