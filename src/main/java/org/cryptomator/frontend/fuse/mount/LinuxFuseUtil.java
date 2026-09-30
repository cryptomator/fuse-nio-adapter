package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class to determine the location of the libfuse3 shared library.
 * The library is resolved by its soname using the dynamic linker, which honors {@code LD_LIBRARY_PATH}, the {@code ld.so.cache} and the default library directories.
 */
public class LinuxFuseUtil {

	private static final Logger LOG = LoggerFactory.getLogger(LinuxFuseUtil.class);

	private LinuxFuseUtil() {
	}

	private static final String[] SONAMES = {"libfuse3.so.3", "libfuse3.so.4"};
	private static final String PROBE_SYMBOL = "fuse_version";

	// typedef struct { const char *dli_fname; void *dli_fbase; const char *dli_sname; void *dli_saddr; } Dl_info;
	private static final StructLayout DL_INFO = MemoryLayout.structLayout( //
			ValueLayout.ADDRESS.withName("dli_fname"), //
			ValueLayout.ADDRESS.withName("dli_fbase"), //
			ValueLayout.ADDRESS.withName("dli_sname"), //
			ValueLayout.ADDRESS.withName("dli_saddr"));
	// int dladdr(const void *addr, Dl_info *info)
	private static final FunctionDescriptor DLADDR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

	/**
	 * Locates libfuse3 by loading it via <a href="https://man7.org/linux/man-pages/man3/dlopen.3.html">dlopen</a> and querying its file name via <a href="https://man7.org/linux/man-pages/man3/dladdr.3.html">dladdr</a>.
	 *
	 * @return absolute path of the libfuse3 shared library, or an empty optional if the library cannot be found
	 */
	static Optional<Path> findLibFuse3() {
		for (var soname : SONAMES) {
			try (var arena = Arena.ofConfined()) {
				var probe = SymbolLookup.libraryLookup(soname, arena).findOrThrow(PROBE_SYMBOL);
				var dladdr = Linker.nativeLinker().downcallHandle(Linker.nativeLinker().defaultLookup().findOrThrow("dladdr"), DLADDR);
				var info = arena.allocate(DL_INFO);
				int status = (int) dladdr.invokeExact(probe, info);
				if (status == 0) {
					LOG.debug("dladdr failed to resolve {} in {}.", PROBE_SYMBOL, soname);
					continue;
				}
				// dli_fname is the first member of Dl_info
				var fileName = info.get(ValueLayout.ADDRESS, 0).reinterpret(Long.MAX_VALUE).getString(0);
				var libPath = Path.of(fileName).toAbsolutePath();
				LOG.debug("Resolved {} to {}.", soname, libPath);
				return Optional.of(libPath);
			} catch (Throwable e) {
				// locating the library is best effort: any failure (library not found, foreign linkage, ...) moves on to the next soname
				LOG.debug("Failed to locate {}.", soname, e);
			}
		}
		return Optional.empty();
	}

}
