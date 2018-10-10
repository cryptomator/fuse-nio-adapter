package org.cryptomator.frontend.fuse.locks;

public interface DataLock extends AutoCloseable {

	@Override
	void close();
}
