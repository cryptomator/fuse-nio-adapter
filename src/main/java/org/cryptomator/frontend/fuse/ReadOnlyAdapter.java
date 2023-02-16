package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
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
import java.nio.ByteBuffer;
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
import java.util.Optional;
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
	private final Optional<String> windowsSharingViolationText;

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
		if (WindowsUtil.isWindowsSystem()) {
			//the Windows exception text returned by the native call ends with a period and CRLF, the JDK exception reason not.
			this.windowsSharingViolationText = WindowsUtil.getLocalizedMessageForSharingViolation().map(String::trim).map(s -> s.substring(0, s.length() - 1));
		} else {
			this.windowsSharingViolationText = Optional.empty();
		}
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
				Operation.INIT,
				Operation.OPEN,
				Operation.OPEN_DIR,
				Operation.READ,
				Operation.READLINK,
				Operation.READ_DIR,
				Operation.RELEASE,
				Operation.RELEASE_DIR,
				Operation.STATFS);
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
			stbuf.setBsize(BLOCKSIZE);
			stbuf.setFrsize(BLOCKSIZE);
			stbuf.setBlocks(tBlocks);
			stbuf.setBavail(aBlocks);
			stbuf.setBfree(aBlocks);
			stbuf.setNameMax(maxFileNameLength);
			LOG.trace("statfs {} ({} / {})", path, avail, total);
			return 0;
		} catch (IOException | RuntimeException e) {
			LOG.error("statfs " + path + " failed.", e);
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
			path.getFileSystem().provider().checkAccess(path, Iterables.toArray(requiredAccessModes, AccessMode.class));
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
			if (e instanceof FileSystemException fse && isWrappedWindowsSharingViolationError(fse)) {
				LOG.debug("Applying Windows workaround for ERROR_SHARING_VIOLATION cause.");
				return -errno.enolck();
			}

			LOG.error("open " + path + " failed.", e);
			return -errno.eio();
		}
	}

	/**
	 * Specialised Method for Windows to check if a generic {@link FileSystemException} contains the localized Windows ERROR_SHARING_VIOLATION text.
	 *
	 * @param fse the occured {@link FileSystemException}
	 * @return {@code true}, if fse contains the ERROR_SHARING_VIOLATION text as reason.
	 */
	private boolean isWrappedWindowsSharingViolationError(FileSystemException fse) {
		return windowsSharingViolationText.map(msg -> msg.equals(fse.getReason().trim())).orElse(false);
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
			LOG.error("read " + path + " failed.", e);
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
			LOG.error("release " + path + " failed.", e);
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
	 * @param e An exception
	 * @param opDesc A human-friendly string describing what operation was attempted (for logging purposes)
	 * @return A specific error code or -EIO.
	 */
	protected int getErrorCodeForGenericFileSystemException(FileSystemException e, String opDesc) {
		String reason = Strings.nullToEmpty(e.getReason());
//		if (reason.contains("path too long") || reason.contains("name too long")) {
//			LOG.warn("{} {} failed, name too long.", opDesc);
//			return -ErrorCodes.ENAMETOOLONG();
//		} else {
			LOG.error(opDesc + " failed.", e);
			return -errno.eio();
//		}
	}
}
