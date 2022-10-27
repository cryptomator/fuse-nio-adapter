package org.cryptomator.frontend.fuse;

import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Fuse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class AccessPatternIntegrationTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
	}

	private FuseNioAdapter adapter;

	@BeforeEach
	void setup(@TempDir Path tmpDir) {
		var builder = Fuse.builder();
		adapter = ReadWriteAdapter.create(builder.errno(), tmpDir, FuseNioAdapter.DEFAULT_MAX_FILENAMELENGTH, FileNameTranscoder.transcoder());
	}

	@Test
	@DisplayName("simulate TextEdit.app's access pattern during save")
	void testAppleAutosaveAccessPattern() {
		// echo "asd" > foo.txt
		FileInfo fi1 = Mockito.spy(new SimpleFileInfo());
		adapter.create("/foo.txt", 0644, fi1);
		adapter.write("/foo.txt", US_ASCII.encode("asd"), 3, 0, fi1);

		// mkdir foo.txt-temp3000
		adapter.mkdir("foo.txt-temp3000", 0755);

		// echo "asdasd" > foo.txt-temp3000/foo.txt
		FileInfo fi2 = Mockito.spy(new SimpleFileInfo());
		adapter.create("/foo.txt-temp3000/foo.txt", 0644, fi2);
		adapter.write("/foo.txt-temp3000/foo.txt", US_ASCII.encode("asdasd"), 6, 0, fi2);

		// mv foo.txt foo.txt-temp3001
		adapter.rename("/foo.txt", "/foo.txt-temp3001", 0);

		// mv foo.txt-temp3000/foo.txt foo.txt
		adapter.rename("/foo.txt-temp3000/foo.txt", "/foo.txt", 0);
		adapter.release("/foo.txt-temp3000/foo.txt", fi2);

		// rm -r foo.txt-temp3000
		adapter.rmdir("/foo.txt-temp3000");

		// rm foo.txt-temp3001
		adapter.release("/foo.txt", fi1);
		adapter.unlink("/foo.txt-temp3001");

		// cat foo.txt == "asdasd"
		ByteBuffer buf = ByteBuffer.allocate(7);
		FileInfo fi3 = Mockito.spy(new SimpleFileInfo());
		adapter.open("/foo.txt", fi3);
		int numRead = adapter.read("/foo.txt", buf, 7, 0, fi3);
		adapter.release("/foo.txt", fi3);
		Assertions.assertEquals(6, numRead);
		Assertions.assertArrayEquals("asdasd".getBytes(US_ASCII), Arrays.copyOf(buf.array(), numRead));
	}

	@Test
	@DisplayName("create, move and delete symlinks")
	@DisabledOnOs(OS.WINDOWS)
		// Symlinks require either admin privileges or enabled developer mode on windows
	void testCreateMoveAndDeleteSymlinks() {
		// touch foo.txt
		FileInfo fi1 = Mockito.mock(FileInfo.class);
		adapter.create("/foo.txt", 0644, fi1);

		// ln -s foo.txt bar.txt
		adapter.symlink("foo.txt", "/bar.txt");
		assertSymlinkTargetExists("/bar.txt", false);

		// mkdir test
		adapter.mkdir("test", 0755);

		// ln -s test test2
		adapter.symlink("test", "/test2");
		assertSymlinkTargetExists("/test2", true);

		// ln -sr ../foo.txt test/baz
		adapter.symlink("../foo.txt", "test/baz.txt");
		assertSymlinkTargetExists("/bar.txt", false);

		// move both to subdir
		adapter.rename("/foo.txt", "/test/foo.txt", 0);
		adapter.rename("/bar.txt", "/test/bar.txt", 0);
		assertSymlinkTargetExists("/test/bar.txt", false);

		// delete all
		adapter.unlink("/test2");
		adapter.unlink("/test/foo.txt");
		adapter.unlink("/test/bar.txt");
		adapter.unlink("/test/baz.txt");
		adapter.rmdir("/test");
	}

	private void assertSymlinkTargetExists(String symlink, boolean targetIsDirectory) {
		FileInfo fi = Mockito.mock(FileInfo.class);
		int returnCode = targetIsDirectory ? adapter.opendir(symlink, fi) : adapter.open(symlink, fi);
		if (returnCode == 0) {
			int err = targetIsDirectory ? adapter.releasedir(symlink, fi) : adapter.release(symlink, fi);
		}
		Assertions.assertEquals(0, returnCode);
	}

	private static class SimpleFileInfo implements FileInfo {

		private long fh;
		@Override
		public void setFh(long fh) {
			this.fh = fh;
		}

		@Override
		public long getFh() {
			return fh;
		}

		@Override
		public int getFlags() {
			return 0;
		}

		@Override
		public Set<StandardOpenOption> getOpenFlags() {
			return Set.of();
		}

		@Override
		public long getLockOwner() {
			return 0;
		}
	}

}
