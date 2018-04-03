package org.cryptomator.frontend.fuse.mount;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EnvironmentVariablesTest {

	@Test
	public void testEnvironmentVariablesBuilder(){
		Path mountPath = Paths.get("/home/testuser/mnt");
		String mountName = "coolStuff";
		EnvironmentVariables envVars = EnvironmentVariables.create().withMountName(mountName).withMountPath(mountPath).build();
		Assertions.assertEquals(mountName, envVars.getMountName().get());
		Assertions.assertEquals(mountPath, envVars.getMountPath());
	}
}
