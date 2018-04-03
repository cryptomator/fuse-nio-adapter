package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LinuxEnvironmentTest {

	public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	public static void main(String[] args) {
		if (IS_LINUX) {
			EnvironmentVariables envVars = EnvironmentVariables.create()
					.withMountName("yolo")
					.withMountPath("/home/")
					.withRevealCommand("nautilus")
					.build();
			Path tmp = Paths.get("/tmp");
			try (Mount mnt = FuseMountFactory.getMounter().mount(tmp, envVars)) {
				mnt.revealMountPathInFilesystemmanager();
				System.out.println("Wait for it...");
				System.in.read();
			} catch (CommandFailedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.out.print("Sorry, this test is only for Linux.");
		}
	}

}
