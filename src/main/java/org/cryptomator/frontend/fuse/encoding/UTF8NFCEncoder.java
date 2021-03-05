package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public class UTF8NFCEncoder implements FuseEncoder {

	private final static Charset UTF8 = StandardCharsets.UTF_8;

	@Override
	public String getEncodingDescription() {
		return "UTF-8 in NFC";
	}

	@Override
	public ByteBuffer encode(CharSequence s) {
		String nfcS = Normalizer.normalize(s, Normalizer.Form.NFC);
		return UTF8.encode(nfcS);
	}
}
