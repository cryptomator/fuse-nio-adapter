package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface Mounter {

	default Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException {
		return mount(directory, false, false, envVars);
	}

	Mount mount(Path directory, boolean blocking, boolean debug, EnvironmentVariables envVars) throws CommandFailedException;

	String[] defaultMountFlags();

	boolean isApplicable();

}
