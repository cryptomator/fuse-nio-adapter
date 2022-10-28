package org.cryptomator.frontend.fuse.locks;

import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public record DataLock(@Unmodifiable List<String> pathComponents, ReadWriteLock rwLock, Lock lock) implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(DataLock.class);

	static DataLock readLock(List<String> pathComponents, ReadWriteLock rwLock) {
		var lock = rwLock.readLock();
		lock.lock();
		LOG.trace("Acquired read data lock for '{}'", pathComponents);
		return new DataLock(pathComponents, rwLock, lock);
	}

	static DataLock writeLock(List<String> pathComponents, ReadWriteLock rwLock) {
		var lock = rwLock.writeLock();
		lock.lock();
		LOG.trace("Acquired write data lock for '{}'", pathComponents);
		return new DataLock(pathComponents, rwLock, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released data lock for '{}'", pathComponents);
		lock.unlock();
	}

}
