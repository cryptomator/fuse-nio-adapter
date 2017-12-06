package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

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
	private final int uid;
	private final int gid;

	@Inject
	public ReadOnlyDirectoryHandler(@Named("uid") int uid, @Named("gid") int gid) {
		this.uid = uid;
		this.gid = gid;
	}

	public int getattr(Path node, FileStat stat) {
		stat.st_mode.set(FileStat.S_IFDIR | 0555);
		long nlinks;
		try {
			stat.st_uid.set(uid);
			stat.st_gid.set(gid);
			BasicFileAttributes attr = Files.readAttributes(node, BasicFileAttributes.class);
			stat.st_mtim.tv_sec.set(attr.lastModifiedTime().toInstant().getEpochSecond());
			stat.st_ctim.tv_sec.set(attr.creationTime().toInstant().getEpochSecond());
			stat.st_atim.tv_sec.set(attr.lastAccessTime().toInstant().getEpochSecond());
			stat.st_size.set(Files.size(node));
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
