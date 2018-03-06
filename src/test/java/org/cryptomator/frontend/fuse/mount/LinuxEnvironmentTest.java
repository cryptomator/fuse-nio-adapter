package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;

public class LinuxEnvironmentTest {

	public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	public static void main(String[] args) {
		if (IS_LINUX) {
			EnvironmentVariables envVars = EnvironmentVariables.create()
					.withMountName("yolo")
					.withMountPath("/home/")
					.withRevealCommand("nautilus")
					.build();
			try {
				FuseEnvironment env = DaggerEnvironmentComponent.create().fuseEnvironmentFactory().get().create(envVars);
				env.revealMountPathInFilesystemmanager();
				System.out.println("Wait for it...");
				System.in.read();
				env.cleanUp();
			} catch (CommandFailedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			System.out.print("Sorry, this test is only for Linux.");
		}
	}

}
