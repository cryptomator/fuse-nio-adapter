package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.struct.FileStat;

import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@PerAdapter
public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	@Inject
	public ReadWriteDirectoryHandler(FileAttributesUtil attrUtil) {
		super(attrUtil);
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		int result = super.getattr(node, attrs, stat);
		stat.st_mode.set(FileStat.S_IFDIR | 0755);
		return result;
	}
}
