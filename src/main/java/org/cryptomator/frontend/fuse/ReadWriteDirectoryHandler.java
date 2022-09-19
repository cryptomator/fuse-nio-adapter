package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Stat;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

@PerAdapter
public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	@Inject
	public ReadWriteDirectoryHandler(FileAttributesUtil attrUtil, FileNameTranscoder fileNameTranscoder) {
		super(attrUtil, fileNameTranscoder);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, Stat stat) {
		int result = super.getattr(node, attrs, stat);
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			stat.setPermissions(posixAttrs.permissions());
		} else {
			stat.toggleMode(0755, true);
		}
		return result;
	}
}
