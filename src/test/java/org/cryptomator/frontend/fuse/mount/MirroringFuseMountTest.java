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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Scanner;

/**
 * Test programs to mirror an existing directory or vault.
 * <p>
 * Run with {@code --enable-native-access=...}
 */
public class MirroringFuseMountTest {


	private static final Logger LOG = LoggerFactory.getLogger(MirroringFuseMountTest.class);

	/**
	 * Mirror directory
	 */
	public static class Mirror {

		static {
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.LockManager", "warn");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.DataLock", "warn");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.PathLock", "warn");
			System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
			System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
		}

		public static void main(String[] args) throws MountFailedException {
			var mountService = MountService.get().findAny().orElseThrow(() -> new MountFailedException("Did not find a mount provider"));
			LOG.info("Using mount provider: {}", mountService.displayName());
			try (Scanner scanner = new Scanner(System.in)) {
				Path p = Path.of(propertyOrPrompt("mirror.dir", "Enter path to the directory you want to mirror:", scanner));
				mount(mountService, p, scanner);
			}
		}

	}

	/**
	 * Mirror vault
	 */
	public static class CryptoFsMirror {

		static {
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.LockManager", "warn");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.DataLock", "warn");
			System.setProperty("org.slf4j.simpleLogger.log.org.cryptomator.frontend.fuse.locks.PathLock", "warn");
			System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
			System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
		}

		public static void main(String[] args) throws IOException, NoSuchAlgorithmException, MountFailedException {
			var mountService = MountService.get().findAny().orElseThrow(() -> new MountFailedException("Did not find a mount provider"));
			LOG.info("Using mount provider: {}", mountService.displayName());
			try (Scanner scanner = new Scanner(System.in)) {
				Path vaultPath = Path.of(propertyOrPrompt("mirror.vault", "Enter path to the vault you want to mirror:", scanner));
				Preconditions.checkArgument(CryptoFileSystemProvider.checkDirStructureForVault(vaultPath, "vault.cryptomator", "masterkey.cryptomator") == DirStructure.VAULT, "Not a vault: " + vaultPath);

				String passphrase = propertyOrPrompt("mirror.passphrase", "Enter vault password:", scanner);

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

	// reads the given system property, prompting for the value if the property is unset or empty
	private static String propertyOrPrompt(String key, String prompt, Scanner scanner) {
		var value = System.getProperty(key, "");
		if (!value.isEmpty()) {
			return value;
		}
		LOG.info(prompt);
		return scanner.nextLine();
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
		if (mountProvider.hasCapability(MountCapability.FILE_SYSTEM_NAME)) {
			mountBuilder.setFileSystemName("MirrorFS");
		}
		if (mountProvider.hasCapability(MountCapability.MOUNT_TO_SYSTEM_CHOSEN_PATH)) {
			// don't set a mount point
		} else {
			Path m = Path.of(propertyOrPrompt("mirror.mountPoint", "Enter mount point: ", scanner));
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

