package org.cryptomator.frontend.fuse.mount;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EnvironmentVariablesTest {

	@Test
	public void testEnvironmentVariablesBuilder() {
		String[] flags = new String[]{"--test", "--debug"};
		Path mountPoint = Paths.get("/home/testuser/mnt");

		EnvironmentVariables envVars = EnvironmentVariables.create().withFlags(flags).withMountPoint(mountPoint).build();

		Assertions.assertEquals(flags, envVars.getFuseFlags());
		Assertions.assertEquals(mountPoint, envVars.getMountPoint());
	}
}
