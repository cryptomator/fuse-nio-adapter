package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.slf4j.impl.SimpleLogger;

public class MacMirroringTest {

	public static void main(String[] args) throws IOException {
		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
		System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "HH:mm:ss:SSS");
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p = Paths.get(scanner.nextLine());
			int uid = (int) Files.getAttribute(p, "unix:uid");
			int gid = (int) Files.getAttribute(p, "unix:gid");
			System.out.println("Enter mount point:");
			Path m = Paths.get(scanner.nextLine());
			if (Files.isDirectory(p) && Files.isDirectory(m)) {
				try (FuseNioAdapter fs = AdapterFactory.createReadWriteAdapter(p)) {
					fs.mount(m, false, false, new String[]{"-ouid="+uid, "-ogid="+gid, "-ovolname=FUSE-NIO-Adapter", "-oauto_xattr", "-oatomic_o_trunc", "-onoappledouble"});
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
