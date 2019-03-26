package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

class DataWLockImpl extends DataLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(DataWLockImpl.class);

	private DataWLockImpl(List<String> pathComponents, ReadWriteLock lock) {
		super(pathComponents, lock);
	}

	static DataWLockImpl create(List<String> pathComponents, ReadWriteLock lock) {
		lock.writeLock().lock();
		LOG.trace("Acquired write data lock for '{}'", pathComponents);
		return new DataWLockImpl(pathComponents, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released write data lock for '{}'", pathComponents);
		lock.writeLock().unlock();
	}

}
