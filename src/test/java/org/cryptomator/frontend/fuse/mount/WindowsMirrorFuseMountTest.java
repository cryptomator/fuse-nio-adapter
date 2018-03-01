package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.cryptofs.CryptoFileSystem;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class WindowsMirrorFuseMountTest {

	private static final boolean IS_WIN = System.getProperty("os.name").contains("Windows");

	public static void main(String[] args) throws IOException {
		if (!IS_WIN) {
			System.err.println("To be run on Windows");
		}
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p = Paths.get(scanner.nextLine());
			if (Files.isDirectory(p)) {
				FuseMount fm = MountFactory.createMountObject();
				EnvironmentVariables envVars = EnvironmentVariables.create()
						.withMountName("FuseMirror")
						.withMountPath("J:\\")
						.build();
				try {
					fm.mount(p.toAbsolutePath(), envVars);
					System.out.println("Mounted successfully. Enter anything to stop the server...");
					System.in.read();
					fm.unmount();
					System.out.println("Unmounted successfully. Exiting...");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.err.println("Invalid directory.");
			}
		}
	}
}
