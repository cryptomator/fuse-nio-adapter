package org.cryptomator.frontend.fuse;

import org.cryptomator.frontend.fuse.locks.AlreadyLockedException;
import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.LockManager;
import org.cryptomator.frontend.fuse.locks.PathLock;
import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
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
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

public sealed class ReadOnlyAdapter implements FuseNioAdapter permits ReadWriteAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyAdapter.class);
	private static final int BLOCKSIZE = 4096;
	protected final Errno errno;
	protected final Path root;
	private final int maxFileNameLength;
	protected final FileStore fileStore;
	protected final LockManager lockManager;
	protected final OpenFileFactory openFiles;
	protected final FileNameTranscoder fileNameTranscoder;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;
	private final ReadOnlyLinkHandler linkHandler;
	private final BooleanSupplier hasOpenFiles;

	protected ReadOnlyAdapter(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, FileStore fileStore, OpenFileFactory openFiles, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler) {
		this.errno = errno;
		this.root = root;
		this.maxFileNameLength = maxFileNameLength;
		this.fileNameTranscoder = fileNameTranscoder;
		this.fileStore = fileStore;
		this.lockManager = new LockManager();
		this.openFiles = openFiles;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
		this.linkHandler = new ReadOnlyLinkHandler(fileNameTranscoder);
		this.hasOpenFiles = () -> openFiles.getOpenFileCount() != 0;
	}

	public static ReadOnlyAdapter create(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder) {
		try {
			var fileStore = Files.getFileStore(root);
			var openFiles = new OpenFileFactory();
			var dirHandler = new ReadOnlyDirectoryHandler(fileNameTranscoder);
			var fileHandler = new ReadOnlyFileHandler(openFiles);
			return new ReadOnlyAdapter(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Errno errno() {
		return errno;
	}

	@Override
	public Set<Operation> supportedOperations() {
		return Set.of(Operation.ACCESS,
				Operation.CHMOD,
				Operation.CREATE,
				Operation.DESTROY,
				Operation.GET_ATTR,
				Operation.GET_XATTR,
				Operation.INIT,
				Operation.LIST_XATTR,
				Operation.OPEN,
				Operation.OPEN_DIR,
				Operation.READ,
				Operation.READLINK,
				Operation.READ_DIR,
				Operation.RELEASE,
				Operation.RELEASE_DIR,
				Operation.STATFS);
	}

	private String stripLeadingFrom(String string) {
		StringBuilder sb = new StringBuilder(string);
		while (!sb.isEmpty() && sb.charAt(0) == '/') {
			sb.deleteCharAt(0);
		}
		return sb.toString();
	}

	protected Path resolvePath(String absolutePath) {
		String relativePath = stripLeadingFrom(absolutePath);
		return root.resolve(relativePath);
	}

	@Override
	public int statfs(String path, Statvfs stbuf) {
		try {
			long total = fileStore.getTotalSpace();
			long avail = fileStore.getUsableSpace();
			long tBlocks = total / BLOCKSIZE;
			long aBlocks = avail / BLOCKSIZE;
			stbuf.setBsize(BLOCKSIZE);
			stbuf.setFrsize(BLOCKSIZE);
			stbuf.setBlocks(tBlocks);
			stbuf.setBavail(aBlocks);
			stbuf.setBfree(aBlocks);
			stbuf.setNameMax(maxFileNameLength);
			LOG.trace("statfs {} ({} / {})", path, avail, total);
			return 0;
		} catch (IOException | RuntimeException e) {
			LOG.error("statfs {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int access(String path, int mask) {
		try {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			Set<AccessMode> accessModes = FileAttributesUtil.accessModeMaskToSet(mask);
			return checkAccess(node, accessModes);
		} catch (RuntimeException e) {
			LOG.error("checkAccess failed.", e);
			return -errno.eio();
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
			path.getFileSystem().provider().checkAccess(path, requiredAccessModes.toArray(AccessMode[]::new));
			return 0;
		} catch (NoSuchFileException e) {
			return -errno.enoent();
		} catch (AccessDeniedException e) {
			return -errno.eacces();
		} catch (IOException e) {
			LOG.error("checkAccess failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int readlink(String path, ByteBuffer buf, long size) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			return linkHandler.readlink(node, buf, size);
		} catch (NotLinkException | NoSuchFileException e) {
			LOG.trace("readlink {} failed, node not found or not a symlink", path);
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			LOG.error("readlink failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int getattr(String path, Stat stat, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
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
			return -errno.enoent();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "getattr " + path);
		} catch (IOException | RuntimeException e) {
			LOG.error("getattr failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int getxattr(String path, String name, ByteBuffer value) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			LOG.trace("getxattr {} {}", path, name);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enotsup();
			}
			int size = xattr.size(name);
			if (value.capacity() == 0) {
				return size;
			} else if (value.remaining() < size) {
				return -errno.erange();
			} else {
				return xattr.read(name, value);
			}
		} catch (NoSuchFileException e) {
			return -errno.enoent();
		} catch (IOException e) {
			return -errno.eio();
		}
	}

	@Override
	public int listxattr(String path, ByteBuffer list) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			LOG.trace("listxattr {}", path);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enosys(); // TODO: return ENOTSUP
			}
			var names = xattr.list();
			if (list.capacity() == 0) {
				var contentBytes = xattr.list().stream().map(StandardCharsets.UTF_8::encode).mapToInt(ByteBuffer::remaining).sum();
				var nulBytes = names.size();
				return contentBytes + nulBytes; // attr1\0aattr2\0attr3\0
			} else {
				int startpos = list.position();
				for (var name : names) {
					list.put(StandardCharsets.UTF_8.encode(name)).put((byte) 0x00);
				}
				return list.position() - startpos;
			}
		} catch (BufferOverflowException e) {
			return -errno.erange();
		} catch (NoSuchFileException e) {
			return -errno.enoent();
		} catch (IOException e) {
			return -errno.eio();
		}
	}

	@Override
	public int opendir(String path, FileInfo fi) {
		return 0; // TODO
	}

	@Override
	public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("readdir {}", path);
			return dirHandler.readdir(node, filler, offset, fi);
		} catch (NotDirectoryException e) {
			LOG.error("readdir {} failed, node is not a directory.", path);
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			LOG.error("readdir failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int releasedir(String path, FileInfo fi) {
		return 0; // TODO
	}

	@Override
	public int open(String path, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("open {} ({})", path, fi.getFh());
			fileHandler.open(node, fi);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("open {} failed, file not found.", path);
			return -errno.enoent();
		} catch (AccessDeniedException e) {
			LOG.warn("Attempted to open file with unsupported flags.", e);
			return -errno.erofs();
		} catch (IOException | RuntimeException e) {
			LOG.error("open {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			LOG.trace("read {} bytes from file {} starting at {}...", size, path, offset);
			int read = fileHandler.read(buf, size, offset, fi);
			LOG.trace("read {} bytes from file {}", read, path);
			return read;
		} catch (ClosedChannelException e) {
			LOG.warn("read {} failed, invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			LOG.error("read {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int release(String path, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForReading()) {
			LOG.trace("release {} ({})", path, fi.getFh());
			fileHandler.release(fi);
			return 0;
		} catch (ClosedChannelException e) {
			LOG.warn("release {} failed, invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			LOG.error("release {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public void destroy() {
		try {
			close();
		} catch (IOException | RuntimeException e) {
			LOG.error("destroy failed.", e);
		}
	}

	@Override
	public boolean isInUse() {
		try (PathLock pLock = lockManager.tryLockForWriting("/")) {
			return hasOpenFiles.getAsBoolean();
		} catch (AlreadyLockedException e) {
			return true;
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
		String reason = e.getReason();
		reason = reason != null ? reason : "";
//		if (reason.contains("path too long") || reason.contains("name too long")) {
//			LOG.warn("{} {} failed, name too long.", opDesc);
//			return -ErrorCodes.ENAMETOOLONG();
//		} else {
		LOG.error(opDesc + " failed.", e);
		return -errno.eio();
//		}
	}
}
