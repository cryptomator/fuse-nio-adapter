package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.stream.Stream;

import javax.inject.Inject;

import com.google.common.collect.Iterators;
import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

@PerAdapter
public class ReadOnlyDirectoryHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyDirectoryHandler.class);
	private static final Path SAME_DIR = Paths.get(".");
	private static final Path PARENT_DIR = Paths.get("..");
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyDirectoryHandler(FileAttributesUtil attrUtil) {
		this.attrUtil = attrUtil;
	}

	public int getattr(Path path, FileStat stat) {
		stat.st_mode.set(FileStat.S_IFDIR | 0555);
		long nlinks;
		try {
			BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
			attrUtil.copyBasicFileAttributesFromNioToFuse(attr, stat);
			nlinks = 2 + countSubDirs(path);
		} catch (IOException e) {
			nlinks = 2;
		}
		stat.st_nlink.set(nlinks);
		return 0;
	}

	private long countSubDirs(Path path) throws IOException {
		try (Stream<Path> stream = Files.list(path)) {
			return stream.filter(Files::isDirectory).count();
		}
	}

	public int readdir(Path path, Pointer buf, FuseFillDir filler, long offset, FuseFileInfo fi) throws IOException {
		// fill in names and basic file attributes - however only the filetype is used...
//		Files.walkFileTree(node, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
//
//			@Override
//			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//				FileStat stat = attrUtil.basicFileAttributesToFileStat(attrs);
//				if (attrs.isDirectory()) {
//					stat.st_mode.set(FileStat.S_IFDIR | FileStat.ALL_READ | FileStat.S_IXUGO);
//				} else {
//					stat.st_mode.set(FileStat.S_IFREG | FileStat.ALL_READ);
//				}
//				filter.apply(buf, file.getFileName().toString(), stat, 0);
//				return FileVisitResult.CONTINUE;
//			}
//		});

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
