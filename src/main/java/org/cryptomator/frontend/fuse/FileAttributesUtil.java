package org.cryptomator.frontend.fuse;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import ru.serce.jnrfuse.struct.FileStat;

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
		// @formatter:off
		if ((mode & 0400) == 0400) result.add(PosixFilePermission.OWNER_READ);
		if ((mode & 0200) == 0200) result.add(PosixFilePermission.OWNER_WRITE);
		if ((mode & 0100) == 0100) result.add(PosixFilePermission.OWNER_EXECUTE);
		if ((mode & 0040) == 0040) result.add(PosixFilePermission.GROUP_READ);
		if ((mode & 0020) == 0020) result.add(PosixFilePermission.GROUP_WRITE);
		if ((mode & 0010) == 0010) result.add(PosixFilePermission.GROUP_EXECUTE);
		if ((mode & 0004) == 0004) result.add(PosixFilePermission.OTHERS_READ);
		if ((mode & 0002) == 0002) result.add(PosixFilePermission.OTHERS_WRITE);
		if ((mode & 0001) == 0001) result.add(PosixFilePermission.OTHERS_EXECUTE);
		// @formatter:on
		return result;
	}

	public FileStat basicFileAttributesToFileStat(BasicFileAttributes attrs) {
		FileStat stat = new FileStat(jnr.ffi.Runtime.getSystemRuntime());
		copyBasicFileAttributesFromNioToFuse(attrs, stat);
		return stat;
	}

	public void copyBasicFileAttributesFromNioToFuse(BasicFileAttributes attrs, FileStat stat) {
		if (attrs.isDirectory()) {
			stat.st_mode.set(stat.st_mode.longValue() | FileStat.S_IFDIR);
		} else {
			stat.st_mode.set(stat.st_mode.longValue() | FileStat.S_IFREG);
		}
		stat.st_uid.set(uid);
		stat.st_gid.set(gid);
		stat.st_mtim.tv_sec.set(attrs.lastModifiedTime().toInstant().getEpochSecond());
		stat.st_mtim.tv_nsec.set(attrs.lastModifiedTime().toInstant().getNano());
		stat.st_birthtime.tv_sec.set(attrs.creationTime().toInstant().getEpochSecond());
		stat.st_birthtime.tv_nsec.set(attrs.creationTime().toInstant().getNano());
		stat.st_atim.tv_sec.set(attrs.lastAccessTime().toInstant().getEpochSecond());
		stat.st_atim.tv_nsec.set(attrs.lastAccessTime().toInstant().getNano());
		stat.st_size.set(attrs.size());
	}

}
