package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.struct.FileStat;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

@PerAdapter
public class FileAttributesUtil {

	private final int uid;
	private final int gid;

	@Inject
	public FileAttributesUtil(@Named("uid") int uid, @Named("gid") int gid) {
		this.uid = uid;
		this.gid = gid;
	}

	public Set<PosixFilePermission> octalModeToPosixPermissions(long mode) {
		Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
		if ((mode & 0400) == 0400)
			result.add(PosixFilePermission.OWNER_READ);
		if ((mode & 0200) == 0200)
			result.add(PosixFilePermission.OWNER_WRITE);
		if ((mode & 0100) == 0100)
			result.add(PosixFilePermission.OWNER_EXECUTE);
		if ((mode & 0040) == 0040)
			result.add(PosixFilePermission.GROUP_READ);
		if ((mode & 0020) == 0020)
			result.add(PosixFilePermission.GROUP_WRITE);
		if ((mode & 0010) == 0010)
			result.add(PosixFilePermission.GROUP_EXECUTE);
		if ((mode & 0004) == 0004)
			result.add(PosixFilePermission.OTHERS_READ);
		if ((mode & 0002) == 0002)
			result.add(PosixFilePermission.OTHERS_WRITE);
		if ((mode & 0001) == 0001)
			result.add(PosixFilePermission.OTHERS_EXECUTE);
		return result;
	}

	public void copyBasicFileAttributesFromNioToFuse(BasicFileAttributes attr, FileStat stat) {
		stat.st_uid.set(uid);
		stat.st_gid.set(gid);
		stat.st_mtim.tv_sec.set(attr.lastModifiedTime().toInstant().getEpochSecond());
		stat.st_mtim.tv_nsec.set(attr.lastModifiedTime().toInstant().getNano());
		stat.st_ctim.tv_sec.set(attr.creationTime().toInstant().getEpochSecond());
		stat.st_ctim.tv_nsec.set(attr.creationTime().toInstant().getNano());
		stat.st_atim.tv_sec.set(attr.lastAccessTime().toInstant().getEpochSecond());
		stat.st_atim.tv_nsec.set(attr.lastAccessTime().toInstant().getNano());
		stat.st_size.set(attr.size());
	}

}
