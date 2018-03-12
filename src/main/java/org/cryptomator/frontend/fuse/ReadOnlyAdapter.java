package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
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
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyAdapter(@Named("root") Path root, FileStore fileStore, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler, FileAttributesUtil attrUtil) {
		this.root = root;
		this.fileStore = fileStore;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
		this.attrUtil = attrUtil;
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
		} catch (IOException | RuntimeException e) {
			LOG.error("statfs failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int access(String path, int mask) {
		try {
			Path node = resolvePath(path);
			Set<AccessMode> accessModes = attrUtil.accessModeMaskToSet(mask);
			return checkAccess(node, accessModes);
		} catch (RuntimeException e) {
			LOG.error("checkAccess failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	protected int checkAccess(Path path, Set<AccessMode> accessModes) {
		try {
			// TODO return -EACCES, if accessMode contains WRITE
			path.getFileSystem().provider().checkAccess(path, Iterables.toArray(accessModes, AccessMode.class));
			return 0;
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (AccessDeniedException e) {
			return -ErrorCodes.EACCES();
		} catch (IOException e) {
			LOG.error("checkAccess failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int getattr(String path, FileStat stat) {
		try {
			Path node = resolvePath(path);
			BasicFileAttributes attrs = Files.readAttributes(node, BasicFileAttributes.class);
			if (attrs.isDirectory()) {
				return dirHandler.getattr(node, attrs, stat);
			} else {
				return fileHandler.getattr(node, attrs, stat);
			}
		} catch (NoSuchFileException e) {
			// see Files.notExists
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("getattr failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			return dirHandler.readdir(node, buf, filler, offset, fi);
		} catch (NotDirectoryException e) {
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("readdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int open(String path, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			// TODO do we need to distinguish files vs. dirs? https://github.com/libfuse/libfuse/wiki/Invariants
			if (Files.isDirectory(node)) {
				return -ErrorCodes.EISDIR();
			} else if (Files.exists(node)) {
				return fileHandler.open(node, fi);
			} else {
				return -ErrorCodes.ENOENT();
			}
		} catch (RuntimeException e) {
			LOG.error("open failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			assert Files.exists(node);
			return fileHandler.read(node, buf, size, offset, fi);
		} catch (RuntimeException e) {
			LOG.error("read failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int release(String path, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			return fileHandler.release(node, fi);
		} catch (RuntimeException e) {
			LOG.error("release failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public void destroy(Pointer initResult) {
		try {
			close();
		} catch (IOException | RuntimeException e) {
			LOG.error("destroy failed.", e);
		}
	}

	@Override
	public void close() throws IOException {
		fileHandler.close();
	}
}
