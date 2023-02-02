package org.cryptomator.frontend.fuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class WindowsUtil {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsUtil.class);

	//see https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-formatmessage
	static String windowsWorkaround() {
		try (MemorySession session = MemorySession.openConfined()) {
			var kernel32 = SymbolLookup.libraryLookup("kernel32", session);
			//var memSegment = kernel32.lookup("FormatMessage").get();
			var linker = Linker.nativeLinker();
			MethodHandle formatMessageMethod = linker.downcallHandle(kernel32.lookup("FormatMessageW").get(),
					FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)
			);
			var buffer = session.allocate(1024); //strings are encoded in UTF-16 in Windows, e.g. 4 bytes per char. 50 chars should be sufficient
			//formatMessageMethod.invoke(0x1000, MemoryAddress.NULL, 0x20, 0, buffer, 50, null);
			int charsInBuffer = (int) formatMessageMethod.invoke(0x1000, MemoryAddress.NULL, 0x20, 0, buffer, 256, MemoryAddress.NULL);
			if( charsInBuffer == 0) {
				MethodHandle getLastErrorMethod = linker.downcallHandle(kernel32.lookup("GetLastError").get(),
						FunctionDescriptor.of(JAVA_INT)
				);
				int errorCode = (int) getLastErrorMethod.invoke();
				LOG.error("Windows error code: {}", Integer.toHexString(errorCode));
				throw new RuntimeException(Integer.toHexString(errorCode));
			} else {
				//TODO:
				var s = new String(buffer.toArray(ValueLayout.OfByte.JAVA_BYTE),0, charsInBuffer*2, StandardCharsets.UTF_16LE);
				LOG.info("The string is \"{}\" and has {} characters.",s,charsInBuffer);
				return s;
			}
		} catch (Throwable e) {
			LOG.error("Calling FormatMessage failed.",e);
		}
		return "NUTHIN'";
	}
}
