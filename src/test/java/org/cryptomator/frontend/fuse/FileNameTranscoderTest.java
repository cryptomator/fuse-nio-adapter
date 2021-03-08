package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.text.Normalizer.Form.NFC;
import static java.text.Normalizer.Form.NFD;

public class FileNameTranscoderTest {

	private static final Charset COMPARSION_CS = StandardCharsets.UTF_16;
	private MockedStatic<Normalizer> normalizerClass;

	@BeforeEach
	public void setup() {
		normalizerClass = Mockito.mockStatic(Normalizer.class);
		normalizerClass.when(() -> Normalizer.normalize(Mockito.any(), Mockito.any())).thenCallRealMethod();
	}

	@AfterEach
	public void tearDown() {
		normalizerClass.close();
	}

	@Nested
	@DisplayName("UTF-8/NFC <-> UTF-8/NFC")
	public class NoopTranscoder {

		private FileNameTranscoder transcoder;

		@BeforeEach
		public void setup() {
			this.transcoder = new FileNameTranscoder(UTF_8, UTF_8, NFC, NFC);
		}

		@ParameterizedTest
		@DisplayName("fuseToNio()")
		@ValueSource(strings = {"", "This is a test", "äöü", "🙂🐱"})
		public void testNoopTranscodeFuseToNio(String str) {
			String result = transcoder.fuseToNio(str);

			normalizerClass.verifyNoInteractions();
			Assertions.assertArrayEquals(str.getBytes(COMPARSION_CS), result.getBytes(COMPARSION_CS));
		}

		@ParameterizedTest
		@DisplayName("nioToFuse()")
		@ValueSource(strings = {"", "This is a test", "äöü", "🙂🐱"})
		public void testNoopTranscodeNioToFuse(String str) {
			String result = transcoder.nioToFuse(str);

			normalizerClass.verifyNoInteractions();
			Assertions.assertArrayEquals(str.getBytes(COMPARSION_CS), result.getBytes(COMPARSION_CS));
		}

	}

	@Nested
	@DisplayName("UTF-8/NFC <-> UTF-8/NFD")
	public class TransNormalizer {

		private FileNameTranscoder transcoder;

		@BeforeEach
		public void setup() {
			this.transcoder = new FileNameTranscoder(UTF_8, UTF_8, NFD, NFC);
		}

		@ParameterizedTest
		@DisplayName("fuseToNio()")
		@ValueSource(strings = {"", "This is a test", "äöü", "🙂🐱"})
		public void testNoopTranscodeFuseToNio(String str) {
			String result = transcoder.fuseToNio(str);

			normalizerClass.verify(() -> Normalizer.normalize(str, NFC));
			Assertions.assertEquals(Normalizer.normalize(str, NFC), Normalizer.normalize(result, NFC));
		}

		@ParameterizedTest
		@DisplayName("nioToFuse()")
		@ValueSource(strings = {"", "This is a test", "äöü", "🙂🐱"})
		public void testNoopTranscodeNioToFuse(String str) {
			String result = transcoder.nioToFuse(str);

			normalizerClass.verify(() -> Normalizer.normalize(str, NFD));
			Assertions.assertEquals(Normalizer.normalize(str, NFC), Normalizer.normalize(result, NFC));
		}

	}

	@Nested
	@DisplayName("UTF-8 <-> ISO-8859-1")
	public class ReEncoder {

		private FileNameTranscoder transcoder;

		@BeforeEach
		public void setup() {
			this.transcoder = new FileNameTranscoder(ISO_8859_1, UTF_8, NFC, NFC);
		}

		@ParameterizedTest
		@DisplayName("toFuse(str) != str")
		@ValueSource(strings = {"äöü", "🙂🐱"})
		public void testTranscodeToLatin(String str) {
			Assumptions.assumeTrue(str.chars().anyMatch(c -> c > 0x7F), "str does not contain multi-byte unicode character");

			String fuseStr = transcoder.nioToFuse(str);

			normalizerClass.verifyNoInteractions();
			Assertions.assertNotEquals(str, fuseStr);
		}

		@ParameterizedTest
		@DisplayName("toNio(toFuse(str)) == str")
		@ValueSource(strings = {"", "This is a test", "äöü", "🙂🐱"})
		public void testLosslessBackAndForth(String str) {
			String result = transcoder.fuseToNio(transcoder.nioToFuse(str));

			normalizerClass.verifyNoInteractions();
			Assertions.assertEquals(str, result);
		}

	}

}
