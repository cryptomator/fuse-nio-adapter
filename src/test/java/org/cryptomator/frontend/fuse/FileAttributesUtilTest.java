package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.TimeSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.nio.file.AccessMode;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

public class FileAttributesUtilTest {

	@ParameterizedTest
	@MethodSource("accessModeProvider")
	public void testAccessModeMaskToSet(Set<AccessMode> expectedModes, int mask) {
		Set<AccessMode> accessModes = FileAttributesUtil.accessModeMaskToSet(mask);
		Assertions.assertEquals(expectedModes, accessModes);
	}

	static Stream<Arguments> accessModeProvider() {
		return Stream.of( //
				Arguments.of(EnumSet.noneOf(AccessMode.class), 0), //
				Arguments.of(EnumSet.of(AccessMode.READ), 4), //
				Arguments.of(EnumSet.of(AccessMode.WRITE), 2), //
				Arguments.of(EnumSet.of(AccessMode.EXECUTE), 1), //
				Arguments.of(EnumSet.of(AccessMode.READ, AccessMode.WRITE), 4 | 2), //
				Arguments.of(EnumSet.allOf(AccessMode.class), 4 | 2 | 1) //
		);
	}

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testOctalModeToPosixPermissions(Set<PosixFilePermission> expectedPerms, long octalMode) {
		Set<PosixFilePermission> perms = FileAttributesUtil.octalModeToPosixPermissions(octalMode);
		Assertions.assertEquals(expectedPerms, perms);
	}

	static Stream<Arguments> filePermissionProvider() {
		return Stream.of( //
				Arguments.of(PosixFilePermissions.fromString("rwxr-xr-x"), 0755l), //
				Arguments.of(PosixFilePermissions.fromString("---------"), 0000l), //
				Arguments.of(PosixFilePermissions.fromString("r--r--r--"), 0444l), //
				Arguments.of(PosixFilePermissions.fromString("rwx------"), 0700l), //
				Arguments.of(PosixFilePermissions.fromString("rw-r--r--"), 0644l), //
				Arguments.of(PosixFilePermissions.fromString("-w---xr--"), 0214l) //
		);
	}

	@Test
	public void testCopyBasicFileAttributesFromNioToFuse() {
		Instant instant = Instant.ofEpochSecond(424242l, 42);
		FileTime ftime = FileTime.from(instant);
		BasicFileAttributes attr = Mockito.mock(BasicFileAttributes.class);
		Mockito.when(attr.isDirectory()).thenReturn(true);
		Mockito.when(attr.lastModifiedTime()).thenReturn(ftime);
		Mockito.when(attr.creationTime()).thenReturn(ftime);
		Mockito.when(attr.lastAccessTime()).thenReturn(ftime);
		Mockito.when(attr.size()).thenReturn(42l);

		var stat = Mockito.mock(Stat.class);
		var mtime = Mockito.mock(TimeSpec.class);
		var atime = Mockito.mock(TimeSpec.class);
		var btime = Mockito.mock(TimeSpec.class);
		Mockito.doReturn(mtime).when(stat).mTime();
		Mockito.doReturn(atime).when(stat).aTime();
		Mockito.doReturn(btime).when(stat).birthTime();
		FileAttributesUtil.copyBasicFileAttributesFromNioToFuse(attr, stat);

		Mockito.verify(stat).setModeBits(Stat.S_IFDIR);
		Mockito.verify(mtime).set(Instant.ofEpochSecond(424242L, 42L));
		Mockito.verify(atime).set(Instant.ofEpochSecond(424242L, 42L));
		Mockito.verify(btime).set(Instant.ofEpochSecond(424242L, 42L));
		Mockito.verify(stat).setSize(42L);
	}

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testPosixPermissionsToOctalMode(Set<PosixFilePermission> permissions, long expectedMode) {
		long mode = FileAttributesUtil.posixPermissionsToOctalMode(permissions);
		Assertions.assertEquals(expectedMode, mode);
	}

}
