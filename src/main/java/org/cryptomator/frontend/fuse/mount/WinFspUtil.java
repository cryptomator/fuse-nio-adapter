package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to determine location of the Winfsp binary.
 * It reads <a href="https://github.com/winfsp/winfsp/wiki/WinFsp-Registry-Settings">WinFsp registry keys</a> and caches the result.
 */
public class WinFspUtil {

	private static final Logger LOG = LoggerFactory.getLogger(WinFspUtil.class);

	private WinFspUtil() {
	}

	private static final String REG_WINFSP_KEY = "SOFTWARE\\WOW6432Node\\WinFsp";
	private static final String REG_WINFSP_VALUE = "InstallDir";
	private static final String FALLBACK_PATH = "C:\\Program Files (x86)\\WinFsp\\";

	// HKEY_LOCAL_MACHINE is defined as ((HKEY) (ULONG_PTR) ((LONG) 0x80000002)), i.e. sign-extended to pointer size
	private static final MemorySegment HKEY_LOCAL_MACHINE = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0002L);
	private static final int RRF_RT_REG_SZ = 0x00000002;
	private static final int ERROR_SUCCESS = 0;
	// LSTATUS RegGetValueW(HKEY hkey, LPCWSTR lpSubKey, LPCWSTR lpValue, DWORD dwFlags, LPDWORD pdwType, PVOID pvData, LPDWORD pcbData)
	private static final FunctionDescriptor REG_GET_VALUE_W = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

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
	 * Attempts to read the WinFsp installation directory from the registry via <a href="https://learn.microsoft.com/en-us/windows/win32/api/winreg/nf-winreg-reggetvaluew">RegGetValueW</a>.
	 * If this fails, the default installation path {@value FALLBACK_PATH} is returned.
	 *
	 * @return absolute path of the installation directory of WinFsp
	 */
	static Path getWinFspInstallDir() {
		try (var arena = Arena.ofConfined()) {
			var regGetValueW = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("Advapi32.dll", arena).findOrThrow("RegGetValueW"), REG_GET_VALUE_W);
			var subKey = arena.allocateFrom(REG_WINFSP_KEY, StandardCharsets.UTF_16LE);
			var valueName = arena.allocateFrom(REG_WINFSP_VALUE, StandardCharsets.UTF_16LE);
			var dataSize = arena.allocate(ValueLayout.JAVA_INT);

			// first call determines the required buffer size in bytes
			int status = (int) regGetValueW.invokeExact(HKEY_LOCAL_MACHINE, subKey, valueName, RRF_RT_REG_SZ, MemorySegment.NULL, MemorySegment.NULL, dataSize);
			if (status != ERROR_SUCCESS) {
				throw new RegistryQueryFailedException(status);
			}

			// second call reads the value into a buffer with room for a terminating null character
			int bufferSize = dataSize.get(ValueLayout.JAVA_INT, 0) + 2;
			var data = arena.allocate(bufferSize);
			dataSize.set(ValueLayout.JAVA_INT, 0, bufferSize);
			status = (int) regGetValueW.invokeExact(HKEY_LOCAL_MACHINE, subKey, valueName, RRF_RT_REG_SZ, MemorySegment.NULL, data, dataSize);
			if (status != ERROR_SUCCESS) {
				throw new RegistryQueryFailedException(status);
			}

			var installDir = Path.of(data.getString(0, StandardCharsets.UTF_16LE));
			LOG.debug("Successfully read WinFsp directory {} from registry.", installDir);
			return installDir;
		} catch (Throwable e) {
			// reading the registry is best effort: any failure (foreign linkage, missing key, malformed value, ...) falls back to the default location
			LOG.debug("Failed to read WinFsp directory from registry. Using fallback path {}", FALLBACK_PATH, e);
			return Path.of(FALLBACK_PATH);
		}
	}

	static boolean isWinFspInstalled() {
		return Files.exists(getWinFspDLLPath());
	}

	private static class RegistryQueryFailedException extends Exception {

		RegistryQueryFailedException(int status) {
			super("RegGetValueW(HKEY_LOCAL_MACHINE\\" + REG_WINFSP_KEY + ", " + REG_WINFSP_VALUE + ") failed with error code " + status);
		}

	}

}
