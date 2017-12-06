package org.cryptomator.frontend.fuse;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

@PerAdapter
public class FileAttributesUtil {

	@Inject
	public FileAttributesUtil() {
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

}
