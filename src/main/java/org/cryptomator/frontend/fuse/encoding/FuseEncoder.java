package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public interface FuseEncoder {

	String getEncodingDescription();

	Charset getTargetCharset();

	ByteBuffer encode(CharSequence s);

}
