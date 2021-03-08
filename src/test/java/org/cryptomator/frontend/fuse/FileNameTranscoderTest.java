package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public class FileNameTranscoderTest {

	private static final Charset COMPARSION_CS = StandardCharsets.UTF_16;

	@Test
	@DisplayName("Transcoding from a charset into itself without normalization does nothing")
	public void testTranscodingFromCharsetToItselfDoesNothing(){
		var transcodingCS = StandardCharsets.ISO_8859_1;
		var fileNameTranscoder = FileNameTranscoder.transcoder(transcodingCS,transcodingCS);

		String test = "This is a test";
		byte [] before = test.getBytes(COMPARSION_CS);
		byte [] after = fileNameTranscoder.fuseToNio(test).getBytes(COMPARSION_CS);

		Assertions.assertArrayEquals(before, after);
	}

	@Test
	@DisplayName("Normalization is only applied if src normalization != dst normalization")
	public void testNoNormalizationIfSrcAndDstNormalizationIsSame(){
		var transcodingCS = StandardCharsets.UTF_8;
		var normalization = Normalizer.Form.NFD;
		var fileNameTranscoder = FileNameTranscoder.transcoder(transcodingCS,transcodingCS, normalization, normalization);

		String testString = "This is ä \uFA00";
		byte [] before = testString.getBytes(COMPARSION_CS);
		byte [] after = fileNameTranscoder.fuseToNio(testString).getBytes(COMPARSION_CS);

		Assertions.assertArrayEquals(before,after);
	}

	@Test
	@DisplayName("Creating a transcoder with non-null normalization and non-utf encoding fails")
	public void testBuilderWithNormalizationFailsIfNoUTF(){
		var csMock =Mockito.mock(Charset.class);
		Mockito.when(csMock.displayName()).thenReturn("ISO88");
		Assertions.assertThrows(IllegalArgumentException.class,() -> FileNameTranscoder.transcoder(csMock, csMock, Normalizer.Form.NFD,null));
	}
}
