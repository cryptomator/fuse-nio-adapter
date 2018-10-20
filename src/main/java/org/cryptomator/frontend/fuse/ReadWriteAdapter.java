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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
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
	public ReadWriteAdapter(@Named("root") Path root, FileStore fileStore, LockManager lockManager, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, FileAttributesUtil attrUtil, BitMaskEnumUtil bitMaskUtil) {
		super(root, fileStore, lockManager, dirHandler, fileHandler, attrUtil);
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
		Path node = resolvePath(path);
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
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
	public int create(String path, @mode_t long mode, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, fi.flags.longValue());
			Path node = resolvePath(path);
			LOG.trace("createAndOpen {} with openOptions {}", node, flags);
			if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(attrUtil.octalModeToPosixPermissions(mode));
				return fileHandler.createAndOpen(node, fi, attrs);
			} else {
				return fileHandler.createAndOpen(node, fi);
			}
		} catch (RuntimeException e) {
			LOG.error("create failed.", e);
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
			Files.setPosixFilePermissions(node, attrUtil.octalModeToPosixPermissions(mode));
			return 0;
		} catch (NoSuchFileException e) {
			return -ErrorCodes.ENOENT();
		} catch (UnsupportedOperationException e) {
			LOG.warn("Setting posix permissions not supported by underlying file system.");
			return -ErrorCodes.ENOSYS();
		} catch (IOException | RuntimeException e) {
			LOG.error("chmod failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int unlink(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			assert !Files.isDirectory(node);
			LOG.info("Unlinking {}", path);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("unlink failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int rmdir(String path) {
		try (PathLock pathLock = lockManager.createPathLock(path).forWriting();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			LOG.trace("rmdir {}.", path);
			assert Files.isDirectory(node);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("rmdir failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	private int delete(Path node) {
		try {
			// TODO: recursively check for open file handles
			if (Files.isDirectory(node)) {
				deleteAppleDoubleFiles(node);
			}
			Files.delete(node);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException e) {
			LOG.error("Error deleting file: " + node, e);
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
	private static void deleteAppleDoubleFiles(Path node) throws IOException {
		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(node, MacUtil::isAppleDoubleOrDStoreName)) {
			for (Path p : directoryStream) {
				Files.delete(p);
			}
		}
	}

	@Override
	public int rename(String oldpath, String newpath) {
		try (PathLock oldPathLock = lockManager.createPathLock(oldpath).forWriting();
			 DataLock oldDataLock = oldPathLock.lockDataForWriting();
			 PathLock newPathLock = lockManager.createPathLock(newpath).forWriting();
			 DataLock newDataLock = newPathLock.lockDataForWriting()) {
			// TODO: recursively check for open file handles
			Path nodeOld = resolvePath(oldpath);
			Path nodeNew = resolvePath(newpath);
			LOG.info("Renaming {} to {}", oldpath, newpath);
			Files.move(nodeOld, nodeNew, StandardCopyOption.REPLACE_EXISTING);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (DirectoryNotEmptyException e) {
			return -ErrorCodes.ENOTEMPTY();
		} catch (IOException | RuntimeException e) {
			LOG.error("Renaming " + oldpath + " to " + newpath + " failed.", e);
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
			return fileHandler.utimens(node, timespec[1], timespec[0]);
		} catch (RuntimeException e) {
			LOG.error("utimens failed.", e);
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
			LOG.error("write failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int truncate(String path, @off_t long size) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.truncate(node, size);
		} catch (RuntimeException e) {
			LOG.error("truncate failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int ftruncate(String path, long size, FuseFileInfo fi) {
		try (PathLock pathLock = lockManager.createPathLock(path).forReading();
			 DataLock dataLock = pathLock.lockDataForWriting()) {
			Path node = resolvePath(path);
			return fileHandler.ftruncate(node, size, fi);
		} catch (RuntimeException e) {
			LOG.error("ftruncate failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int flush(String path, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			return fileHandler.flush(node, fi);
		} catch (RuntimeException e) {
			LOG.error("flush failed.", e);
			return -ErrorCodes.EIO();
		}
	}
}
