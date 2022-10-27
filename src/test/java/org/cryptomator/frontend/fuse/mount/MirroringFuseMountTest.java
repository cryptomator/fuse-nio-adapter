package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.cryptofs.DirStructure;
import org.cryptomator.cryptolib.common.MasterkeyFileAccess;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountFeature;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Scanner;

/**
 * Test programs to mirror an existing directory or vault.
 * <p>
 * Run with {@code --enable-native-access=...}
 */
public class MirroringFuseMountTest {

	static {
		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
		System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "HH:mm:ss.SSS");
	}

	private static final Logger LOG = LoggerFactory.getLogger(MirroringFuseMountTest.class);

	/**
	 * Mirror directory
	 */
	public static class Mirror {

		public static void main(String[] args) throws MountFailedException {
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("Enter path to the directory you want to mirror:");
				Path p = Paths.get(scanner.nextLine());
				mount(p, scanner);
			}
		}

	}

	/**
	 * Mirror vault
	 */
	public static class CryptoFsMirror {

		public static void main(String[] args) throws IOException, NoSuchAlgorithmException, MountFailedException {
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("Enter path to the vault you want to mirror:");
				Path vaultPath = Paths.get(scanner.nextLine());
				Preconditions.checkArgument(CryptoFileSystemProvider.checkDirStructureForVault(vaultPath, "vault.cryptomator", "masterkey.cryptomator") == DirStructure.VAULT, "Not a vault: " + vaultPath);

				System.out.println("Enter vault password:");
				String passphrase = scanner.nextLine();

				SecureRandom csprng = SecureRandom.getInstanceStrong();
				CryptoFileSystemProperties props = CryptoFileSystemProperties.cryptoFileSystemProperties()
						.withKeyLoader(url -> new MasterkeyFileAccess(new byte[0], csprng).load(vaultPath.resolve("masterkey.cryptomator"), passphrase))
						.build();
				try (FileSystem cryptoFs = CryptoFileSystemProvider.newFileSystem(vaultPath, props)) {
					Path p = cryptoFs.getPath("/");
					mount(p, scanner);
				}
			}
		}

	}

	private static void mount(Path pathToMirror, Scanner scanner) throws MountFailedException {
		var mountProvider = MountProvider.get().findAny().orElseThrow(() -> new MountFailedException("Did not find a mount provider"));
		LOG.info("Using mount provider: {}", mountProvider.displayName());
		var mountBuilder = mountProvider.forFileSystem(pathToMirror);
		if (mountProvider.supportedFeatures().contains(MountFeature.MOUNT_FLAGS)) {
			mountBuilder.setMountFlags(mountProvider.getDefaultMountFlags("mirror"));
		}
		if (mountProvider.supportedFeatures().contains(MountFeature.DEFAULT_MOUNT_POINT)) {
			mountBuilder.setMountpoint(mountProvider.getDefaultMountPoint("mirror"));
		} else {
			System.out.println("Enter mount point: ");
			Path m = Paths.get(scanner.nextLine());
			mountBuilder.setMountpoint(m);
		}

		try (var mount = mountBuilder.mount()) {
			LOG.info("Mounted successfully to: {}", mount.getMountpoint());
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				LOG.info("Revealing {}...", mount.getMountpoint());
				Desktop.getDesktop().open(mount.getMountpoint().toFile());
			}

			LOG.info("Enter anything to unmount...");
			System.in.read();

			try {
				mount.unmout();
			} catch (UnmountFailedException e) {
				if (mountProvider.supportedFeatures().contains(MountFeature.UNMOUNT_FORCED)) {
					LOG.warn("Graceful unmount failed. Attempting force-unmount...");
					mount.unmountForced();
				}
			}
		} catch (UnmountFailedException e) {
			LOG.warn("Unmount failed.", e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

