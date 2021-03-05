package org.cryptomator.frontend.fuse;

import jnr.ffi.Pointer;
import org.cryptomator.frontend.fuse.encoding.BufferEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.struct.FileStat;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

@PerAdapter
class ReadOnlyLinkHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyLinkHandler.class);

	private final FileAttributesUtil attrUtil;
	private final BufferEncoder toFuseEncoder;

	@Inject
	public ReadOnlyLinkHandler(FileAttributesUtil attrUtil, BufferEncoder toFuseEncoder) {
		this.attrUtil = attrUtil;
		this.toFuseEncoder = toFuseEncoder;
	}

	public int getattr(Path path, BasicFileAttributes attrs, FileStat stat) {
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			mode = mode & 0555;
			stat.st_mode.set(FileStat.S_IFLNK | mode);
		} else {
			stat.st_mode.set(FileStat.S_IFLNK | 0555);
		}
		attrUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
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
	public int readlink(Path path, Pointer buf, long size) throws IOException {
		Path target = Files.readSymbolicLink(path);
		var encodedTarget= toFuseEncoder.encode(target.toString());
		int maxSize = size == 0 ? 0 : (int) size - 1;
		buf.put(0, encodedTarget.array(),0,encodedTarget.capacity());
		buf.putByte(maxSize, (byte) 0x00);
		return 0;
	}


}
