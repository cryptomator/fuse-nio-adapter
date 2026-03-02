package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to determine location of the Winfsp binary.
 * It reads <a href="https://github.com/winfsp/winfsp/wiki/WinFsp-Registry-Settings">WinFsp registry keys</a> and caches the result.
 */
public class WinfspUtil {

	private static final Logger LOG = LoggerFactory.getLogger(WinfspUtil.class);

	private WinfspUtil() {
	}

	private static final String REGSTR_TOKEN = "REG_SZ";
	private static final String REG_WINFSP_KEY = "HKLM\\SOFTWARE\\WOW6432Node\\WinFsp";
	private static final String REG_WINFSP_VALUE = "InstallDir";
	private static final String FALLBACK_PATH = "C:\\Program Files (x86)\\WinFsp\\";

	private static final AtomicReference<String> cache = new AtomicReference<>(null);

	static String getWinFspInstallDir() {
		if (cache.get() == null) {
			cache.set(readWinFspInstallDirFromRegistry());
		}
		return cache.get();
	}

	static String readWinFspInstallDirFromRegistry() {
		try {
			ProcessBuilder command = new ProcessBuilder("reg", "query", REG_WINFSP_KEY, "/v", REG_WINFSP_VALUE);
			Process p = command.start();
			ProcessHelper.waitForSuccess(p, 3, "`reg query`");
			try (var reader = p.inputReader(StandardCharsets.UTF_8)) {
				String result = reader.lines().filter(l -> l.contains(REG_WINFSP_VALUE)).findFirst().orElseThrow();
				var installDir = result.substring(result.indexOf(REGSTR_TOKEN) + REGSTR_TOKEN.length()).trim();
				LOG.debug("Successfully read WinFsp directory {} from registry.", installDir);
				return installDir;
			}
		} catch (TimeoutException | IOException | ProcessHelper.CommandFailedException | NoSuchElementException e) {
			//no-op
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		LOG.debug("Failed to read WinFsp directory from registry. Using fallback path {}", FALLBACK_PATH);
		return FALLBACK_PATH;
	}

	static boolean isWinFspInstalled() {
		return Files.exists(Path.of(getWinFspInstallDir()));
	}

}
