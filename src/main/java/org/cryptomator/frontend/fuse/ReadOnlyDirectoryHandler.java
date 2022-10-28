package org.cryptomator.frontend.fuse;

import com.google.common.collect.Iterators;
import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Stat;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Iterator;

public class ReadOnlyDirectoryHandler {

	private static final Path SAME_DIR = Paths.get(".");
	private static final Path PARENT_DIR = Paths.get("..");
	private final FileNameTranscoder fileNameTranscoder;

	public ReadOnlyDirectoryHandler(FileNameTranscoder fileNameTranscoder) {
		this.fileNameTranscoder = fileNameTranscoder;
	}

	public int getattr(Path path, BasicFileAttributes attrs, Stat stat) {
		if (attrs instanceof PosixFileAttributes posixAttrs) {
			stat.setPermissions(posixAttrs.permissions());
		} else {
			stat.setMode(0555);
		}
		FileAttributesUtil.copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return 0;
	}

	public int readdir(Path path, DirFiller filler, long offset, FileInfo fi) throws IOException {
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
				filler.fill(fileNameTranscoder.nioToFuse(fileName));
			}
			return 0;
		} catch (DirectoryIteratorException e) {
			throw new IOException(e);
		}
	}

}
