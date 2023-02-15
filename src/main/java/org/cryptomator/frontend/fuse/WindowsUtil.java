package org.cryptomator.frontend.fuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Windows utility class
 * <p>
 * References:
 * <ul>
 *     <li><a href="https://github.com/openjdk/panama-foreign/blob/5427e47b4dae9d22a9fa5c4ebfd8959d789b5757/doc/panama_ffi.md">Panama Tutorial (January 2023)</a></li>
 *     <li><a href="https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-">Windows Error codes 0 to 499</a></li>
 *     <li><a href="https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-formatmessagew">FormatMessageW method</a> </li>
 * </ul>
 */
public class WindowsUtil {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsUtil.class);
	private static final String FORMAT_MESSAGE_SYMBOL_NAME = "FormatMessageW";
	private static final int ERROR_SHARING_VIOLATION = 0x20;
	private static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x1000;

	static boolean runningOSIsWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	static Optional<String> getLocalizedMessageForSharingViolation() {
		try (MemorySession session = MemorySession.openConfined()) {
			var linker = Linker.nativeLinker();
			var kernel32Lib = SymbolLookup.libraryLookup("kernel32", session);
			var symbolAddress = kernel32Lib.lookup(FORMAT_MESSAGE_SYMBOL_NAME).orElseThrow(() -> new NoSuchSymbolException("kernel32", FORMAT_MESSAGE_SYMBOL_NAME));
			var formatMessageMethod = linker.downcallHandle(symbolAddress, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
			int bufSizeInUtf16Chars = 512;
			var buffer = session.allocate(bufSizeInUtf16Chars * 2);

			int utf16CharsInBuf = (int) formatMessageMethod.invoke(FORMAT_MESSAGE_FROM_SYSTEM, MemoryAddress.NULL, ERROR_SHARING_VIOLATION, 0, buffer, bufSizeInUtf16Chars, MemoryAddress.NULL);
			if (utf16CharsInBuf == 0) {
				//error case
				MethodHandle getLastErrorMethod = linker.downcallHandle(kernel32Lib.lookup("GetLastError").orElseThrow(), FunctionDescriptor.of(JAVA_INT));
				int lastErrorCode = (int) getLastErrorMethod.invoke();
				throw new LastErrorSetException(FORMAT_MESSAGE_SYMBOL_NAME + " set last-error code to " + Integer.toHexString(lastErrorCode));
			}

			return Optional.of(new String(buffer.toArray(ValueLayout.OfByte.JAVA_BYTE), 0, utf16CharsInBuf * 2, StandardCharsets.UTF_16LE));
		} catch (Throwable e) {
			LOG.error("Calling FormatMessageW failed.", e);
			return Optional.empty();
		}
	}


	private static class LastErrorSetException extends RuntimeException {
		LastErrorSetException(String msg) {
			super(msg);
		}
	}

	private static class NoSuchSymbolException extends RuntimeException {

		NoSuchSymbolException(String libraryName, String symbolName) {
			super("Symbol " + symbolName + " not found in dynamic library " + libraryName);
		}

	}
}
