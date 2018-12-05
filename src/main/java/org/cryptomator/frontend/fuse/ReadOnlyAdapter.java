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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.cryptomator.frontend.fuse.locks.LockManager;
import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.PathLock;
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
	private static final int FUSE_NAME_MAX = 254; // 255 is preferred, but nautilus checks for this value + 1
	protected final Path root;
	protected final FileStore fileStore;
	protected final LockManager lockManager;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyAdapter(@Named("root") Path root, FileStore fileStore, LockManager lockManager, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler, FileAttributesUtil attrUtil) {
		this.root = root;
		this.fileStore = fileStore;
		this.lockManager = lockManager;
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
			stbuf.f_namemax.set(FUSE_NAME_MAX);
			LOG.trace("statfs {} ({} / {})", path, avail, total);
			return 0;
		} catch (IOException | RuntimeException e) {
			LOG.error("statfs " + path + " failed.", e);
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

	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes) {
		return checkAccess(path, requiredAccessModes, EnumSet.of(AccessMode.WRITE));
	}

	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes, Set<AccessMode> deniedAccessModes) {
		try {
			if (!Collections.disjoint(requiredAccessModes, deniedAccessModes)) {
				throw new AccessDeniedException(path.toString());
			}
			path.getFileSystem().provider().checkAccess(path, Iterables.toArray(requiredAccessModes, AccessMode.class));
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
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			BasicFileAttributes attrs = Files.readAttributes(node, BasicFileAttributes.class);
			LOG.trace("getattr {} (lastModifiedTime: {}, lastAccessTime: {}, creationTime: {}, isRegularFile: {}, isDirectory: {}, isSymbolicLink: {}, isOther: {}, size: {}, fileKey: {})", path, attrs.lastModifiedTime(), attrs.lastAccessTime(), attrs.creationTime(), attrs.isRegularFile(), attrs.isDirectory(), attrs.isSymbolicLink(), attrs.isOther(), attrs.size(), attrs.fileKey());
			if (attrs.isDirectory()) {
				return dirHandler.getattr(node, attrs, stat);
			} else {
				return fileHandler.getattr(node, attrs, stat);
			}
		} catch (NoSuchFileException e) {
			// see Files.notExists
			LOG.trace("getattr {} failed, node not found", path);
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("getattr failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			LOG.trace("readdir {}", path);
			return dirHandler.readdir(node, buf, filler, offset, fi);
		} catch (NotDirectoryException e) {
			LOG.error("readdir {} failed, node is not a directory.", path);
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("readdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int open(String path, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			// TODO do we need to distinguish files vs. dirs? https://github.com/libfuse/libfuse/wiki/Invariants
			if (Files.isDirectory(node)) {
				LOG.error("open {} failed, node is a directory.", path);
				return -ErrorCodes.EISDIR();
			} else if (Files.exists(node)) {
				LOG.trace("open {} ({})", path, fi.fh.get());
				return fileHandler.open(node, fi);
			} else {
				LOG.error("open {} failed, file not found.", path);
				return -ErrorCodes.ENOENT();
			}
		} catch (RuntimeException e) {
			LOG.error("open failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
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
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			LOG.trace("release {} ({})", path, fi.fh.get());
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
