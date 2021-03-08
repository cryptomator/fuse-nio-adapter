package org.cryptomator.frontend.fuse;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

public class FileNameTranscoder {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	private static final Normalizer.Form DEFAULT_NORMALIZATION = Normalizer.Form.NFC;
	private static final Pattern UTF_MATCH = Pattern.compile("(utf|UTF)-?(8|((16|32)(BE|LE|be|le)?))");

	private final Charset fuseCharset;
	private final Charset nioCharset;
	private final Optional<Normalizer.Form> fuseNormalization;
	private final Optional<Normalizer.Form> nioNormalization;

	private FileNameTranscoder(Charset fuseCharset, Charset nioCharset, Normalizer.Form fuseNormalization, Normalizer.Form nioNormalization) {
		this.fuseCharset = Preconditions.checkNotNull(fuseCharset);
		this.nioCharset = Preconditions.checkNotNull(nioCharset);
		this.fuseNormalization = Optional.ofNullable(fuseNormalization);
		this.nioNormalization = Optional.ofNullable(nioNormalization);
	}

	/**
	 * Transcodes the given NIO file name to FUSE representation.
	 *
	 * @param nioFileName A file name encoded with the charset used in NIO
	 * @return The file name encoded with the charset used by FUSE
	 */
	public String nioToFuse(String nioFileName) {
		return transcode(nioFileName, nioCharset, fuseCharset, nioNormalization, fuseNormalization);
	}

	/**
	 * Transcodes the given FUSE file name to NIO representation.
	 *
	 * @param fuseFileName A file name encoded with the charset used in FUSE
	 * @return The file name encoded with the charset used by NIO
	 */
	public String fuseToNio(String fuseFileName) {
		return transcode(fuseFileName, fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	/**
	 * Interprets the given string as FUSE character set encoded and returns the original byte sequence.
	 * @param fuseFileName string from the fuse layer
	 * @return A byte sequence with the original encoding of the input
	 */
	public ByteBuffer interpretAsFuseString(String fuseFileName) {
		return fuseCharset.encode(fuseFileName);
	}

	/**
	 * Interprets the given string as NIO character set encoded and returns the original byte sequence.
	 * @param nioFileName string from the nio layer
	 * @return A byte sequence with the original encoding of the input
	 */
	public ByteBuffer interpretAsNioString(String nioFileName) {
		return nioCharset.encode(nioFileName);
	}

	private String transcode(String original, Charset srcCharset, Charset dstCharset, Optional<Normalizer.Form> srcNormalization, Optional<Normalizer.Form> dstNormalization) {
		String result = original;
		if (!srcCharset.equals(dstCharset)) {
			result = dstCharset.decode(srcCharset.encode(result)).toString();
		}
		if (dstNormalization.isPresent()) {
			if ((srcNormalization.isPresent() && !srcNormalization.get().equals(dstNormalization.get())) || srcNormalization.isEmpty()) {
				Normalizer.normalize(result, dstNormalization.get());
			}
		}
		return result;
	}

	/* Builder */
	public static FileNameTranscoder transcoder(Charset fuseCharset, Charset nioCharset, Normalizer.Form fuseNormalization, Normalizer.Form nioNormalization) {
		if ((fuseNormalization != null && !UTF_MATCH.matcher(fuseCharset.displayName()).matches()) //
				|| (nioNormalization != null && !UTF_MATCH.matcher(nioCharset.displayName()).matches())) {
			throw new IllegalArgumentException("Normalization only applicable to utf encodings");
		}
		return new FileNameTranscoder(fuseCharset, nioCharset, fuseNormalization, nioNormalization);
	}

	public static FileNameTranscoder transcoder(Charset fuseCharset, Charset nioCharset) {
		return new FileNameTranscoder(fuseCharset, nioCharset, null, null);
	}
	//TODO: maybe let the FUSE charset be Charset.defaultCharset() ?
	public static FileNameTranscoder transcoder() {
		return new FileNameTranscoder(DEFAULT_CHARSET, DEFAULT_CHARSET, DEFAULT_NORMALIZATION, DEFAULT_NORMALIZATION);
	}
}
