package org.cryptomator.frontend.fuse;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Iterator;

@PerAdapter
public class ReadOnlyDirectoryHandler {

	private static final Path SAME_DIR = Paths.get(".");
	private static final Path PARENT_DIR = Paths.get("..");
	protected final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyDirectoryHandler(FileAttributesUtil attrUtil) {
		this.attrUtil = attrUtil;
	}

	public int getattr(Path path, BasicFileAttributes attrs, FileStat stat) {
		if (attrs instanceof PosixFileAttributes) {
			PosixFileAttributes posixAttrs = (PosixFileAttributes) attrs;
			long mode = attrUtil.posixPermissionsToOctalMode(posixAttrs.permissions());
			mode = mode & 0555;
			stat.st_mode.set(FileStat.S_IFDIR | mode);
		} else {
			stat.st_mode.set(FileStat.S_IFDIR | 0555);
		}
		attrUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return 0;
	}

	public int readdir(Path path, Pointer buf, FuseFillDir filler, long offset, FuseFileInfo fi) throws IOException {
		// fill in names and basic file attributes - however only the filetype is used...
//		try {
//			Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
//
//				@Override
//				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//					FileStat stat = attrUtil.basicFileAttributesToFileStat(attrs);
//					if (attrs.isDirectory()) {
//						stat.st_mode.set(FileStat.S_IFDIR | FileStat.ALL_READ | FileStat.S_IXUGO);
//					} else {
//						stat.st_mode.set(FileStat.S_IFREG | FileStat.ALL_READ);
//					}
//					if (filler.apply(buf, file.getFileName().toString(), stat, 0) != 0) {
//						throw new FillerBufferIsFullException();
//					} else {
//						return FileVisitResult.CONTINUE;
//					}
//				}
//			});
//			return 0;
//		} catch (FillerBufferIsFullException e) {
//			return -ErrorCodes.ENOMEM();
//		}

		// just fill in names, getattr gets called for each entry anyway
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
			Iterator<Path> sameAndParent = Iterators.forArray(SAME_DIR, PARENT_DIR);
			Iterator<Path> iter = Iterators.concat(sameAndParent, ds.iterator());
			while (iter.hasNext()) {
				String fileName = iter.next().getFileName().toString();
				if (filler.apply(buf, fileName, null, 0) != 0) {
					return -ErrorCodes.ENOMEM();
				}
			}
			return 0;
		} catch (DirectoryIteratorException e) {
			throw new IOException(e);
		}
	}

}
