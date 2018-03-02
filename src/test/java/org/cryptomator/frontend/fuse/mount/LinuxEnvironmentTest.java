package org.cryptomator.frontend.fuse.mount;

public class LinuxEnvironmentTest {

	public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	public static void main(String[] args) {
		if (IS_LINUX) {
			FuseEnvironment env = DaggerEnvironmentComponent.create().fuseEnvironment();
			EnvironmentVariables envVars = EnvironmentVariables.create()
					.withMountName("yolo")
					.withMountPath("/home/")
					.withRevealCommand("nautilus")
					.build();
			try {
				env.makeEnvironment(envVars);
				env.revealMountPathInFilesystemmanager();
				env.cleanUp();
			} catch (CommandFailedException e) {
				e.printStackTrace();
			}
		} else {
			System.out.print("Sorry, this test is only for Linux.");
		}
	}

}
