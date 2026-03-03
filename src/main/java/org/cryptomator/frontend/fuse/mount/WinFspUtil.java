package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to determine location of the Winfsp binary.
 * It reads <a href="https://github.com/winfsp/winfsp/wiki/WinFsp-Registry-Settings">WinFsp registry keys</a> and caches the result.
 */
public class WinFspUtil {

	private static final Logger LOG = LoggerFactory.getLogger(WinFspUtil.class);

	private WinFspUtil() {
	}

	private static final String REGSTR_TOKEN = "REG_SZ";
	private static final String REG_WINFSP_KEY = "HKLM\\SOFTWARE\\WOW6432Node\\WinFsp";
	private static final String REG_WINFSP_VALUE = "InstallDir";
	private static final String FALLBACK_PATH = "C:\\Program Files (x86)\\WinFsp\\";

	private static final AtomicReference<Path> cache = new AtomicReference<>(null);


	static Path getWinFspDLLPath() {
		if (cache.get() == null) {
			var installDir = getWinFspInstallDir();
			var dllName = (System.getProperty("os.arch").toLowerCase().contains("aarch64") ? "winfsp-a64.dll" : "winfsp-x64.dll");
			cache.set(installDir.resolve("bin", dllName));
		}
		return cache.get();
	}

	/**
	 * Attempts to read the WinFsp installation directory from the registry with the "reg" tool.
	 * If this fails, the default installation path {@value FALLBACK_PATH} is returned.
	 *
	 * @return absolute path of the installation directory of WinFsp
	 */
	static Path getWinFspInstallDir() {
		Process p = null;
		try {
			var systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
			var regExe = Path.of(systemRoot, "System32", "reg.exe").toString();
			ProcessBuilder command = new ProcessBuilder(regExe, "query", REG_WINFSP_KEY, "/v", REG_WINFSP_VALUE);
			p = command.start();
			ProcessHelper.waitForSuccess(p, 3, "`reg query`");
			try (var reader = p.inputReader(StandardCharsets.UTF_8)) {
				String result = reader.lines().filter(l -> l.contains(REG_WINFSP_VALUE)).findFirst().orElseThrow();
				var installDir = result.substring(result.indexOf(REGSTR_TOKEN) + REGSTR_TOKEN.length()).trim();
				LOG.debug("Successfully read WinFsp directory {} from registry.", installDir);
				return Path.of(installDir);
			}
		} catch (TimeoutException | IOException | ProcessHelper.CommandFailedException | NoSuchElementException |
				 InvalidPathException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			LOG.debug("Failed to read WinFsp directory from registry. Using fallback path {}", FALLBACK_PATH, e);
			return Path.of(FALLBACK_PATH);
		} finally {
			if (p != null) {
				p.destroyForcibly();
			}
		}
	}

	static boolean isWinFspInstalled() {
		return Files.exists(getWinFspDLLPath());
	}

}
