package org.cryptomator.frontend.fuse;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import javax.inject.Inject;

import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

@PerAdapter
public class ReadOnlyFileHandler implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyFileHandler.class);

	protected final OpenFileFactory openFiles;
	private final FileAttributesUtil attrUtil;
	private final OpenOptionsUtil openOptionsUtil;

	@Inject
	public ReadOnlyFileHandler(OpenFileFactory openFiles, FileAttributesUtil attrUtil, OpenOptionsUtil openOptionsUtil) {
		this.openFiles = openFiles;
		this.attrUtil = attrUtil;
		this.openOptionsUtil = openOptionsUtil;
	}

	public int open(Path path, FuseFileInfo fi) {
		try {
			Set<OpenOption> openOptions = openOptionsUtil.fuseOpenFlagsToNioOpenOptions(fi.flags.longValue());
			long fileHandle = open(path, openOptions);
			fi.fh.set(fileHandle);
			return 0;
		} catch (AccessDeniedException e) {
			LOG.warn("Attempted to open file with unsupported flags.", e);
			return -ErrorCodes.EROFS();
		} catch (IOException e) {
			LOG.error("Error opening file.", e);
			return -ErrorCodes.EIO();
		}
	}

	/**
	 * @param path path of the file to open
	 * @param openOptions file open options
	 * @return file handle used to identify and close open files.
	 * @throws AccessDeniedException Thrown if the requested openOptions are not supported
	 * @throws IOException
	 */
	protected long open(Path path, Set<OpenOption> openOptions) throws AccessDeniedException, IOException {
		if (openOptions.contains(StandardOpenOption.WRITE)) {
			throw new AccessDeniedException(path.toString(), null, "Unsupported open options: WRITE");
		} else {
			return openFiles.open(path, StandardOpenOption.READ);
		}
	}

	public int read(Path path, Pointer buf, long size, long offset, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("Attempted to read from file with illegal fileHandle {}: {}", fi.fh.get(), path);
			return -ErrorCodes.EBADF();
		}
		try {
			return file.read(buf, size, offset);
		} catch (IOException e) {
			LOG.error("Reading file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int release(Path path, FuseFileInfo fi) {
		try {
			openFiles.close(fi.fh.get());
			return 0;
		} catch (ClosedChannelException e) {
			LOG.warn("Attempted to close file with illegal fileHandle {}: {}", fi.fh.get(), path);
			return -ErrorCodes.EBADF();
		} catch (IOException e) {
			LOG.error("Error closing file.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		stat.st_mode.set(FileStat.S_IFREG | 0444);
		try {
			attrUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		} catch (IllegalArgumentException e) {
			LOG.warn("Wrong ciphertext file size of file {}. Setting the displayed cleartext file size to zero.", node.toString());
			stat.st_size.set(0);
		}
		return 0;
	}

	@Override
	public void close() throws IOException {
		openFiles.close();
	}

}
