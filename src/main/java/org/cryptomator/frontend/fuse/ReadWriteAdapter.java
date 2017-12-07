package org.cryptomator.frontend.fuse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jnr.ffi.Pointer;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.uid_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.Flock;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

/**
 * TODO: get the current user and save it as the file owner!
 */
@PerAdapter
public class ReadWriteAdapter extends ReadOnlyAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteAdapter.class);
	private final ReadWriteFileHandler fileHandler;
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadWriteAdapter(@Named("root") Path root, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, FileAttributesUtil attrUtil) {
		super(root, dirHandler, fileHandler);
		this.fileHandler = fileHandler;
		this.attrUtil = attrUtil;
	}

	@Override
	public int mknod(String path, long mode, long rdev) {
		Path absPath = resolvePath(path);
		if (Files.isDirectory(absPath)) {
			return -ErrorCodes.EISDIR();
		} else if (Files.exists(absPath)) {
			return -ErrorCodes.EEXIST();
		} else {
			try {
				// TODO: take POSIX permisiions in mode into FileAttributes!
				Files.createFile(absPath);
				return 0;
			} catch (IOException e) {
				return -ErrorCodes.EIO();
			}
		}
	}

	@Override
	public int mkdir(String path, @mode_t long mode) {
		Path node = resolvePath(path);
		try {
			Files.createDirectory(node);
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException e) {
			LOG.error("Exception occured", e);
			return -ErrorCodes.EIO();
		}

	}

	@Override
	public int create(String path, @mode_t long mode, FuseFileInfo fi) {
		Path node = resolvePath(path);
		try {
			Files.createFile(node, PosixFilePermissions.asFileAttribute(attrUtil.octalModeToPosixPermissions(mode)));
			return 0;
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException e) {
			LOG.error("Exception occured", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int chown(String path, @uid_t long uid, @gid_t long gid) {
		LOG.info("Ignoring chown(uid={}, gid={}) call. Files will be served with static uid/gid.", uid, gid);
		return 0;
	}

	@Override
	public int chmod(String path, @mode_t long mode) {
		Path node = resolvePath(path);
		if (!Files.exists(node)) {
			return -ErrorCodes.ENOENT();
		} else {
			try {
				Files.setPosixFilePermissions(node, attrUtil.octalModeToPosixPermissions(mode));
				return 0;
			} catch (UnsupportedOperationException e) {
				LOG.warn("Setting posix permissions not supported by underlying file system.");
				return -ErrorCodes.ENOSYS();
			} catch (IOException e) {
				LOG.error("Error changing file attributes: " + node, e);
				return -ErrorCodes.EIO();
			}
		}
	}

	@Override
	public int unlink(String path) {
		Path node = resolvePath(path);
		assert !Files.isDirectory(node);
		return delete(node);
	}

	@Override
	public int rmdir(String path) {
		Path node = resolvePath(path);
		assert Files.isDirectory(node);
		return delete(node);
	}

	private int delete(Path node) {
		try {
			Files.delete(node);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (IOException e) {
			LOG.error("Error deleting file: " + node, e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int rename(String oldpath, String newpath) {
		Path nodeOld = resolvePath(oldpath);
		Path nodeNew = resolvePath(newpath);
		try {
			Files.move(nodeOld, nodeNew);
			return 0;
		} catch (FileNotFoundException e) {
			return -ErrorCodes.ENOENT();
		} catch (FileAlreadyExistsException e) {
			return -ErrorCodes.EEXIST();
		} catch (IOException e) {
			LOG.error("Renaming " + nodeOld + " to " + nodeNew + " failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int utimens(String path, Timespec[] timespec) {
		Path node = resolvePath(path);
		if (!Files.exists(node)) {
			return -ErrorCodes.ENOENT();
		} else {
			/*
			 From utimensat(2) man page:
			 the array times: times[0] specifies the new "last access time" (atime);
			 times[1] specifies the new "last modification time" (mtime).
			 */
			assert timespec.length == 2;
			return fileHandler.utimens(node, timespec[0], timespec[1]);
		}
	}

	@Override
	public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		Path node = resolvePath(path);
		return fileHandler.write(node, buf, size, offset, fi);
	}

	@Override
	public int truncate(String path, @off_t long size) {
		Path node = resolvePath(path);
		return fileHandler.truncate(node, size);
	}

	@Override
	public int flush(String path, FuseFileInfo fi) {
		Path node = resolvePath(path);
		return fileHandler.flush(node);
	}
}
