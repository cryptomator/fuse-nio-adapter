package org.cryptomator.frontend.fuse;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/**
 * Class to transcode filenames and path components from one encoding to another.
 * <p>
 * Instances created with {@link FileNameTranscoder#transcoder()} default to fuse and nio UTF-8 encoding with NFC normalization. To change encoding and normalization, use the supplied "withXXX()" methods. If an encoding is not part of the UTF famlily, the normalization is ignored.
 */
public class FileNameTranscoder {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	private static final Normalizer.Form DEFAULT_NORMALIZATION = Normalizer.Form.NFC;

	private final Charset fuseCharset;
	private final Charset nioCharset;
	private final Normalizer.Form fuseNormalization;
	private final Normalizer.Form nioNormalization;
	private final boolean fuseCharsetIsUnicode;
	private final boolean nioCharsetIsUnicode;

	FileNameTranscoder(Charset fuseCharset, Charset nioCharset, Normalizer.Form fuseNormalization, Normalizer.Form nioNormalization) {
		this.fuseCharset = Objects.requireNonNull(fuseCharset);
		this.nioCharset = Objects.requireNonNull(nioCharset);
		this.fuseNormalization = Objects.requireNonNull(fuseNormalization);
		this.nioNormalization = Objects.requireNonNull(nioNormalization);
		this.fuseCharsetIsUnicode = fuseCharset.name().startsWith("UTF");
		this.nioCharsetIsUnicode = nioCharset.name().startsWith("UTF");
	}

	/**
	 * Transcodes the given NIO file name to FUSE representation.
	 *
	 * @param nioFileName A file name encoded with the charset used in NIO
	 * @return The file name encoded with the charset used by FUSE
	 */
	public String nioToFuse(String nioFileName) {
		return transcode(nioFileName, nioCharset, fuseCharset, nioNormalization, fuseNormalization, fuseCharsetIsUnicode);
	}

	/**
	 * Transcodes the given FUSE file name to NIO representation.
	 *
	 * @param fuseFileName A file name encoded with the charset used in FUSE
	 * @return The file name encoded with the charset used by NIO
	 */
	public String fuseToNio(String fuseFileName) {
		return transcode(fuseFileName, fuseCharset, nioCharset, fuseNormalization, nioNormalization, nioCharsetIsUnicode);
	}

	/**
	 * Interprets the given string as FUSE character set encoded and returns the original byte sequence.
	 *
	 * @param fuseFileName string from the fuse layer
	 * @return A byte sequence with the original encoding of the input
	 */
	public ByteBuffer interpretAsFuseString(String fuseFileName) {
		return fuseCharset.encode(fuseFileName);
	}

	/**
	 * Interprets the given string as NIO character set encoded and returns the original byte sequence.
	 *
	 * @param nioFileName string from the nio layer
	 * @return A byte sequence with the original encoding of the input
	 */
	public ByteBuffer interpretAsNioString(String nioFileName) {
		return nioCharset.encode(nioFileName);
	}

	private String transcode(String original, Charset srcCharset, Charset dstCharset, Normalizer.Form srcNormalization, Normalizer.Form dstNormalization, boolean applyNormalization) {
		String result = original;
		if (!srcCharset.equals(dstCharset)) {
			result = dstCharset.decode(srcCharset.encode(result)).toString();
		}
		if (applyNormalization && srcNormalization != dstNormalization) {
			result = Normalizer.normalize(result, dstNormalization);
		}
		return result;
	}

	/* Builder/Wither */
	public FileNameTranscoder withFuseCharset(Charset fuseCharset) {
		return new FileNameTranscoder(fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	public FileNameTranscoder withNioCharset(Charset nioCharset) {
		return new FileNameTranscoder(fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	public FileNameTranscoder withFuseNormalization(Normalizer.Form fuseNormalization) {
		return new FileNameTranscoder(fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	public FileNameTranscoder withNioNormalization(Normalizer.Form nioNormalization) {
		return new FileNameTranscoder(fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	public static FileNameTranscoder transcoder() {
		return new FileNameTranscoder(DEFAULT_CHARSET, DEFAULT_CHARSET, DEFAULT_NORMALIZATION, DEFAULT_NORMALIZATION);
	}
}
