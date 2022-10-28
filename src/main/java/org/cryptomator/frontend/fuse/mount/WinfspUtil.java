package org.cryptomator.frontend.fuse.mount;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to determine location of the Winfsp binary.
 * It reads <a href="https://github.com/winfsp/winfsp/wiki/WinFsp-Registry-Settings">WinFsp registry keys</a> and caches the result.
 */
public class WinfspUtil {

	private WinfspUtil() {
	}

	private static final String REGSTR_TOKEN = "REG_SZ";
	private static final String REG_WINFSP_KEY = "HKLM\\SOFTWARE\\WOW6432Node\\WinFsp";
	private static final String REG_WINFSP_VALUE = "InstallDir";

	private static final AtomicReference<String> cache = new AtomicReference<>(null);

	static String getWinFspInstallDir() throws WinFspNotFoundException {
		if (cache.get() == null) {
			cache.set(readWinFspInstallDirFromRegistry());
		}
		return cache.get();
	}

	static String readWinFspInstallDirFromRegistry() {
		try {
			ProcessBuilder command = new ProcessBuilder("reg", "query", REG_WINFSP_KEY, "/v", REG_WINFSP_VALUE);
			Process p = command.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			p.waitFor(3000, TimeUnit.MILLISECONDS);
			if (p.exitValue() != 0) {
				throw new RuntimeException("Reading registry failed with exit code " + p.exitValue());
			}
			String result = reader.lines().filter(l -> l.contains(REG_WINFSP_VALUE)).findFirst().orElseThrow();
			return result.substring(result.indexOf(REGSTR_TOKEN) + REGSTR_TOKEN.length()).trim();
		} catch (Exception e) {
			throw new WinFspNotFoundException(e);
		}
	}

	static boolean isWinFspInstalled() {
		try {
			return Files.exists(Path.of(getWinFspInstallDir()));
		} catch (WinFspNotFoundException e) {
			return false;
		}
	}

	static class WinFspNotFoundException extends RuntimeException {

		public WinFspNotFoundException(Exception e) {
			super(e);
		}
	}

}
