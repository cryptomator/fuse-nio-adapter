package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Stat;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	public ReadWriteDirectoryHandler(FileNameTranscoder fileNameTranscoder) {
		super(fileNameTranscoder);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, Stat stat) {
		int result = super.getattr(node, attrs, stat);
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			stat.setPermissions(posixAttrs.permissions());
		} else {
			stat.setModeBits(0755);
		}
		return result;
	}
}
