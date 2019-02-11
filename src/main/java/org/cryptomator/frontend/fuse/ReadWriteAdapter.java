package org.cryptomator.frontend.fuse;

import jnr.constants.platform.OpenFlags;
import jnr.ffi.Pointer;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.uid_t;
import org.cryptomator.frontend.fuse.locks.DataLock;
import org.cryptomator.frontend.fuse.locks.LockManager;
import org.cryptomator.frontend.fuse.locks.PathLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * TODO: get the current user and save it as the file owner!
 */
@PerAdapter
public class ReadWriteAdapter extends ReadOnlyAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteAdapter.class);
	private final ReadWriteFileHandler fileHandler;
	private final FileAttributesUtil attrUtil;
	private final BitMaskEnumUtil bitMaskUtil;

	@Inject
	public ReadWriteAdapter(@Named("root") Path root, FileStore fileStore, LockManager lockManager, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, ReadOnlyLinkHandler linkHandler, FileAttributesUtil attrUtil, BitMaskEnumUtil bitMaskUtil) {
		super(root, fileStore, lockManager, dirHandler, fileHandler, linkHandler, attrUtil);
		this.fileHandler = fileHandler;
		this.attrUtil = attrUtil;
		this.bitMaskUtil = bitMaskUtil;
	}

	@Override
	protected int checkAccess(Path path, Set<AccessMode> requiredAccessModes) {
		return checkAccess(path, requiredAccessModes, EnumSet.noneOf(AccessMode.class));
	}

	@Override
	public int mkdir(String path, @mode_t long mode) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("mkdir {} ({})", path, mode);
			Files.createDirectory(node);
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException | RuntimeException e) {
			LOG.error("mkdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int symlink(String oldpath, String newpath) {
		try (PathLock pathLock = lockManager.createPathLock(newpath).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path link = resolvePath(newpath);
			Path target = link.getFileSystem().getPath(oldpath);;
			LOG.trace("symlink {} -> {}", newpath, oldpath);
			Files.createSymbolicLink(link, target);
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException | RuntimeException e) {
			LOG.error("mkdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int create(String path, @mode_t long mode, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, fi.flags.longValue());
			LOG.trace("create {} with flags {}", path, flags);
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(attrUtil.octalModeToPosixPermissions(mode));
				return fileHandler.createAndOpen(node, fi, attrs);
			} else {
				return fileHandler.createAndOpen(node, fi);
			}
		} catch (RuntimeException e) {
			LOG.error("create " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int chown(String path, @uid_t long uid, @gid_t long gid) {
		LOG.trace("Ignoring chown(uid={}, gid={}) call. Files will be served with static uid/gid.", uid, gid);
		return 0;
	}

	@Override
	public int chmod(String path, @mode_t long mode) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("chmod {} ({})", path, mode);
			Files.setPosixFilePermissions(node, attrUtil.octalModeToPosixPermissions(mode));
			return 0;
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (UnsupportedOperationException e) {
			LOG.warn("Setting posix permissions not supported by underlying file system.");
			return -ErrorCodes.ENOSYS();
		} catch (IOException | RuntimeException e) {
			LOG.error("chmod " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int unlink(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			if (Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				LOG.error("unlink {} failed, node is a directory.", path);
				return -ErrorCodes.EISDIR();
			}
			LOG.trace("unlink {}", path);
			Files.delete(node);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.error("unlink {} failed, file not found.", path);
			return -ErrorCodes.ENOENT();
		} catch (IOException | RuntimeException e) {
			LOG.error("unlink " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int rmdir(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			if (!Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
				LOG.error("rmdir {} failed, node is not a directory.", path);
				return -ErrorCodes.ENOTDIR();
			}
			LOG.trace("rmdir {}", path);
			// TODO: recursively check for open file handles
			deleteAppleDoubleFiles(node);
			Files.delete(node);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.error("rmdir {} failed, file not found.", path);
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			LOG.error("rmdir {} failed, directory not empty.", path);
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException | RuntimeException e) {
			LOG.error("rmdir " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	/**
	 * Specialised method on MacOS due to the usage of the <em>-noappledouble</em> option in the {@link org.cryptomator.frontend.fuse.mount.MacMounter} and the possible existence of AppleDouble or DSStore-Files.
	 *
	 * @param node the directory path for which is checked for such files
	 * @throws IOException if an AppleDouble file cannot be deleted or opening of a directory stream fails
	 *
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
	public int rename(String oldPath, String newPath) {
		try (PathLock oldPathLock = lockManager.createPathLock(oldPath).forWriting();
			 DataLock oldDataLock = oldPathLock.lockDataForWriting();
			 PathLock newPathLock = lockManager.createPathLock(newPath).forWriting();
			 DataLock newDataLock = newPathLock.lockDataForWriting()) {
			// TODO: recursively check for open file handles
			Path nodeOld = resolvePath(oldPath);
			Path nodeNew = resolvePath(newPath);
			LOG.trace("rename {} to {}", oldPath, newPath);
			Files.move(nodeOld, nodeNew, StandardCopyOption.REPLACE_EXISTING);
			return 0;
		} catch (NoSuchFileException e) {
			LOG.error("rename " + oldPath + " to " + newPath + " failed, file not found.", e);
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			LOG.error("rename " + oldPath + " to " + newPath + " failed, directory not empty.", e);
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException | RuntimeException e) {
			LOG.error("rename " + oldPath + " to " + newPath + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int utimens(String path, Timespec[] timespec) {
		/*
		 * From utimensat(2) man page:
		 * the array times: times[0] specifies the new "last access time" (atime);
		 * times[1] specifies the new "last modification time" (mtime).
		 */
		assert timespec.length == 2;
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("utimens {} (last modification {} sec {} nsec, last access {} sec {} nsec)", path, timespec[1].tv_sec.get(), timespec[1].tv_nsec.longValue(), timespec[0].tv_sec.get(), timespec[0].tv_nsec.longValue());
			return fileHandler.utimens(node, timespec[1], timespec[0]);
		} catch (RuntimeException e) {
			LOG.error("utimens " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.write(node, buf, size, offset, fi);
		} catch (RuntimeException e) {
			LOG.error("write " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int truncate(String path, @off_t long size) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("truncate {} {}", path, size);
			return fileHandler.truncate(node, size);
		} catch (RuntimeException e) {
			LOG.error("truncate " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int ftruncate(String path, long size, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("ftruncate {} {}", path, size);
			return fileHandler.ftruncate(node, size, fi);
		} catch (RuntimeException e) {
			LOG.error("ftruncate " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int flush(String path, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			LOG.trace("flush {}", path);
			return fileHandler.flush(node, fi);
		} catch (RuntimeException e) {
			LOG.error("flush " + path + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}
}
