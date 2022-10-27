package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Stat;

import java.nio.file.AccessMode;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

@SuppressWarnings("OctalInteger")
public class FileAttributesUtil {

	// TODO: are default UID/GID system-dependent?
	// uid/gid are overwritten by fuse mount options -ouid=...
	private static final int DUMMY_UID = 65534; // usually nobody
	private static final int DUMMY_GID = 65534; // usually nobody

	private FileAttributesUtil(){}

	public static Set<AccessMode> accessModeMaskToSet(int mask) {
		Set<AccessMode> accessModes = EnumSet.noneOf(AccessMode.class);
		// @formatter:off
		if ((mask & 4) == 4) accessModes.add(AccessMode.READ);
		if ((mask & 2) == 2) accessModes.add(AccessMode.WRITE);
		if ((mask & 1) == 1) accessModes.add(AccessMode.EXECUTE);
		// @formatter:on
		return accessModes;
	}

	public static Set<PosixFilePermission> octalModeToPosixPermissions(long mode) {
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

	public static void copyBasicFileAttributesFromNioToFuse(BasicFileAttributes attrs, Stat stat) {
		stat.unsetModeBits(Stat.S_IFMT);
		if (attrs.isDirectory()) {
			stat.setModeBits(Stat.S_IFDIR);
		} else if (attrs.isRegularFile()) {
			stat.setModeBits(Stat.S_IFREG);
		} else if (attrs.isSymbolicLink()) {
			stat.setModeBits(Stat.S_IFLNK);
		}
		stat.setUid(DUMMY_UID);
		stat.setGid(DUMMY_GID);
		stat.mTime().set(attrs.lastModifiedTime().toInstant());
		stat.birthTime().set(attrs.creationTime().toInstant());
		stat.aTime().set(attrs.lastAccessTime().toInstant());
		stat.setSize(attrs.size());
		stat.setNLink((short) 1);
	}

	public static long posixPermissionsToOctalMode(Set<PosixFilePermission> permissions) {
		long mode = 0;
		// @formatter:off
		if (permissions.contains(PosixFilePermission.OWNER_READ))     mode = mode | 0400;
		if (permissions.contains(PosixFilePermission.GROUP_READ))     mode = mode | 0040;
		if (permissions.contains(PosixFilePermission.OTHERS_READ))    mode = mode | 0004;
		if (permissions.contains(PosixFilePermission.OWNER_WRITE))    mode = mode | 0200;
		if (permissions.contains(PosixFilePermission.GROUP_WRITE))    mode = mode | 0020;
		if (permissions.contains(PosixFilePermission.OTHERS_WRITE))   mode = mode | 0002;
		if (permissions.contains(PosixFilePermission.OWNER_EXECUTE))  mode = mode | 0100;
		if (permissions.contains(PosixFilePermission.GROUP_EXECUTE))  mode = mode | 0010;
		if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode = mode | 0001;
		// @formatter:on
		return mode;
	}

}
