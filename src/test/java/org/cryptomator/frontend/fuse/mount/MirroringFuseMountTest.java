package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class MirroringFuseMountTest {

	static {
		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
		System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "HH:mm:ss.SSS");
	}

	private static final Logger LOG = LoggerFactory.getLogger(MirroringFuseMountTest.class);

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Enter path to the directory you want to mirror:");
			Path p = Paths.get(scanner.nextLine());
			System.out.println("Enter mount point:");
			Path m = Paths.get(scanner.nextLine());
			if (m.startsWith(p) || p.startsWith(m)) {
				System.err.println("Mirrored directory and mount location must not be nested.");
			} else if (Files.isDirectory(p)) {
				mount(p, m);
			} else {
				LOG.error("Invalid directory.");
			}
		}
	}

	private static void mount(Path pathToMirror, Path mountPoint) {
		Mounter mounter = FuseMountFactory.getMounter();
		EnvironmentVariables envVars = EnvironmentVariables.create()
				.withMountName("FuseMirror")
				.withMountPath(mountPoint)
				.build();
		try (Mount mnt = mounter.mount(pathToMirror, envVars)) {
			LOG.info("Mounted successfully. Enter anything to stop the server...");
			mnt.revealInFileManager();
			System.in.read();
			LOG.info("Unmounted successfully. Exiting...");
		} catch (IOException | CommandFailedException e) {
			LOG.error("Mount failed", e);
		}
	}
}
