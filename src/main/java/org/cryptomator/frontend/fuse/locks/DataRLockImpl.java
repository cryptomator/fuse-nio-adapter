package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReadWriteLock;

class DataRLockImpl extends DataLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(DataRLockImpl.class);

	private DataRLockImpl(String path, ReadWriteLock lock) {
		super(path, lock);
	}

	static DataRLockImpl create(String path, ReadWriteLock lock) {
		lock.readLock().lock();
		LOG.trace("Acquired read data lock for '{}'", path);
		return new DataRLockImpl(path, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released read data lock for '{}'", path);
		lock.readLock().unlock();
	}

}
