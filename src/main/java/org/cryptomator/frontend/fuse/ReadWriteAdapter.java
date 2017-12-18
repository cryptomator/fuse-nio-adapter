package org.cryptomator.frontend.fuse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import jnr.constants.platform.OpenFlags;
import jnr.ffi.Pointer;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.uid_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
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
	private final BitMaskEnumUtil bitMaskUtil;

	@Inject
	public ReadWriteAdapter(@Named("root") Path root, FileStore fileStore, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler, FileAttributesUtil attrUtil, BitMaskEnumUtil bitMaskUtil) {
		super(root, fileStore, dirHandler, fileHandler, attrUtil);
		this.fileHandler = fileHandler;
		this.attrUtil = attrUtil;
		this.bitMaskUtil = bitMaskUtil;
	}

	@Override
	public int mkdir(String path, @mode_t long mode) {
		Path node = resolvePath(path);
		try {
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
		try {
			Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, fi.flags.longValue());
			LOG.info("createAndOpen {} with openOptions {}", path, flags);
			Path node = resolvePath(path);
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
	public int open(String path, FuseFileInfo fi) {
		Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, fi.flags.longValue());
		LOG.info("open {} with openOptions {}", path, flags);
		return super.open(path, fi);
	}

	@Override
	public int chown(String path, @uid_t long uid, @gid_t long gid) {
		LOG.info("Ignoring chown(uid={}, gid={}) call. Files will be served with static uid/gid.", uid, gid);
		return 0;
	}

	@Override
	public int chmod(String path, @mode_t long mode) {
		try {
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
		try {
			Path node = resolvePath(path);
			assert !Files.isDirectory(node);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("unlink failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int rmdir(String path) {
		try {
			Path node = resolvePath(path);
			assert Files.isDirectory(node);
			return delete(node);
		} catch (RuntimeException e) {
			LOG.error("rmdir failed.", e);
			return -ErrorCodes.EIO();
		}
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
		try {
			Path nodeOld = resolvePath(oldpath);
			Path nodeNew = resolvePath(newpath);
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
		try {
			Path node = resolvePath(path);
			return fileHandler.utimens(node, timespec[0], timespec[1]);
		} catch (RuntimeException e) {
			LOG.error("utimens failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
		try {
			Path node = resolvePath(path);
			return fileHandler.write(node, buf, size, offset, fi);
		} catch (RuntimeException e) {
			LOG.error("write failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int truncate(String path, @off_t long size) {
		try {
			Path node = resolvePath(path);
			return fileHandler.truncate(node, size);
		} catch (RuntimeException e) {
			LOG.error("truncate failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int ftruncate(String path, long size, FuseFileInfo fi) {
		try {
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
