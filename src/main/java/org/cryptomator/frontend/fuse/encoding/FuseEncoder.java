package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;

public interface FuseEncoder {

	String getEncodingDescription();

	ByteBuffer encode(CharSequence s);

}
