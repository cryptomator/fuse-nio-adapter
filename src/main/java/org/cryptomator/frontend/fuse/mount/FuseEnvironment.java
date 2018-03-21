package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface FuseEnvironment {

	String[] getMountParameters() throws CommandFailedException;

	Path getMountPoint();

	default void revealMountPathInFilesystemmanager() throws CommandFailedException {
		throw new CommandFailedException("Not implemented.");
	}

	void cleanUp() throws CommandFailedException;

}
