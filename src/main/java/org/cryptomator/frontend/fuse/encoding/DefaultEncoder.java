package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class DefaultEncoder implements FuseEncoder {

	@Override
	public String getEncodingDescription() {
		return "Default Charset encoding";
	}

	@Override
	public Charset getTargetCharset() {
		return Charset.defaultCharset();
	}

	@Override
	public ByteBuffer encode(CharSequence s) {
		return ByteBuffer.wrap(s.toString().getBytes());
	}
}
