package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LinuxEnvironmentTest {

	public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	public static void main(String[] args) throws IOException {
		if (IS_LINUX) {
			Path mountPoint = Files.createTempDirectory("fuse-mount");
			Mounter mounter = FuseMountFactory.getMounter();
			EnvironmentVariables envVars = EnvironmentVariables.create()
					.withFlags(mounter.defaultMountFlags())
					.withMountPoint(mountPoint)
					.build();
			Path tmp = Paths.get("/tmp");
			try (Mount mnt = mounter.mount(tmp, envVars)) {
				try {
					mnt.reveal(new AwtFrameworkRevealer());
				} catch (Exception e) {
					System.out.println("Reveal failed.");
				}
				System.out.println("Wait for it...");
				System.in.read();
				mnt.unmountForced();
			} catch (IOException | FuseMountException e) {
				e.printStackTrace();
			}
		} else {
			System.out.print("Sorry, this test is only for Linux.");
		}
	}

}
