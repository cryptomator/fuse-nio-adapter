package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import jnr.ffi.Pointer;
import jnr.ffi.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Read-Only FUSE-NIO-Adapter based on Sergey Tselovalnikov's <a href="https://github.com/SerCeMan/jnr-fuse/blob/0.5.1/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java">HelloFuse</a>
 * TODO: get the current user and save it as the file owner!
 */
@PerAdapter
public class ReadWriteAdapter extends ReadOnlyAdapter implements FuseNioAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyDirectoryHandler.class);
	private final Path root;
	private final ReadWriteDirectoryHandler dirHandler;
	private final ReadWriteFileHandler fileHandler;

	@Inject
	public ReadWriteAdapter(@Named("root") Path root, ReadWriteDirectoryHandler dirHandler, ReadWriteFileHandler fileHandler) {
		super(root, dirHandler, fileHandler);
		this.root = root;
		this.dirHandler = dirHandler;
		this.fileHandler = fileHandler;
	}

	/**
	 * TODO: check if this function should be implemented at all!
	 *
	 * @param var1
	 * @param var2
	 * @param var4
	 * @return
	 */
	@Override
	public int mknod(String var1, @mode_t long var2, @dev_t long var4) {
		Path absPath = resolvePath(var1);
		if (Files.isDirectory(absPath)) {
			return -ErrorCodes.EISDIR();
		} else if (Files.exists(absPath)) {
			return -ErrorCodes.EEXIST();
		} else {
			try {
				//TODO: take POSIX permisiions in var2 into FileAttributes!
				Files.createFile(absPath);
				return 0;
			} catch (IOException e) {
				return -ErrorCodes.EIO();
			}
		}
	}

	/**
	 * TODO: löschen, wenn nicht gebraucht
	 * UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
	 * Files.setOwner(node, lookupService.lookupPrincipalByName(System.getProperty("user.name")));
	 *
	 * @param path
	 * @param mode
	 * @return
	 */
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

	/**
	 * TODO: Maybe create a file attribute object to set the metadata more atomically
	 *
	 * @param path
	 * @param mode
	 * @param fi
	 * @return
	 */
	@Override
	public int create(String path, @mode_t long mode, FuseFileInfo fi) {
		Path node = resolvePath(path);
		try {
			Files.createFile(node);
			//TODO: maybe create an instance of the lookup service as a class variable
			Files.setPosixFilePermissions(node, octalModeToPosixPermissions(mode));
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
		//return -ErrorCodes.ENOSYS();
		return 0;
	}

	@Override
	public int chmod(String path, @mode_t long mode) {
		Path node = resolvePath(path);
		if (!Files.exists(node)) {
			return -ErrorCodes.ENOENT();
		} else {
			try {
				Files.setPosixFilePermissions(node, octalModeToPosixPermissions(mode));
				return 0;
			} catch (IOException e) {
				e.printStackTrace();
				return -ErrorCodes.EIO();
			}
		}
	}


	/**
	 * test-method, needs refactoring
	 * but works properly
	 *
	 * @param mode
	 * @return
	 */
	private Set<PosixFilePermission> octalModeToPosixPermissions(long mode) {
		Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
		if ((mode & 0400) == 0400) result.add(PosixFilePermission.OWNER_READ);
		if ((mode & 0200) == 0200) result.add(PosixFilePermission.OWNER_WRITE);
		if ((mode & 0100) == 0100) result.add(PosixFilePermission.OWNER_EXECUTE);
		if ((mode & 0040) == 0040) result.add(PosixFilePermission.GROUP_READ);
		if ((mode & 0020) == 0020) result.add(PosixFilePermission.GROUP_WRITE);
		if ((mode & 0010) == 0010) result.add(PosixFilePermission.GROUP_EXECUTE);
		if ((mode & 0004) == 0004) result.add(PosixFilePermission.OTHERS_READ);
		if ((mode & 0002) == 0002) result.add(PosixFilePermission.OTHERS_WRITE);
		if ((mode & 0001) == 0001) result.add(PosixFilePermission.OTHERS_EXECUTE);
		return result;
	}

	@Override
	public int unlink(String var1) {
		Path node = resolvePath(var1);
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
			LOG.info("Error:", e);
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
			LOG.error("", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int utimens(String path, Timespec[] timespec) {
		Path node = resolvePath(path);
		if (!Files.exists(node)) {
			return -ErrorCodes.ENOENT();
		} else {
			try {
				//TODO::: implement it right
				Files.setLastModifiedTime(node, FileTime.from(Instant.now()));
				Files.setAttribute(node, "lastAccessTime", FileTime.from(Instant.now()));
				return 0;
			} catch (IOException e) {
				LOG.error("", e);
				return -ErrorCodes.EIO();
			}
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
		try (FileChannel fc = FileChannel.open(node, StandardOpenOption.WRITE)) {
			fc.truncate(size);
			return 0;
		} catch (IOException e) {
			LOG.error("", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int flush(String path, FuseFileInfo fi) {
		Path node = resolvePath(path);
		return fileHandler.flush(node);
	}
}

