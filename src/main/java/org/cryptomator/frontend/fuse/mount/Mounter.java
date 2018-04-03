package org.cryptomator.frontend.fuse.mount;

public interface Mounter {

	Mount create(EnvironmentVariables envVars) throws CommandFailedException;

	boolean isApplicable();

}
