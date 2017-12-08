package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.CharMatcher;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 */
@PerAdapter
public class ReadOnlyAdapter extends FuseStubFS implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyAdapter.class);
	private static final int BLOCKSIZE = 4096;
	protected final Path root;
	protected final FileStore fileStore;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;

	@Inject
	public ReadOnlyAdapter(@Named("root") Path root, FileStore fileStore, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler) {
		this.root = root;
		this.fileStore = fileStore;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
	}

	protected Path resolvePath(String absolutePath) {
		String relativePath = CharMatcher.is('/').trimLeadingFrom(absolutePath);
		return root.resolve(relativePath);
	}

	@Override
	public int statfs(String path, Statvfs stbuf) {
		try {
			long total = fileStore.getTotalSpace();
			long avail = fileStore.getUsableSpace();
			long tBlocks = total / BLOCKSIZE;
			long aBlocks = avail / BLOCKSIZE;
			stbuf.f_bsize.set(BLOCKSIZE);
			stbuf.f_frsize.set(BLOCKSIZE);
			stbuf.f_blocks.set(tBlocks);
			stbuf.f_bavail.set(aBlocks);
			stbuf.f_bfree.set(aBlocks);
			return 0;
		} catch (IOException e) {
			LOG.error("statfs failed.", e);
			return -ErrorCodes.EIO();
		}
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
