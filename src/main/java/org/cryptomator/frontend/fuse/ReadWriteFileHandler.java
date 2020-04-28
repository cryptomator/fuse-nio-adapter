package org.cryptomator.frontend.fuse;

import jnr.ffi.Pointer;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@PerAdapter
public class ReadWriteFileHandler extends ReadOnlyFileHandler implements Closeable {

	private static final long UTIME_NOW = -1l; // https://github.com/apple/darwin-xnu/blob/xnu-4570.1.46/bsd/sys/stat.h#L538
	private static final long UTIME_OMIT = -2l; // https://github.com/apple/darwin-xnu/blob/xnu-4570.1.46/bsd/sys/stat.h#L539

	@Inject
	public ReadWriteFileHandler(OpenFileFactory openFiles, FileAttributesUtil attrUtil, FileStore fileStore, OpenOptionsUtil openOptionsUtil) {
		super(openFiles, attrUtil, openOptionsUtil);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		int result = super.getattr(node, attrs, stat);
		if (result == 0 && attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			stat.st_mode.set(FileStat.S_IFREG | mode);
		} else if (result == 0) {
			stat.st_mode.set(FileStat.S_IFREG | 0777);
		}
		return result;
	}

	public void createAndOpen(Path path, FuseFileInfo fi, FileAttribute<?>... attrs) throws IOException {
		long fileHandle = openFiles.open(path, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE), attrs);
		fi.fh.set(fileHandle);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long open(Path path, Set<OpenOption> openOptions) throws IOException {
		return openFiles.open(path, openOptions);
	}

	/**
	 * Writes up to {@code size} bytes from {@code buf} beginning at {@code offset} into the current file
	 *
	 * @param buf    Buffer
	 * @param size   Number of bytes to write
	 * @param offset Position of first byte to write at
	 * @param fi     contains the file handle
	 * @return Actual number of bytes written
	 * @throws ClosedChannelException If no open file could be found for the given file handle
	 * @throws IOException            If an exception occurs during write.
	 */
	public int write(Pointer buf, long size, long offset, FuseFileInfo fi) throws IOException {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			throw new ClosedChannelException();
		}
		return file.write(buf, size, offset);
	}

	/**
	 * fsync
	 *
	 * @param fi       contains the file handle
	 * @param metaData fsync metadata
	 * @throws ClosedChannelException If no open file could be found for the given file handle
	 * @throws IOException            If an exception occurs during write.
	 */
	public void fsync(FuseFileInfo fi, boolean metaData) throws IOException {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			throw new ClosedChannelException();
		}
		file.fsync(metaData);
	}

	public void truncate(Path path, long size) throws IOException {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.WRITE)) {
			fc.truncate(size);
		}
	}

	/**
	 * ftruncate
	 *
	 * @param size target size
	 * @param fi   contains the file handle
	 * @throws ClosedChannelException If no open file could be found for the given file handle
	 * @throws IOException            If an exception occurs during write.
	 */
	public void ftruncate(long size, FuseFileInfo fi) throws IOException {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			throw new ClosedChannelException();
		}
		file.truncate(size);
	}

	public void utimens(Path node, Timespec mTimeSpec, Timespec aTimeSpec) throws IOException {
		FileTime mTime = toFileTime(mTimeSpec);
		FileTime aTime = toFileTime(aTimeSpec);
		BasicFileAttributeView view = Files.getFileAttributeView(node, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		view.setTimes(mTime, aTime, null); // might fail on JDK < 13, see https://bugs.openjdk.java.net/browse/JDK-8220793
	}

	private FileTime toFileTime(Timespec timespec) {
		long seconds = timespec.tv_sec.longValue();
		long nanoseconds = timespec.tv_nsec.longValue();
		if (nanoseconds == UTIME_NOW) {
			return FileTime.from(Instant.now());
		} else if (nanoseconds == UTIME_OMIT) {
			return null;
		} else {
			return FileTime.from(Instant.ofEpochSecond(seconds, nanoseconds));
		}
	}
}
