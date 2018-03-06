package org.cryptomator.frontend.fuse.mount;

public interface FuseEnvironmentFactory {

	FuseEnvironment create(EnvironmentVariables envVars) throws CommandFailedException;

	boolean isApplicable();

}
