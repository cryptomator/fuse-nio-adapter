package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelUtilTest {

	private static final String FILE_NAME = "foo.bar";
	private static final int CURRENT_SIZE = 3000;

	@ParameterizedTest
	@ValueSource(longs = {15L, 2099L, 3000L})
	public void testTruncation(long wantedSize, @TempDir Path path) throws IOException {
		Assumptions.assumeTrue(wantedSize <= CURRENT_SIZE);
		var tempFile = path.resolve(FILE_NAME);
		Files.write(tempFile, new byte[CURRENT_SIZE], StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
		try (var ch = Mockito.spy(FileChannel.open(tempFile, StandardOpenOption.WRITE))) {

			FileChannelUtil.truncateOrExpand(ch, wantedSize);

			Mockito.verify(ch).truncate(wantedSize);
			Mockito.verify(ch, Mockito.never()).write(Mockito.any(ByteBuffer.class));
			Mockito.verify(ch, Mockito.never()).position(Mockito.anyLong());
			Assertions.assertEquals(wantedSize, ch.size());
		}
		Assertions.assertEquals(wantedSize, Files.size(tempFile));
	}

	@ParameterizedTest
	@ValueSource(longs = {3001L, 30_005L})
	public void testExtension(long wantedSize, @TempDir Path path) throws IOException {
		Assumptions.assumeTrue(wantedSize > CURRENT_SIZE);
		var tempFile = path.resolve(FILE_NAME);
		Files.write(tempFile, new byte[CURRENT_SIZE], StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
		try (var ch = Mockito.spy(FileChannel.open(tempFile, StandardOpenOption.WRITE))) {

			FileChannelUtil.truncateOrExpand(ch, wantedSize);

			Mockito.verify(ch).position(CURRENT_SIZE);
			Mockito.verify(ch, Mockito.atLeastOnce()).write(Mockito.any(ByteBuffer.class));
			Mockito.verify(ch, Mockito.never()).truncate(Mockito.anyLong());
			Assertions.assertEquals(wantedSize, ch.size());
		}
		Assertions.assertEquals(wantedSize, Files.size(tempFile));
	}
}