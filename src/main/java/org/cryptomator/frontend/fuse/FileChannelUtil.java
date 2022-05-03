package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileChannelUtil {

	private static final int BUFFER_SIZE = 4096;
	private static final ByteBuffer BUFFER = ByteBuffer.allocate(BUFFER_SIZE);

	/**
	 * Truncates this channel's file to the given size by following the POSIX standard.
	 * <p>
	 * Unlike {@link java.nio.channels.FileChannel#truncate(long size)}, this implementation
	 * extends the file if it is shorter by using zero bytes as defined in the POSIX standard.
	 *
	 * @param channel channel's file
	 * @param size    target size
	 * @throws IOException If an exception occurs during truncate or write.
	 */
	public static void truncateOrExpand(FileChannel channel, long size) throws IOException {
		long currentSize = channel.size();
		if (size <= currentSize) {
			channel.truncate(size);
		} else {
			long remaining = size - currentSize;
			channel.position(currentSize);
			var buffer = BUFFER.duplicate();
			do {
				buffer.clear();
				int len = (int) Math.min(remaining, BUFFER_SIZE);
				buffer.limit(len);
				remaining -= channel.write(buffer);
			} while (remaining > 0);
		}
	}

}
