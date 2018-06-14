package org.cryptomator.frontend.fuse;

import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;

import javax.inject.Inject;

import ru.serce.jnrfuse.struct.FileStat;

@PerAdapter
public class ReadWriteDirectoryHandler extends ReadOnlyDirectoryHandler {

	private final FileStore fileStore;

	@Inject
	public ReadWriteDirectoryHandler(FileAttributesUtil attrUtil, FileStore fileStore) {
		super(attrUtil);
		this.fileStore = fileStore;
	}

	@Override
	public int getattr(Path node, BasicFileAttributes attrs, FileStat stat) {
		int result = super.getattr(node, attrs, stat);
		stat.st_mode.set(FileStat.S_IFDIR | 0755);
		return result;
	}
}
