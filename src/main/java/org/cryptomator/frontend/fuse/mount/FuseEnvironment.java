package org.cryptomator.frontend.fuse.mount;

import java.util.List;

public interface FuseEnvironment {

	void makeEnvironment(EnvironmentVariables envVar) throws CommandFailedException;

	String[] getMountParameters() throws CommandFailedException;

	String getMountPoint();

	default void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not implemented.");
	}

	List<String> getRevealCommands();

	void cleanUp() throws CommandFailedException;

	default boolean isApplicable() {
		return false;
	}


}
