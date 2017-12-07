package org.cryptomator.frontend.fuse;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.time.DateTimeException;
import java.time.Instant;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jnr.constants.platform.OpenFlags;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

@PerAdapter
public class ReadWriteFileHandler extends ReadOnlyFileHandler implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(ReadWriteFileHandler.class);
	private FileStore fileStore;

	@Inject
	public ReadWriteFileHandler(OpenFileFactory openFiles, FileAttributesUtil attrUtil, FileStore fileStore) {
		super(openFiles, attrUtil);
		this.fileStore = fileStore;
	}

	@Override
	public int getattr(Path node, FileStat stat) {
		int result = super.getattr(node, stat);
		if (result == 0 && fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
			stat.st_mode.set(FileStat.S_IFREG | 0644);
		} else if (result == 0) {
			stat.st_mode.set(FileStat.S_IFREG | 0777);
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long open(Path path, OpenFlags openFlags) throws IOException {
		switch (openFlags) {
		case O_RDWR:
			return openFiles.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
		case O_WRONLY:
			return openFiles.open(path, StandardOpenOption.WRITE);
		case O_APPEND:
			return openFiles.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		case O_TRUNC:
			return openFiles.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		default:
			return super.open(path, openFlags);
		}
	}

	public int write(Path path, Pointer buf, long size, long offset, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("File not opened: {}", path);
			return -ErrorCodes.EBADFD();
		}
		try {
			return file.write(buf, size, offset);
		} catch (IOException e) {
			LOG.error("Writing to file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int flush(Path path, FuseFileInfo fi) {
		OpenFile file = openFiles.get(fi.fh.get());
		if (file == null) {
			LOG.warn("File not opened: {}", path);
			return -ErrorCodes.EBADFD();
		}
		try {
			file.flush();
			return 0;
		} catch (IOException e) {
			LOG.error("Flushing file failed.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int truncate(Path path, long size) {
		try (FileChannel fc = FileChannel.open(path, StandardOpenOption.WRITE)) {
			fc.truncate(size);
			return 0;
		} catch (IOException e) {
			LOG.error("Truncating file feild.", e);
			return -ErrorCodes.EIO();
		}
	}

	public int utimens(Path node, Timespec mTimeSpec, Timespec aTimeSpec) {
		try {
			FileTime mTime = FileTime.from(Instant.ofEpochSecond(mTimeSpec.tv_sec.longValue(), mTimeSpec.tv_nsec.intValue()));
			FileTime aTime = FileTime.from(Instant.ofEpochSecond(aTimeSpec.tv_sec.longValue(), aTimeSpec.tv_nsec.intValue()));
			Files.getFileAttributeView(node, BasicFileAttributeView.class).setTimes(mTime, aTime, null);
			return 0;
		} catch (DateTimeException | ArithmeticException e) {
			LOG.error("Invalid argument in Instant.ofEpochSecond(...) ", e);
			return -ErrorCodes.EINVAL();
		} catch (IOException e) {
			LOG.error("Setting file access/modification times failed.", e);
			return -ErrorCodes.EIO();
		}
	}
}
