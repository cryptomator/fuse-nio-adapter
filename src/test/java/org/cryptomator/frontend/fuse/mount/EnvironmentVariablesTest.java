package org.cryptomator.frontend.fuse.mount;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openjdk.tools.javac.util.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EnvironmentVariablesTest {

	@Test
	public void testEnvironmentVariablesBuilder(){
		String mountPath = "/home/testuser/mnt";
		String mountName = "coolStuff";
		EnvironmentVariables envVars = EnvironmentVariables.create().withMountName(mountName).withMountPath(mountPath).build();
		Assertions.assertNotNull(envVars.get(EnvironmentVariable.MOUNTPATH));
		Assertions.assertNotNull(envVars.get(EnvironmentVariable.MOUNTNAME));
		Assertions.assertEquals(mountName, envVars.get(EnvironmentVariable.MOUNTNAME));
		Assertions.assertEquals(mountPath.toString(), envVars.get(EnvironmentVariable.MOUNTPATH));
	}
}
