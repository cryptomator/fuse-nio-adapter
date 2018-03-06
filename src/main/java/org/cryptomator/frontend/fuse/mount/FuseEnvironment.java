package org.cryptomator.frontend.fuse.mount;

import java.util.List;

public interface FuseEnvironment {

	String[] getMountParameters() throws CommandFailedException;

	String getMountPoint();

	default void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not implemented.");
	}

	void cleanUp() throws CommandFailedException;

}
