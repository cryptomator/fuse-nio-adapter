package org.cryptomator.frontend.fuse.locks;

public interface PathLock extends AutoCloseable {

	DataLock lockDataForReading();

	DataLock lockDataForWriting();

	@Override
	void close();

}
