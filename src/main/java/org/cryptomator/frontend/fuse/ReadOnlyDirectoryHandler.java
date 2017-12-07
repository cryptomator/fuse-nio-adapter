package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jnr.ffi.Pointer;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

@PerAdapter
public class ReadOnlyDirectoryHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyDirectoryHandler.class);
	private final FileAttributesUtil attrUtil;

	@Inject
	public ReadOnlyDirectoryHandler(FileAttributesUtil attrUtil) {
		this.attrUtil = attrUtil;
	}

	public int getattr(Path node, FileStat stat) {
		stat.st_mode.set(FileStat.S_IFDIR | 0555);
		long nlinks;
		try {
			BasicFileAttributes attr = Files.readAttributes(node, BasicFileAttributes.class);
			attrUtil.copyBasicFileAttributesFromNioToFuse(attr, stat);
			nlinks = 2 + Files.list(node).filter(Files::isDirectory).count();
		} catch (IOException e) {
			nlinks = 2;
		}
		stat.st_nlink.set(nlinks);
		return 0;
	}

	public int readdir(Path node, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
		filter.apply(buf, ".", null, 0);
		filter.apply(buf, "..", null, 0);

		try (Stream<Path> stream = Files.list(node)) {
			stream.map(Path::getFileName).map(Path::toString).forEach(fileName -> {
				filter.apply(buf, fileName, null, 0);
			});
			return 0;
		} catch (IOException e) {
			LOG.error("Dir Listing failed.", e);
			return -ErrorCodes.EIO();
		}
	}

}
