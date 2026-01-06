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

import static org.cryptomator.jfuse.api.FuseOperations.Operation.GET_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.LIST_XATTR;

public sealed class ReadOnlyAdapter implements FuseNioAdapter permits ReadWriteAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyAdapter.class);
	private static final int BLOCKSIZE = 4096;

	protected final Errno errno;
	protected final Path root;
	private final int maxFileNameLength;
	protected final FileStore fileStore;
	protected final boolean enableXattr;
	protected final LockManager lockManager;
	protected final OpenFileFactory openFiles;
	protected final FileNameTranscoder fileNameTranscoder;
	private final ReadOnlyDirectoryHandler dirHandler;
	private final ReadOnlyFileHandler fileHandler;
	private final ReadOnlyLinkHandler linkHandler;

	protected ReadOnlyAdapter(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, FileStore fileStore, OpenFileFactory openFiles, ReadOnlyDirectoryHandler dirHandler, ReadOnlyFileHandler fileHandler, boolean enableXattr) {
		this.errno = errno;
		this.root = root;
		this.maxFileNameLength = maxFileNameLength;
		this.fileNameTranscoder = fileNameTranscoder;
		this.fileStore = fileStore;
		this.enableXattr = enableXattr;
		this.lockManager = new LockManager();
		this.openFiles = openFiles;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
		this.linkHandler = new ReadOnlyLinkHandler(fileNameTranscoder);
	}

	public static ReadOnlyAdapter create(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, boolean enableXattr) {
		try {
			var fileStore = Files.getFileStore(root);
			var openFiles = new OpenFileFactory();
			var dirHandler = new ReadOnlyDirectoryHandler(fileNameTranscoder);
			var fileHandler = new ReadOnlyFileHandler(openFiles);
			return new ReadOnlyAdapter(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler, enableXattr);
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
		var supportedOps = EnumSet.of(Operation.ACCESS,
				Operation.CHMOD,
				Operation.CREATE,
				Operation.DESTROY,
				Operation.GET_ATTR,
				GET_XATTR,
				Operation.INIT,
				LIST_XATTR,
				Operation.OPEN,
				Operation.OPEN_DIR,
				Operation.READ,
				Operation.READLINK,
				Operation.READ_DIR,
				Operation.RELEASE,
				Operation.RELEASE_DIR,
				Operation.STATFS);
		if (!enableXattr) {
			supportedOps.remove(GET_XATTR);
			supportedOps.remove(LIST_XATTR);
		}
		return supportedOps;
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
			if(LOG.isDebugEnabled()) {
				LOG.debug("statfs {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("statfs returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int access(String path, int mask) {
		try {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			Set<AccessMode> accessModes = FileAttributesUtil.accessModeMaskToSet(mask);
			return checkAccess(node, accessModes);
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("access {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("access returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes) throws IOException {
		return checkAccess(path, requiredAccessModes, EnumSet.of(AccessMode.WRITE));
	}

	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes, Set<AccessMode> deniedAccessModes) throws IOException {
		try {
			if (!Collections.disjoint(requiredAccessModes, deniedAccessModes)) {
				throw new AccessDeniedException(path.toString());
			}
			path.getFileSystem().provider().checkAccess(path, requiredAccessModes.toArray(AccessMode[]::new));
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (AccessDeniedException _) {
			return -errno.eacces();
		}
	}

	@Override
	public int readlink(String path, ByteBuffer buf, long size) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			return linkHandler.readlink(node, buf, size);
		} catch (NotLinkException _) {
			return -errno.einval();
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("readlink {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("readlink returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int getattr(String path, Stat stat, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForReading()) {
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
				throw new UnsupportedFileTypeException(path);
			}
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (UnsupportedFileTypeException _) {
			LOG.debug("getattr {} returns EINVAL due to unsupported file type.", path);
			return -errno.einval();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("getattr {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("getattr returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int getxattr(String path, String name, ByteBuffer value) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enotsup();
			}
			//we use this approach because on different file systems different execptions are thrown when accessing xattr
			//	e.g. on Windows a NoSuchFileException, on Linux a generic FileSystenException is thrown
			if (xattr.list().stream().noneMatch(key -> key.equals(name))) {
				return switch (OS.current()) {
					case MAC -> -errno.enoattr();
					default -> -errno.enodata();
				};
			}
			int size = xattr.size(name);
			if (value.capacity() == 0) {
				return size;
			} else if (value.remaining() < size) {
				return -errno.erange();
			} else {
				return xattr.read(name, value);
			}
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("getxattr {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("getxattr returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int listxattr(String path, ByteBuffer list) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForReading()) {
			Path node = resolvePath(path);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enotsup();
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
		} catch (BufferOverflowException _) {
			return -errno.erange();
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("listxattr {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("listxattr returns EIO due to {}", e.getClass().getName());
			}
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
			 DataLock _ = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			return dirHandler.readdir(node, filler, offset, fi);
		} catch (NotDirectoryException _) {
			LOG.debug("readdir {} returns ENOTDIR due to being non-directory.", path);
			return -errno.enotdir();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("readdir {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("readdir returns EIO due to {}", e.getClass().getName());
			}
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
			 DataLock _ = pathLock.lockDataForReading()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			fileHandler.open(node, fi);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (AccessDeniedException e) {
			return -errno.erofs();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("open {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("open returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForReading()) {
			LOG.trace("read {} request {} bytes starting at {}...", path, size, offset);
			int read = fileHandler.read(buf, size, offset, fi);
			LOG.trace("read {} recieved {} bytes", path, read);
			return read;
		} catch (IllegalArgumentException _) {
			return -errno.einval();
		} catch (ClosedChannelException _) {
			LOG.debug("read {} returns EBADF due to invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("read {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("read returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int release(String path, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			fileHandler.release(fi);
			return 0;
		} catch (ClosedChannelException _) {
			LOG.debug("release {} returns EBADF due to invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("release {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("release returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public void destroy() {
		try {
			close();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("destroy failed.", e);
			} else {
				LOG.warn("destroy failed due to {}.", e.getClass().getName());
			}
		}
	}

	@Override
	public boolean isInUse() {
		try (PathLock pLock = lockManager.tryLockForWriting("/")) {
			return openFiles.hasDirtyFiles();
		} catch (AlreadyLockedException _) {
			return true;
		}
	}

	@Override
	public void close() throws IOException {
		fileHandler.close();
	}
}
