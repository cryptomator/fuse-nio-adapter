package org.cryptomator.frontend.fuse;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

public class OpenFile implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFile.class);
	private static final long MAX_MAPPED_MEMORY_SIZE = 50_000_000L; // 50 MB

	private final Path path;
	private final FileChannel channel;

	/**
	 * Whether any data has been changed on this file.
	 *
	 * This value only changes while holding a write lock, see {@link org.cryptomator.frontend.fuse.locks.LockManager}.
	 */
	private volatile boolean dirty;
	private @Nullable MappedByteBuffer mappedContents;

	private OpenFile(Path path, FileChannel channel, @Nullable MappedByteBuffer mappedContents) {
		this.path = path;
		this.channel = channel;
		this.dirty = false;
		this.mappedContents = mappedContents;
	}

	static OpenFile create(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		FileChannel ch = FileChannel.open(path, options, attrs);
		if (ch.size() < MAX_MAPPED_MEMORY_SIZE) {
			var write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW) || options.contains(StandardOpenOption.APPEND);
			var buf = ch.map(write ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY, 0L, ch.size());
			return new OpenFile(path, ch, buf);
		} else {
			return new OpenFile(path, ch, null);
		}
	}

	/**
	 * Reads up to {@code num} bytes beginning at {@code offset} into {@code buf}
	 *
	 * @param buf Buffer
	 * @param num Number of bytes to read
	 * @param offset Position of first byte to read
	 * @return Actual number of bytes read (can be less than {@code num} if reached EOF).
	 * @throws IOException If an exception occurs during read.
	 */
	public int read(ByteBuffer buf, long num, long offset) throws IOException {
		long size = channel.size();
		if (offset >= size) {
			return 0;
		} else if (num > Integer.MAX_VALUE) {
			throw new IOException("Requested too many bytes");
		} else if (mappedContents != null && offset + num < mappedContents.capacity()) {
			int toRead = (int) Math.min(num, buf.limit());
			buf.put(0, mappedContents, (int) offset, toRead);
			return toRead;
		} else {
			int read = 0;
			int toRead = (int) Math.min(num, buf.limit());
			while (read < toRead) {
				int r = channel.read(buf, offset + read);
				if (r == -1) {
					LOG.trace("Reached EOF");
					break;
				}
				read += r;
			}
			return read;
		}
	}

	/**
	 * Writes up to {@code num} bytes from {@code buf} from {@code offset} into the current file
	 *
	 * @param buf Buffer
	 * @param num Number of bytes to write
	 * @param offset Position of first byte to write at
	 * @return Actual number of bytes written
	 * @throws IOException If an exception occurs during write.
	 */
	public int write(ByteBuffer buf, long num, long offset) throws IOException {
		dirty = true;
		if (num > Integer.MAX_VALUE) {
			throw new IOException("Requested too many bytes");
		} else if (mappedContents != null && offset + num < mappedContents.capacity()) {
			int toWrite = (int) Math.min(num, buf.limit());
			mappedContents.put((int) offset, buf, 0, toWrite);
			return toWrite;
		} else {
			int written = 0;
			int toWrite = (int) Math.min(num, buf.limit());
			while (written < toWrite) {
				written += channel.write(buf, offset + written);
			}
			return written;
		}
	}

	/**
	 * Tests, if this OpenFile is <em>dirty</em>.
	 * An OpenFile is dirty, if its write method is called at least once.
	 *
	 * @return {@code true} if {@link OpenFile#write(ByteBuffer, long, long)} was called on this object, otherwise {@code false}
	 */
	boolean isDirty() {
		return dirty;
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	public void fsync(boolean metaData) throws IOException {
		if (mappedContents != null) {
			mappedContents.force();
		}
		channel.force(metaData);
		dirty = false;
	}

	public void truncate(long size) throws IOException {
		if (mappedContents != null && size > mappedContents.capacity()) {
			mappedContents.force();
			mappedContents = channel.map(FileChannel.MapMode.READ_WRITE, 0L, size);
		}
		FileChannelUtil.truncateOrExpand(channel, size);
	}

	@Override
	public String toString() {
		return "OpenFile{"
				+ "path=" + path + ", "
				+ "channel=" + channel + ", "
				+ "mappedContents=" + mappedContents
				+ "}";
	}

}
