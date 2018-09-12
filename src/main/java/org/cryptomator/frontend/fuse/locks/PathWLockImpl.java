package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

class PathWLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathWLockImpl.class);

	private PathWLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock) {
		super(path, parent, lock);
	}

	static PathLockImpl create(String path, Optional<PathLock> parent, ReadWriteLock lock) {
		lock.writeLock().lock();
		LOG.trace("Acquired write lock for '{}'", path);
		return new PathWLockImpl(path, parent, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released write lock for '{}'", path);
		lock.writeLock().unlock();
		super.close();
	}
}
