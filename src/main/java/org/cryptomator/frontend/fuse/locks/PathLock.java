package org.cryptomator.frontend.fuse.locks;

public interface PathLock extends AutoCloseable {

	default DataLock lockDataForReading() {
		return null;
	}

	default DataLock lockDataForWriting() {
		return null;
	}

	@Override
	void close();

}
