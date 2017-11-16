package org.cryptomator.frontend.fuse;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import jnr.ffi.Pointer;

public class OpenFile implements Closeable {

	private static final int BUFFER_SIZE = 4096;

	private final SeekableByteChannel channel;

	public OpenFile(Path path, OpenOption... options) throws UncheckedIOException {
		try {
			this.channel = Files.newByteChannel(path, options);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Reads up to {@code size} bytes beginning at {@code offset} into {@code buf}
	 * 
	 * @param buf Buffer
	 * @param num Number of bytes to read
	 * @param offset Position of first byte to read
	 * @return Actual number of bytes read (can be less than {@code size} if reached EOF).
	 * @throws IOException If an exception occurs during read.
	 */
	public int read(Pointer buf, long num, long offset) throws IOException {
		long size = channel.size();
		if (offset >= size) {
			return 0;
		} else {
			ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
			int read = 0;
			for (long i = 0; i < num; i += read) {
				long toRead = Math.min(BUFFER_SIZE, num - i);
				assert toRead <= BUFFER_SIZE; // i.e. we can cast it back to int
				bb.limit((int) toRead);
				read = channel.read(bb);
				if (read == -1) {
					return (int) i; // reached EOF
				}
				bb.flip();
				buf.put(i, bb.array(), 0, read);
				bb.clear();
			}
			return (int) num;
		}
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

}
