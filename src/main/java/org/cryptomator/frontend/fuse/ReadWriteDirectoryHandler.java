package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;

import ru.serce.jnrfuse.struct.FileStat;

@PerAdapter
public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	@Inject
	public ReadWriteDirectoryHandler(FileAttributesUtil attrUtil) {
		super(attrUtil);
	}

	@Override
	public int getattr(Path node, FileStat stat) {
		int result = super.getattr(node, stat);
		if (result == 0) {
			stat.st_mode.set(FileStat.S_IFDIR | 0755);
		}
		return result;
	}
}
