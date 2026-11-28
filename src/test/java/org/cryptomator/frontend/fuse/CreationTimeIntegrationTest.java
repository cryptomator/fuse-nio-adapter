package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.TimeSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;

public class CreationTimeIntegrationTest {

	private static final Instant T = Instant.parse("2020-01-02T03:04:05Z");

	private Path tmpDir;
	private FuseNioAdapter adapter;
	private int enoent;

	@BeforeEach
	void setup(@TempDir Path tmpDir) {
		var errno = Fuse.builder().errno();
		this.tmpDir = tmpDir;
		this.enoent = errno.enoent();
		this.adapter = ReadWriteAdapter.create(errno, tmpDir, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, FileNameTranscoder.transcoder(), true);
	}

	@Test
	@DisplayName("setcrtime sets the creation time of a file")
	@EnabledOnOs(OS.MAC)
	void testSetcrtimeOfFile() throws IOException {
		Files.createFile(tmpDir.resolve("f.txt"));
		var mtime = attributes(tmpDir.resolve("f.txt")).lastModifiedTime();

		Assertions.assertEquals(0, adapter.setcrtime("/f.txt", new FixedTimeSpec(T)));

		var attrs = attributes(tmpDir.resolve("f.txt"));
		Assertions.assertEquals(FileTime.from(T), attrs.creationTime());
		Assertions.assertEquals(mtime, attrs.lastModifiedTime());
	}

	@Test
	@DisplayName("setcrtime sets the creation time of a directory")
	@EnabledOnOs(OS.MAC)
	void testSetcrtimeOfDirectory() throws IOException {
		adapter.mkdir("/d", 0755);
		var mtime = attributes(tmpDir.resolve("d")).lastModifiedTime();

		Assertions.assertEquals(0, adapter.setcrtime("/d", new FixedTimeSpec(T)));

		var attrs = attributes(tmpDir.resolve("d"));
		Assertions.assertEquals(FileTime.from(T), attrs.creationTime());
		Assertions.assertEquals(mtime, attrs.lastModifiedTime());
	}

	@Test
	@DisplayName("setcrtime on a symlink leaves its target alone")
	@EnabledOnOs(OS.MAC)
	void testSetcrtimeOfSymlink() throws IOException {
		Files.createFile(tmpDir.resolve("t.txt"));
		Files.createSymbolicLink(tmpDir.resolve("l"), Path.of("t.txt"));

		Assertions.assertEquals(0, adapter.setcrtime("/l", new FixedTimeSpec(T)));

		Assertions.assertEquals(FileTime.from(T), attributes(tmpDir.resolve("l")).creationTime());
		Assertions.assertNotEquals(FileTime.from(T), attributes(tmpDir.resolve("t.txt")).creationTime());
	}

	@Test
	@DisplayName("setcrtime on a missing node returns ENOENT")
	@EnabledOnOs(OS.MAC)
	void testSetcrtimeOfMissingNode() {
		Assertions.assertEquals(-enoent, adapter.setcrtime("/missing", new FixedTimeSpec(T)));
	}

	@Test
	@DisplayName("setchgtime and setbkuptime succeed and change nothing")
	void testSetchgtimeAndSetbkuptime() throws IOException {
		Files.createFile(tmpDir.resolve("f.txt"));
		var before = attributes(tmpDir.resolve("f.txt"));

		Assertions.assertEquals(0, adapter.setchgtime("/f.txt", new FixedTimeSpec(T)));
		Assertions.assertEquals(0, adapter.setbkuptime("/f.txt", new FixedTimeSpec(T)));

		var after = attributes(tmpDir.resolve("f.txt"));
		Assertions.assertEquals(before.creationTime(), after.creationTime());
		Assertions.assertEquals(before.lastModifiedTime(), after.lastModifiedTime());
	}

	@Test
	@DisplayName("xtimes setters are supported operations")
	void testSupportedOperations() {
		Assertions.assertTrue(adapter.supportedOperations().containsAll(Set.of(Operation.SET_CRTIME, Operation.SET_CHGTIME, Operation.SET_BKUPTIME)));
	}

	private static BasicFileAttributes attributes(Path node) throws IOException {
		return Files.readAttributes(node, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
	}

	private record FixedTimeSpec(Instant instant) implements TimeSpec {

		@Override
		public boolean isUtimeOmit() {
			return false;
		}

		@Override
		public boolean isUtimeNow() {
			return false;
		}

		@Override
		public Instant get() {
			return instant;
		}

		@Override
		public void set(Instant time) {
			throw new UnsupportedOperationException();
		}
	}

}
