package org.cryptomator.frontend.fuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

public class OpenFile implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFile.class);

	private final Path path;
	private final FileChannel channel;

	private volatile boolean dirty;

	private OpenFile(Path path, FileChannel channel) {
		this.path = path;
		this.channel = channel;
		this.dirty = false;
	}

	static OpenFile create(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		FileChannel ch = FileChannel.open(path, options, attrs);
		return new OpenFile(path, ch);
	}

	/**
	 * Reads up to {@code num} bytes beginning at {@code offset} into {@code buf}
	 *
	 * @param buf Buffer
	 * @param num Number of bytes to read
	 * @param offset Position of first byte to read
	 * @return Actual number of bytes read (can be less than {@code size} if reached EOF).
	 * @throws IOException If an exception occurs during read.
	 */
	public int read(ByteBuffer buf, long num, long offset) throws IOException {
		long size = channel.size();
		if (offset >= size) {
			return 0;
		} else if (num > Integer.MAX_VALUE) {
			throw new IOException("Requested too many bytes");
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
		}
		int written = 0;
		int toWrite = (int) Math.min(num, buf.limit());
		while (written < toWrite) {
			written += channel.write(buf, offset + written);
		}
		return written;
	}

	/**
	 * Tests, if the write method of this open file was called at least once.
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
		channel.force(metaData);
	}

	public void truncate(long size) throws IOException {
		FileChannelUtil.truncateOrExpand(channel, size);
	}

	@Override
	public String toString() {
		return "OpenFile{"
				+ "path=" + path + ", "
				+ "channel=" + channel
				+ "}";
	}

}
