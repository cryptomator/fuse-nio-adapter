package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.CharMatcher;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 */
@PerAdapter
public class ReadOnlyAdapter extends FuseStubFS implements FuseNioAdapter {

	private final Path root;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;

	@Inject
	public ReadOnlyAdapter(@Named("root") Path root, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler) {
		this.root = root;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
	}

	private Path resolvePath(String absolutePath) {
		String relativePath = CharMatcher.is('/').trimLeadingFrom(absolutePath);
		return root.resolve(relativePath);
	}

	@Override
	public int getattr(String path, FileStat stat) {
		Path node = resolvePath(path);
		if (Files.isDirectory(node)) {
			return dirHandler.getattr(node, stat);
		} else if (Files.exists(node)) {
			return fileHandler.getattr(node, stat);
		} else {
			return -ErrorCodes.ENOENT();
		}
	}

	@Override
	public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
		Path node = resolvePath(path);
		if (!Files.isDirectory(node)) {
			return -ErrorCodes.ENOENT();
		}
		return dirHandler.readdir(node, buf, filter, offset, fi);
	}

	@Override
	public int open(String path, FuseFileInfo fi) {
		Path node = resolvePath(path);
		if (Files.isDirectory(node)) {
			return -ErrorCodes.EISDIR();
		} else if (Files.exists(node)) {
			return fileHandler.open(node, fi);
		} else {
			return -ErrorCodes.ENOENT();
		}
	}

	@Override
	public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		Path node = resolvePath(path);
		if (Files.isDirectory(node)) {
			return -ErrorCodes.EISDIR();
		} else if (Files.exists(node)) {
			return fileHandler.read(node, buf, size, offset, fi);
		} else {
			return -ErrorCodes.ENOENT();
		}
	}

	@Override
	public int release(String path, FuseFileInfo fi) {
		Path node = resolvePath(path);
		if (Files.isDirectory(node)) {
			return -ErrorCodes.EISDIR();
		} else if (Files.exists(node)) {
			return fileHandler.release(node, fi);
		} else {
			return -ErrorCodes.ENOENT();
		}
	}

	@Override
	public void close() throws IOException {
		fileHandler.close();
	}
}
