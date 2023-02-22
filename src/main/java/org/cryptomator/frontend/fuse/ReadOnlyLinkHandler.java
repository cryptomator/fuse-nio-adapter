package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

class ReadOnlyLinkHandler {

	private final FileNameTranscoder fileNameTranscoder;

	public ReadOnlyLinkHandler(FileNameTranscoder fileNameTranscoder) {
		this.fileNameTranscoder = fileNameTranscoder;
	}

	public int getattr(Path path, BasicFileAttributes attrs, Stat stat) {
		if (attrs instanceof PosixFileAttributes posixAttrs) {
			stat.setPermissions(posixAttrs.permissions());
		} else {
			stat.setMode(0555);
		}
		stat.setModeBits(Stat.S_IFLNK);
		FileAttributesUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return 0;
	}

	/**
	 * Writes the target of a link followed by a NUL byte to the given buffer.
	 * @param path the link to read
	 * @param buf buffer to which the link's target shall be written
	 * @param size maximum number of bytes to be written to the buffer including a NUL byte
	 * @return
	 * @throws IOException
	 */
	public int readlink(Path path, ByteBuffer buf, long size) throws IOException {
		if(path.getParent() == null) {
			throw new NotLinkException("Root cannot be a link");
		}
		Path target = Files.readSymbolicLink(path);
		ByteBuffer fuseEncodedTarget = fileNameTranscoder.interpretAsFuseString(fileNameTranscoder.nioToFuse(target.toString()));
		int len = (int) Math.min(fuseEncodedTarget.remaining(), size - 1);
		assert len < size;
		buf.put(0, fuseEncodedTarget.array(), 0, len);
		buf.put(len, (byte) 0x00); // add null terminator
		return 0;
	}


}
