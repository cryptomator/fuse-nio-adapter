package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

class DataRLockImpl extends DataLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(DataRLockImpl.class);

	private DataRLockImpl(List<String> pathComponents, ReadWriteLock lock) {
		super(pathComponents, lock);
	}

	static DataRLockImpl create(List<String> pathComponents, ReadWriteLock lock) {
		lock.readLock().lock();
		LOG.trace("Acquired read data lock for '{}'", pathComponents);
		return new DataRLockImpl(pathComponents, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released read data lock for '{}'", pathComponents);
		lock.readLock().unlock();
	}

}
