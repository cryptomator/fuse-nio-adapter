package org.cryptomator.frontend.fuse.encoding;

import java.nio.ByteBuffer;

public interface BufferEncoder {

	String getEncodingDescription();

	ByteBuffer encode(CharSequence s);

}
