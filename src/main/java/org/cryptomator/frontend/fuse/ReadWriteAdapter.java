package org.cryptomator.frontend.fuse;

import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.PathLock;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.TimeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public final class ReadWriteAdapter extends ReadOnlyAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteAdapter.class);
	private final ReadWriteFileHandler fileHandler;

	private ReadWriteAdapter(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, FileStore fileStore, OpenFileFactory openFiles, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler) {
		super(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler);
		this.fileHandler = fileHandler;
	}

	public static ReadWriteAdapter create(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder) {
		try {
			var fileStore = Files.getFileStore(root);
			var openFiles = new OpenFileFactory();
			var dirHandler = new ReadWriteDirectoryHandler(fileNameTranscoder);
			var fileHandler = new ReadWriteFileHandler(openFiles);
			return new ReadWriteAdapter(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Set<Operation> supportedOperations() {
		var ops = EnumSet.copyOf(super.supportedOperations());
		ops.add(Operation.CHMOD);
		//ops.add(Operation.CHOWN);
		ops.add(Operation.CREATE);
		//ops.add(Operation.FSYNC);
		ops.add(Operation.MKDIR);
		ops.add(Operation.RENAME);
		ops.add(Operation.RMDIR);
		ops.add(Operation.SYMLINK);
		ops.add(Operation.TRUNCATE);
		ops.add(Operation.UNLINK);
		ops.add(Operation.UTIMENS);
		ops.add(Operation.WRITE);
		return ops;
	}

	@Override
	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes) {
		return checkAccess(path, requiredAccessModes, EnumSet.noneOf(AccessMode.class));
	}

	@Override
	public int mkdir(String path, int mode) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("mkdir {} ({})", path, mode);
			Files.createDirectory(node);
			return 0;
		} catch (FileAlreadyExistsException e) {
			LOG.warn("mkdir {} failed, file already exists.", path);
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "mkdir " + path);
		} catch (IOException | RuntimeException e) {
			LOG.error("mkdir {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int symlink(String targetPath, String linkPath) {
		try (PathLock pathLock = lockManager.lockForWriting(linkPath);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path link = resolvePath(fileNameTranscoder.fuseToNio(linkPath));
			Path target = link.getFileSystem().getPath(fileNameTranscoder.fuseToNio(targetPath));
			LOG.trace("symlink {} -> {}", linkPath, targetPath);
			Files.createSymbolicLink(link, target);
			return 0;
		} catch (FileAlreadyExistsException e) {
			LOG.warn("symlink {} -> {} failed, file already exists.", linkPath, targetPath);
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "symlink " + targetPath + " -> " + linkPath);
		} catch (IOException | RuntimeException e) {
			LOG.error("symlink failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int create(String path, int mode, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			var flags = fi.getOpenFlags();
			LOG.trace("create {} with flags {}", path, flags);
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(FileAttributesUtil.octalModeToPosixPermissions(mode));
				fileHandler.createAndOpen(node, fi, attrs);
			} else {
				fileHandler.createAndOpen(node, fi);
			}
			return 0;
		} catch (FileAlreadyExistsException e) {
			LOG.warn("create {} failed, file already exists.", path);
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "create " + path);
		} catch (IOException | RuntimeException e) {
			LOG.error("create {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int chown(String path, int uid, int gid, FileInfo fi) {
		LOG.trace("Ignoring chown(uid={}, gid={}) call. Files will be served with static uid/gid.", uid, gid);
		return 0;
	}

	@Override
	public int chmod(String path, int mode, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("chmod {} ({})", path, mode);
			Files.setPosixFilePermissions(node, FileAttributesUtil.octalModeToPosixPermissions(mode));
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("chmod {} failed, file not found.", path);
			return -errno.enoent();
		} catch (UnsupportedOperationException e) {
			if (!OS.WINDOWS.isCurrent()) { //prevent spamming warnings
				LOG.warn("Setting posix permissions not supported by underlying file system.");
			}
			return -errno.enosys();
		} catch (IOException | RuntimeException e) {
			LOG.error("chmod {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int unlink(String path) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			if (Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				LOG.warn("unlink {} failed, node is a directory.", path);
				return -errno.eisdir();
			}
			LOG.trace("unlink {}", path);
			Files.delete(node);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("unlink {} failed, file not found.", path);
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			LOG.error("unlink {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int rmdir(String path) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			if (!Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				throw new NotDirectoryException(path);
			}
			LOG.trace("rmdir {}", path);
			// TODO: recursively check for open file handles
			deleteAppleDoubleFiles(node);
			Files.delete(node);
			return 0;
		} catch (NotDirectoryException e) {
			LOG.warn("rmdir {} failed, node is not a directory.", path);
			return -errno.enotdir();
		} catch (NoSuchFileException e) {
			LOG.warn("rmdir {} failed, file not found.", path);
			return -errno.enoent();
		} catch (DirectoryNotEmptyException e) {
			LOG.warn("rmdir {} failed, directory not empty.", path);
			return -errno.enotempty();
		} catch (IOException | RuntimeException e) {
			LOG.error("rmdir {} failed.", path, e);
			return -errno.eio();
		}
	}

	/**
	 * Specialised method on MacOS due to the usage of the <em>-noappledouble</em> option in the {@link org.cryptomator.frontend.fuse.mount.MacFuseMountProvider} and the possible existence of AppleDouble or DSStore-Files.
	 *
	 * @param node the directory path for which is checked for such files
	 * @throws IOException if an AppleDouble file cannot be deleted or opening of a directory stream fails
	 * @see <a href="https://github.com/osxfuse/osxfuse/wiki/Mount-options#noappledouble">OSXFuse Documentation of the <em>-noappledouble</em> option</a>
	 */
	private void deleteAppleDoubleFiles(Path node) throws IOException {
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(node, MacUtil::isAppleDoubleOrDStoreName)) {
			for (Path p : directoryStream) {
				Files.delete(p);
			}
		}
	}

	@Override
	public int rename(String oldPath, String newPath, int flags) {
		try (PathLock oldPathLock = lockManager.lockForWriting(oldPath);
			 DataLock oldDataLock = oldPathLock.lockDataForWriting();
			 PathLock newPathLock = lockManager.lockForWriting(newPath);
			 DataLock newDataLock = newPathLock.lockDataForWriting()) {
			// TODO: recursively check for open file handles
			Path nodeOld = resolvePath(fileNameTranscoder.fuseToNio(oldPath));
			Path nodeNew = resolvePath(fileNameTranscoder.fuseToNio(newPath));
			LOG.trace("rename {} to {}", oldPath, newPath);
			Files.move(nodeOld, nodeNew, StandardCopyOption.REPLACE_EXISTING);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("rename {} to {} failed, file not found.", oldPath, newPath);
			return -errno.enoent();
		} catch (DirectoryNotEmptyException e) {
			LOG.warn("rename {} to {} failed, directory not empty.", oldPath, newPath);
			return -errno.enotempty();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "rename " + oldPath + " -> " + newPath);
		} catch (IOException | RuntimeException e) {
			LOG.error("rename " + oldPath + " to " + newPath + " failed.", e);
			return -errno.eio();
		}
	}

	@Override
	public int utimens(String path, TimeSpec atime, TimeSpec mtime, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("utimens {} (last modification {}, last access {})", path, mtime, atime);
			fileHandler.utimens(node, mtime, atime);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("utimens {} failed, file not found.", path);
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			LOG.error("utimens {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int write(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			LOG.trace("write {} bytes to file {} starting at {}...", size, path, offset);
			int written = fileHandler.write(buf, size, offset, fi);
			LOG.trace("wrote {} bytes to file {}.", written, path);
			return written;
		} catch (ClosedChannelException e) {
			LOG.warn("write {} failed, invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			LOG.error("write {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int truncate(String path, long size, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			LOG.trace("truncate {} {}", path, size);
			if (fi != null) {
				fileHandler.ftruncate(size, fi);
			} else {
				fileHandler.truncate(node, size);
			}
			return 0;
		} catch (NoSuchFileException e) {
			LOG.warn("truncate {} failed, file not found.", path);
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			LOG.error("truncate {} failed.", path, e);
			return -errno.eio();
		}
	}

	@Override
	public int fsync(String path, int isdatasync, FileInfo fi) {
		try {
			boolean metaData = isdatasync == 0;
			LOG.trace("fsync {}", path);
			fileHandler.fsync(fi, metaData);
			return 0;
		} catch (ClosedChannelException e) {
			LOG.warn("fsync {} failed, invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			LOG.error("fsync {} failed.", path, e);
			return -errno.eio();
		}
	}

}
