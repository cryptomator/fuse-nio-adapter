package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReadWriteLock;

class DataWLockImpl extends DataLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(DataWLockImpl.class);

	private DataWLockImpl(String path, ReadWriteLock lock) {
		super(path, lock);
	}

	static DataWLockImpl create(String path, ReadWriteLock lock) {
		lock.writeLock().lock();
		LOG.trace("Acquired write data lock for '{}'", path);
		return new DataWLockImpl(path, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released write data lock for '{}'", path);
		lock.writeLock().unlock();
	}

}
