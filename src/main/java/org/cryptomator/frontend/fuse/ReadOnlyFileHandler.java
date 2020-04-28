package org.cryptomator.frontend.fuse;

import jnr.ffi.Pointer;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Set;

@PerAdapter
public class ReadOnlyFileHandler implements Closeable {

	protected final OpenFileFactory openFiles;
	protected final FileAttributesUtil attrUtil;
	private final OpenOptionsUtil openOptionsUtil;

	@Inject
	public ReadOnlyFileHandler(OpenFileFactory openFiles, FileAttributesUtil attrUtil, OpenOptionsUtil openOptionsUtil) {
		this.openFiles = openFiles;
		this.attrUtil = attrUtil;
		this.openOptionsUtil = openOptionsUtil;
	}

	public void open(Path path, FuseFileInfo fi) throws IOException {
		Set<OpenOption> openOptions = openOptionsUtil.fuseOpenFlagsToNioOpenOptions(fi.flags.longValue());
		long fileHandle = open(path, openOptions);
		fi.fh.set(fileHandle);
	}

	/**
	 * @param path        path of the file to open
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

	/**
	 * Reads up to {@code num} bytes beginning at {@code offset} into {@code buf}
	 *
	 * @param buf    Buffer
	 * @param size   Number of bytes to read
	 * @param offset Position of first byte to read
	 * @param fi     contains the file handle
	 * @return Actual number of bytes read (can be less than {@code size} if reached EOF).
	 * @throws ClosedChannelException If no open file could be found for the given file handle
	 * @throws IOException
	 */
	public int read(Pointer buf, long size, long offset, FuseFileInfo fi) throws IOException {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			throw new ClosedChannelException();
		}
		return file.read(buf, size, offset);
	}

	/**
	 * Closes the channel identified by the given fileHandle
	 *
	 * @param fi contains the file handle
	 * @throws ClosedChannelException If no channel for the given fileHandle has been found.
	 * @throws IOException
	 */
	public void release(FuseFileInfo fi) throws IOException {
		openFiles.close(fi.fh.get());
	}

	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			mode = mode & 0555;
			stat.st_mode.set(FileStat.S_IFREG | mode);
		} else {
			stat.st_mode.set(FileStat.S_IFREG | 0444);
		}
		attrUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return 0;
	}

	@Override
	public void close() throws IOException {
		openFiles.close();
	}

}
