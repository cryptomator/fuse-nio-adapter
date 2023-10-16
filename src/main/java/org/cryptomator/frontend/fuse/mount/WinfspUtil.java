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
	private static final String REG_WINFSP_INSTALLDIR_VALUE = "InstallDir";
	private static final String REG_WINFSP_SXSDIR_VALUE = "SxsDir";

	private static final AtomicReference<String> cache = new AtomicReference<>(null);

	static String getWinFspSharedLibraryDir() throws WinFspNotFoundException {
		if (cache.get() == null) {
			cache.set(readWinFspDirFromRegistry() + "bin\\");
		}
		return cache.get();
	}


	static String readWinFspDirFromRegistry() {
		try {
			try {
				return readDataOfRegValue(REG_WINFSP_SXSDIR_VALUE);
			} catch (NoSuchElementException e) {
				return readDataOfRegValue(REG_WINFSP_INSTALLDIR_VALUE);
			}
		} catch (TimeoutException | IOException | ProcessHelper.CommandFailedException | NoSuchElementException e) {
			throw new WinFspNotFoundException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new WinFspNotFoundException(e);
		}
	}

	static String readDataOfRegValue(String regValue) throws IOException, InterruptedException, ProcessHelper.CommandFailedException, TimeoutException {
		ProcessBuilder command = new ProcessBuilder("reg", "query", REG_WINFSP_KEY, "/v", regValue);
		Process p = command.start();
		ProcessHelper.waitForSuccess(p, 3, "`reg query`");
		String result = p.inputReader(StandardCharsets.UTF_8).lines() //
				.filter(l -> l.contains(regValue)) //
				.findFirst().orElseThrow();
		return result.substring(result.indexOf(REGSTR_TOKEN) + REGSTR_TOKEN.length()).trim();
	}

	static boolean isWinFspInstalled() {
		try {
			return Files.exists(Path.of(getWinFspSharedLibraryDir()));
		} catch (WinFspNotFoundException e) {
			return false;
		}
	}

	static class WinFspNotFoundException extends RuntimeException {

		public WinFspNotFoundException(Exception e) {
			super(e);
		}

		public WinFspNotFoundException(String msg) {
			super(msg);
		}
	}

}
