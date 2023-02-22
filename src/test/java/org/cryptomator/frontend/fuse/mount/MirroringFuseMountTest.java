package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.cryptofs.DirStructure;
import org.cryptomator.cryptolib.common.MasterkeyFileAccess;
import org.cryptomator.integrations.mount.MountCapability;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountService;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
	}

	private static final Logger LOG = LoggerFactory.getLogger(MirroringFuseMountTest.class);

	/**
	 * Mirror directory
	 */
	public static class Mirror {

		public static void main(String[] args) throws MountFailedException {
			var mountService = MountService.get().findAny().orElseThrow(() -> new MountFailedException("Did not find a mount provider"));
			LOG.info("Using mount provider: {}", mountService.displayName());
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.println("Enter path to the directory you want to mirror:");
				Path p = Paths.get(scanner.nextLine());
				mount(mountService, p, scanner);
			}
		}

	}

	/**
	 * Mirror vault
	 */
	public static class CryptoFsMirror {

		public static void main(String[] args) throws IOException, NoSuchAlgorithmException, MountFailedException {
			var mountService = MountService.get().findAny().orElseThrow(() -> new MountFailedException("Did not find a mount provider"));
			LOG.info("Using mount provider: {}", mountService.displayName());
			try (Scanner scanner = new Scanner(System.in)) {
				LOG.info("Enter path to the vault you want to mirror:");
				Path vaultPath = Paths.get(scanner.nextLine());
				Preconditions.checkArgument(CryptoFileSystemProvider.checkDirStructureForVault(vaultPath, "vault.cryptomator", "masterkey.cryptomator") == DirStructure.VAULT, "Not a vault: " + vaultPath);

				LOG.info("Enter vault password:");
				String passphrase = scanner.nextLine();

				SecureRandom csprng = SecureRandom.getInstanceStrong();
				CryptoFileSystemProperties props = CryptoFileSystemProperties.cryptoFileSystemProperties()
						.withKeyLoader(url -> new MasterkeyFileAccess(new byte[0], csprng).load(vaultPath.resolve("masterkey.cryptomator"), passphrase))
						.build();
				try (FileSystem cryptoFs = CryptoFileSystemProvider.newFileSystem(vaultPath, props)) {
					Path p = cryptoFs.getPath("/");
					mount(mountService, p, scanner);
				}
			}
		}

	}

	private static void mount(MountService mountProvider, Path pathToMirror, Scanner scanner) throws MountFailedException {

		var mountBuilder = mountProvider.forFileSystem(pathToMirror);
		if (mountProvider.hasCapability(MountCapability.MOUNT_FLAGS)) {
			mountBuilder.setMountFlags(mountProvider.getDefaultMountFlags());
		}
		if (mountProvider.hasCapability(MountCapability.VOLUME_ID)) {
			mountBuilder.setVolumeId("mirror");
		}
		if (mountProvider.hasCapability(MountCapability.VOLUME_NAME)) {
			mountBuilder.setVolumeName("Mirror");
		}
		if (mountProvider.hasCapability(MountCapability.LOOPBACK_HOST_NAME)) {
			mountBuilder.setLoopbackHostName("mirrorHost");
		}
		if (mountProvider.hasCapability(MountCapability.MOUNT_TO_SYSTEM_CHOSEN_PATH)) {
			// don't set a mount point
		} else {
			LOG.info("Enter mount point: ");
			Path m = Paths.get(scanner.nextLine());
			mountBuilder.setMountpoint(m);
		}

		try (var mount = mountBuilder.mount()) {
			LOG.info("Mounted successfully to: {}", mount.getMountpoint().uri());
			LOG.info("Enter anything to unmount...");
			System.in.read();

			try {
				mount.unmount();
			} catch (UnmountFailedException e) {
				if (mountProvider.hasCapability(MountCapability.UNMOUNT_FORCED)) {
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

