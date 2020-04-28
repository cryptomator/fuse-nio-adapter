package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.LockManager;
import org.cryptomator.frontend.fuse.locks.PathLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 */
@PerAdapter
public class ReadOnlyAdapter extends FuseStubFS implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyAdapter.class);
	private static final int BLOCKSIZE = 4096;
	protected final Path root;
	private final int maxFileNameLength;
	protected final FileStore fileStore;
	protected final LockManager lockManager;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;
	private final ReadOnlyLinkHandler linkHandler;
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyAdapter(@Named("root") Path root, @Named("maxFileNameLength") int maxFileNameLength, FileStore fileStore, LockManager lockManager, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler, ReadOnlyLinkHandler linkHandler, FileAttributesUtil attrUtil) {
		this.root = root;
		this.maxFileNameLength = maxFileNameLength;
		this.fileStore = fileStore;
		this.lockManager = lockManager;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
		this.linkHandler = linkHandler;
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
			stbuf.f_namemax.set(maxFileNameLength);
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
	public int readlink(String path, Pointer buf, long size) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			return linkHandler.readlink(node, buf, size);
		} catch (NotLinkException | NoSuchFileException e) {
			LOG.trace("readlink {} failed, node not found or not a symlink", path);
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("readlink failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int getattr(String path, FileStat stat) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			BasicFileAttributes attrs;
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				attrs = Files.readAttributes(node, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			} else {
				attrs = Files.readAttributes(node, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			}
			LOG.trace("getattr {} (lastModifiedTime: {}, lastAccessTime: {}, creationTime: {}, isRegularFile: {}, isDirectory: {}, isSymbolicLink: {}, isOther: {}, size: {}, fileKey: {})", path, attrs.lastModifiedTime(), attrs.lastAccessTime(), attrs.creationTime(), attrs.isRegularFile(), attrs.isDirectory(), attrs.isSymbolicLink(), attrs.isOther(), attrs.size(), attrs.fileKey());
			if (attrs.isDirectory()) {
				return dirHandler.getattr(node, attrs, stat);
			} else if (attrs.isRegularFile()) {
				return fileHandler.getattr(node, attrs, stat);
			} else if (attrs.isSymbolicLink()) {
				return linkHandler.getattr(node, attrs, stat);
			} else {
				throw new NoSuchFileException("Not a supported node type: " + path);
			}
		} catch (NoSuchFileException e) {
			// see Files.notExists
			LOG.trace("getattr {} failed, node not found", path);
			return -ErrorCodes.ENOENT();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "getattr " + path);
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
			LOG.trace("open {} ({})", path, fi.fh.get());
			fileHandler.open(node, fi);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("open {} failed, file not found.", path);
			return -ErrorCodes.ENOENT();
		} catch (AccessDeniedException e) {
			LOG.warn("Attempted to open file with unsupported flags.", e);
			return -ErrorCodes.EROFS();
		} catch (IOException | RuntimeException e) {
			LOG.error("open " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			LOG.trace("read {} bytes from file {} starting at {}...", size, path, offset);
			int read = fileHandler.read(buf, size, offset, fi);
			LOG.trace("read {} bytes from file {}", read, path);
			return read;
		} catch (ClosedChannelException e) {
			LOG.warn("read {} failed, invalid file handle {}", path, fi.fh.get());
			return -ErrorCodes.EBADF();
		} catch (IOException | RuntimeException e) {
			LOG.error("read " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int release(String path, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForReading()) {
			LOG.trace("release {} ({})", path, fi.fh.get());
			fileHandler.release(fi);
			return 0;
		} catch (ClosedChannelException e) {
			LOG.warn("release {} failed, invalid file handle {}", path, fi.fh.get());
			return -ErrorCodes.EBADF();
		} catch (IOException | RuntimeException e) {
			LOG.error("release " + path + " failed.", e);
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
	public boolean isMounted() {
		return mounted.get();
	}

	/*
	 * We overwrite the default implementation to skip the "internal" unmount command, because we want to use system commands instead.
	 * See also: https://github.com/cryptomator/fuse-nio-adapter/issues/29
	 */
	@Override
	public void umount() {
		// this might be called multiple times: explicitly _and_ via a shutdown hook registered during mount() in AbstractFuseFS
		if (mounted.compareAndSet(true, false)) {
			LOG.debug("Marked file system adapter as unmounted.");
		} else {
			LOG.trace("File system adapter already unmounted.");
		}
	}

	@Override
	public void close() throws IOException {
		fileHandler.close();
	}

	/**
	 * Attempts to get a specific error code that best describes the given exception.
	 * As a side effect this logs the error.
	 *
	 * @param e      An exception
	 * @param opDesc A human-friendly string describing what operation was attempted (for logging purposes)
	 * @return A specific error code or -EIO.
	 */
	protected int getErrorCodeForGenericFileSystemException(FileSystemException e, String opDesc) {
		String reason = Strings.nullToEmpty(e.getReason());
		if (reason.contains("path too long") || reason.contains("name too long")) {
			LOG.warn("{} {} failed, name too long.", opDesc);
			return -ErrorCodes.ENAMETOOLONG();
		} else {
			LOG.error(opDesc + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}
}
