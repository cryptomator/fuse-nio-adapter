package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

public class FileAttributesUtilTest {

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testOctalModeToPosixPermissions(Set<PosixFilePermission> expectedPerms, long octalMode) {
		FileAttributesUtil util = new FileAttributesUtil();
		Set<PosixFilePermission> perms = util.octalModeToPosixPermissions(octalMode);
		Assertions.assertEquals(expectedPerms, perms);
	}

	private static Stream<Arguments> filePermissionProvider() {
		return Stream.of(
				Arguments.of(PosixFilePermissions.fromString("rwxr-xr-x"), 0755l),
				Arguments.of(PosixFilePermissions.fromString("---------"), 0000l),
				Arguments.of(PosixFilePermissions.fromString("r--r--r--"), 0444l),
				Arguments.of(PosixFilePermissions.fromString("rwx------"), 0700l),
				Arguments.of(PosixFilePermissions.fromString("rw-r--r--"), 0644l),
				Arguments.of(PosixFilePermissions.fromString("--x--x---"), 0110l)
		);
	}

}
