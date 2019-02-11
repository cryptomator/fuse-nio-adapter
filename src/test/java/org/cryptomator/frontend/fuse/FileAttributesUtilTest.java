package org.cryptomator.frontend.fuse;

import java.nio.file.AccessMode;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import jnr.posix.util.Platform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import ru.serce.jnrfuse.flags.AccessConstants;
import ru.serce.jnrfuse.struct.FileStat;

public class FileAttributesUtilTest {

	@ParameterizedTest
	@MethodSource("accessModeProvider")
	public void testAccessModeMaskToSet(Set<AccessMode> expectedModes, int mask) {
		FileAttributesUtil util = new FileAttributesUtil();
		Set<AccessMode> accessModes = util.accessModeMaskToSet(mask);
		Assertions.assertEquals(expectedModes, accessModes);
	}

	static Stream<Arguments> accessModeProvider() {
		return Stream.of( //
				Arguments.of(EnumSet.noneOf(AccessMode.class), 0), //
				Arguments.of(EnumSet.of(AccessMode.READ), AccessConstants.R_OK), //
				Arguments.of(EnumSet.of(AccessMode.WRITE), AccessConstants.W_OK), //
				Arguments.of(EnumSet.of(AccessMode.EXECUTE), AccessConstants.X_OK), //
				Arguments.of(EnumSet.of(AccessMode.READ, AccessMode.WRITE), AccessConstants.R_OK | AccessConstants.W_OK), //
				Arguments.of(EnumSet.allOf(AccessMode.class), AccessConstants.R_OK | AccessConstants.W_OK | AccessConstants.X_OK | AccessConstants.F_OK) //
		);
	}

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testOctalModeToPosixPermissions(Set<PosixFilePermission> expectedPerms, long octalMode) {
		FileAttributesUtil util = new FileAttributesUtil();
		Set<PosixFilePermission> perms = util.octalModeToPosixPermissions(octalMode);
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

		FileAttributesUtil util = new FileAttributesUtil();
		FileStat stat = new FileStat(jnr.ffi.Runtime.getSystemRuntime());
		util.copyBasicFileAttributesFromNioToFuse(attr, stat);

		Assertions.assertTrue((FileStat.S_IFDIR & stat.st_mode.intValue()) == FileStat.S_IFDIR);
		Assertions.assertEquals(424242l, stat.st_mtim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_mtim.tv_nsec.intValue());
		Assertions.assertEquals(424242l, stat.st_ctim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_ctim.tv_nsec.intValue());
		Assumptions.assumingThat(Platform.IS_MAC || Platform.IS_WINDOWS, () -> {
			Assertions.assertEquals(424242l, stat.st_birthtime.tv_sec.get());
			Assertions.assertEquals(42, stat.st_birthtime.tv_nsec.intValue());
		});
		Assertions.assertEquals(424242l, stat.st_atim.tv_sec.get());
		Assertions.assertEquals(42, stat.st_atim.tv_nsec.intValue());
		Assertions.assertEquals(42l, stat.st_size.longValue());
	}

	@ParameterizedTest
	@MethodSource("filePermissionProvider")
	public void testPosixPermissionsToOctalMode(Set<PosixFilePermission> permissions, long expectedMode) {
		FileAttributesUtil util = new FileAttributesUtil();
		long mode = util.posixPermissionsToOctalMode(permissions);
		Assertions.assertEquals(expectedMode, mode);
	}

}
