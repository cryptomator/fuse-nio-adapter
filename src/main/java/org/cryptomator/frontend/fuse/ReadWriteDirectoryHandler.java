package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.struct.FileStat;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

@PerAdapter
public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	@Inject
	public ReadWriteDirectoryHandler(FileAttributesUtil attrUtil) {
		super(attrUtil);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		int result = super.getattr(node, attrs, stat);
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			stat.st_mode.set(FileStat.S_IFDIR | mode);
		} else {
			stat.st_mode.set(FileStat.S_IFDIR | 0755);
		}
		return result;
	}
}
