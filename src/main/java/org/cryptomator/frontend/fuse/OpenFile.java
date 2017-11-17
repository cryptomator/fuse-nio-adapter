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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFile implements Closeable {

	private static final Logger LOG = LoggerFactory.getLogger(OpenFile.class);
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
	public synchronized int read(Pointer buf, long num, long offset) throws IOException {
		long size = channel.size();
		if (offset >= size) {
			return 0;
		} else {
			ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
			long pos = 0;
			channel.position(offset);
			do {
				long remaining = num-pos;
				int read = readNext(bb, remaining);
				if (read == -1) {
					return (int) pos; // reached EOF TODO: wtf cast
				} else {
					LOG.trace("Reading {}-{} ({}-{})", offset+pos, offset+pos+read, offset, offset+num);
					buf.put(pos, bb.array(), 0, read);
					pos += read;
				}
			} while (pos < num);
			return (int) pos; // TODO wtf cast
		}
	}

	private int readNext(ByteBuffer readBuf, long num) throws IOException {
		readBuf.clear();
		readBuf.limit((int) Math.min(readBuf.capacity(), num));
		return channel.read(readBuf);
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

}
