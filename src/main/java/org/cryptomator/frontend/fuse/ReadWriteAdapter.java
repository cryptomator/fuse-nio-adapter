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
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public final class ReadWriteAdapter extends ReadOnlyAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteAdapter.class);
	private final ReadWriteFileHandler fileHandler;

	private ReadWriteAdapter(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, FileStore fileStore, OpenFileFactory openFiles, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, boolean enableXattr) {
		super(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler, enableXattr);
		this.fileHandler = fileHandler;
	}

	public static ReadWriteAdapter create(Errno errno, Path root, int maxFileNameLength, FileNameTranscoder fileNameTranscoder, boolean enableXattr) {
		try {
			var fileStore = Files.getFileStore(root);
			var openFiles = new OpenFileFactory();
			var dirHandler = new ReadWriteDirectoryHandler(fileNameTranscoder);
			var fileHandler = new ReadWriteFileHandler(openFiles);
			return new ReadWriteAdapter(errno, root, maxFileNameLength, fileNameTranscoder, fileStore, openFiles, dirHandler, fileHandler, enableXattr);
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
		ops.add(Operation.FSYNC);
		ops.add(Operation.MKDIR);
		ops.add(Operation.RENAME);
		ops.add(Operation.RMDIR);
		ops.add(Operation.SYMLINK);
		ops.add(Operation.TRUNCATE);
		ops.add(Operation.UNLINK);
		ops.add(Operation.UTIMENS);
		ops.add(Operation.WRITE);
		if (enableXattr) {
			ops.add(Operation.SET_XATTR);
			ops.add(Operation.REMOVE_XATTR);
		}
		return ops;
	}

	@Override
	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes) throws IOException {
		return checkAccess(path, requiredAccessModes, EnumSet.noneOf(AccessMode.class));
	}

	@Override
	public int mkdir(String path, int mode) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			Files.createDirectory(node);
			LOG.trace("mkdir {} ({})", path, mode);
			return 0;
		} catch(UnsupportedOperationException _) {
			LOG.debug("mkdir {} returns ENOTSUP due to unable to set a dir attribute atomically.", path);
			return -errno.einval();
		} catch (FileAlreadyExistsException _) {
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "mkdir " + path);
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("mkdir {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("mkdir returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int removexattr(String path, String name) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enotsup();
			}
			xattr.delete(name);
			LOG.trace("removexattr {} {}", path, name);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("removexattr {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("removexattr returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int setxattr(String path, String name, ByteBuffer value, int flags) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			var xattr = Files.getFileAttributeView(node, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (xattr == null) {
				return -errno.enotsup();
			}
			xattr.write(name, value);
			LOG.trace("setxattr {} {}", path, name);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("setxattr {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("setxattr returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int symlink(String targetPath, String linkPath) {
		try (PathLock pathLock = lockManager.lockForWriting(linkPath);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path link = resolvePath(fileNameTranscoder.fuseToNio(linkPath));
			Path target = link.getFileSystem().getPath(fileNameTranscoder.fuseToNio(targetPath));
			Files.createSymbolicLink(link, target);
			LOG.trace("symlink {} -> {}", linkPath, targetPath);
			return 0;
		} catch (FileAlreadyExistsException _) {
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "symlink " + targetPath + " -> " + linkPath);
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("symlink {} -> {} returns EIO due to exception.", linkPath, targetPath, e);
			} else {
				LOG.warn("symlink returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int create(String path, int mode, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			var flags = fi.getOpenFlags();
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(FileAttributesUtil.octalModeToPosixPermissions(mode));
				fileHandler.createAndOpen(node, fi, attrs);
			} else {
				fileHandler.createAndOpen(node, fi);
			}
			LOG.trace("create {} with flags {}", path, flags);
			return 0;
		} catch (FileAlreadyExistsException _) {
			LOG.warn("create {} failed, file already exists.", path);
			return -errno.eexist();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "create " + path);
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("create {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("create returns EIO due to {}", e.getClass().getName());
			}
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
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			Files.setPosixFilePermissions(node, FileAttributesUtil.octalModeToPosixPermissions(mode));
			LOG.trace("chmod {} ({})", path, mode);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (UnsupportedOperationException _) {
			return -errno.enotsup();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("chmod {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("chmod returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int unlink(String path) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			if (Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				return -errno.eisdir();
			}
			Files.delete(node);
			LOG.trace("unlink {}", path);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("unlink {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("unlink returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int rmdir(String path) {
		try (PathLock pathLock = lockManager.lockForWriting(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			if (!Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				throw new NotDirectoryException(path);
			}
			// TODO: recursively check for open file handles
			deleteAppleDoubleFiles(node);
			Files.delete(node);
			LOG.trace("rmdir {}", path);
			return 0;
		} catch (NotDirectoryException _) {
			return -errno.enotdir();
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (DirectoryNotEmptyException _) {
			return -errno.enotempty();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("rmdir {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("rmdir returns EIO due to {}", e.getClass().getName());
			}
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
			Files.move(nodeOld, nodeNew, StandardCopyOption.REPLACE_EXISTING);
			LOG.trace("rename {} to {}", oldPath, newPath);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (DirectoryNotEmptyException _) {
			return -errno.enotempty();
		} catch (FileSystemException e) {
			return getErrorCodeForGenericFileSystemException(e, "rename " + oldPath + " -> " + newPath);
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("rename {} to {} returns EIO due to exception.", oldPath, newPath, e);
			} else {
				LOG.warn("rename returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int utimens(String path, TimeSpec atime, TimeSpec mtime, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			fileHandler.utimens(node, mtime, atime);
			LOG.trace("utimens {} (last modification {}, last access {})", path, mtime, atime);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("utimens {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("utimens returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int write(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			LOG.trace("write {} requests {} bytes starting at {}...", path, size, offset);
			int written = fileHandler.write(buf, size, offset, fi);
			LOG.trace("write {} wrote {} bytes.", path, written);
			return written;
		} catch (ClosedChannelException _) {
			LOG.debug("write {} returns EBADF due to invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("write {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("write returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int truncate(String path, long size, FileInfo fi) {
		try (PathLock pathLock = lockManager.lockForReading(path);
			 DataLock _ = pathLock.lockDataForWriting()) {
			Path node = resolvePath(fileNameTranscoder.fuseToNio(path));
			if (fi != null) {
				fileHandler.ftruncate(size, fi);
			} else {
				fileHandler.truncate(node, size);
			}
			LOG.trace("truncate {} {}", path, size);
			return 0;
		} catch (NoSuchFileException _) {
			return -errno.enoent();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("truncate {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("truncate returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

	@Override
	public int fsync(String path, int isdatasync, FileInfo fi) {
		try {
			boolean metaData = isdatasync == 0;
			fileHandler.fsync(fi, metaData);
			LOG.trace("fsync {}", path);
			return 0;
		} catch (ClosedChannelException _) {
			LOG.debug("fsync {} returns EBADF due to invalid file handle {}", path, fi.getFh());
			return -errno.ebadf();
		} catch (IOException | RuntimeException e) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("fsync {} returns EIO due to exception.", path, e);
			} else {
				LOG.warn("fsync returns EIO due to {}", e.getClass().getName());
			}
			return -errno.eio();
		}
	}

}
