package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface Mounter {

	Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException;

	String[] defaultMountFlags();

	boolean isApplicable();

}
