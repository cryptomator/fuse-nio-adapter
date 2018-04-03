package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class MirroringFuseMountTest {

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p = Paths.get(scanner.nextLine());
			System.out.println("Enter mount point:");
			Path m = Paths.get(scanner.nextLine());
			if (Files.isDirectory(p)) {
				Mounter mounter = FuseMountFactory.getMounter();
				EnvironmentVariables envVars = EnvironmentVariables.create()
						.withMountName("FuseMirror")
						.withMountPath(m)
						.build();
				try (Mount mnt = mounter.mount(p, envVars)) {
					System.out.println("Mounted successfully. Enter anything to stop the server...");
					mnt.revealInFileManager();
					System.in.read();
				} catch (IOException | CommandFailedException e) {
					e.printStackTrace();
				}
				System.out.println("Unmounted successfully. Exiting...");
			} else {
				System.err.println("Invalid directory.");
			}
		}
	}
}
