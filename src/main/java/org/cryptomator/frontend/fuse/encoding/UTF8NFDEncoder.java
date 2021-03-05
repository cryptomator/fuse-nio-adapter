package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

//TODO: for MacOS standard NFD is not enough
public class UTF8NFDEncoder implements BufferEncoder {

	private final static Charset UTF8 = StandardCharsets.UTF_8;

	@Override
	public String getEncodingDescription() {
		return "UTF-8 in NFD";
	}

	@Override
	public ByteBuffer encode(CharSequence s) {
		String normalizedS = Normalizer.normalize(s, Normalizer.Form.NFD);
		return UTF8.encode(normalizedS);
	}
}
