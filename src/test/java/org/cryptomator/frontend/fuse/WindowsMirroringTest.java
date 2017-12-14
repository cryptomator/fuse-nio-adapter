package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class WindowsMirroringTest {

	private static final boolean IS_WIN = System.getProperty("os.name").contains("Windows");

	public static void main(String[] args) throws IOException {
		if (!IS_WIN) {
			System.err.println("To be run on Windows");
		}
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p = Paths.get(scanner.nextLine());
			if (Files.isDirectory(p)) {
				try (FuseNioAdapter fs = AdapterFactory.createReadWriteAdapter(p)) {
					fs.mount(Paths.get("J:\\"), false, true, new String[] {"-ouid=-1", "-ogid=-1", "-ovolname=FUSE-NIO-Adapter", "-oatomic_o_trunc"});
					System.out.println("Mounted successfully. Enter anything to stop the server...");
					System.in.read();
					fs.umount();
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
