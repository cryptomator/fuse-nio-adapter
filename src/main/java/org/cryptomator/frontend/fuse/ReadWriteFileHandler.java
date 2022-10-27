package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.TimeSpec;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.EnumSet;
import java.util.Set;

public class ReadWriteFileHandler extends ReadOnlyFileHandler implements Closeable {

	public ReadWriteFileHandler(OpenFileFactory openFiles) {
		super(openFiles);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, Stat stat) {
		int result = super.getattr(node, attrs, stat);
		if (result == 0 && attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			stat.setPermissions(posixAttrs.permissions());
		} else if (result == 0) {
			stat.setModeBits(0777);
		}
		return result;
	}

	public void createAndOpen(Path path, FileInfo fi, FileAttribute<?>... attrs) throws IOException {
		long fileHandle = openFiles.open(path, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE), attrs);
		fi.setFh(fileHandle);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long open(Path path, Set<StandardOpenOption> openOptions) throws IOException {
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
	public int write(ByteBuffer buf, long size, long offset, FileInfo fi) throws IOException {
		OpenFile file = openFiles.get(fi.getFh());
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
	public void fsync(FileInfo fi, boolean metaData) throws IOException {
		OpenFile file = openFiles.get(fi.getFh());
		if (file == null) {
			throw new ClosedChannelException();
		}
		file.fsync(metaData);
	}

	public void truncate(Path path, long size) throws IOException {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.WRITE)) {
			FileChannelUtil.truncateOrExpand(fc, size);
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
	public void ftruncate(long size, FileInfo fi) throws IOException {
		OpenFile file = openFiles.get(fi.getFh());
		if (file == null) {
			throw new ClosedChannelException();
		}
		file.truncate(size);
	}

	public void utimens(Path node, TimeSpec mTimeSpec, TimeSpec aTimeSpec) throws IOException {
		FileTime mTime = mTimeSpec.getOptional().map(FileTime::from).orElse(null);
		FileTime aTime = aTimeSpec.getOptional().map(FileTime::from).orElse(null);
		BasicFileAttributeView view = Files.getFileAttributeView(node, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		view.setTimes(mTime, aTime, null);
	}

}
