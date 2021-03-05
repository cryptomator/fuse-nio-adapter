package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;

public class DefaultEncoder implements BufferEncoder {

	@Override
	public String getEncodingDescription() {
		return "Default Charset encoding";
	}

	@Override
	public ByteBuffer encode(CharSequence s) {
		return ByteBuffer.wrap(s.toString().getBytes());
	}
}
