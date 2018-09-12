package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

class PathRLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathRLockImpl.class);

	private PathRLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock) {
		super(path, parent, lock);
	}

	static PathLockImpl create(String path, Optional<PathLock> parent, ReadWriteLock lock) {
		lock.readLock().lock();
		LOG.trace("Acquired read lock for '{}'", path);
		return new PathRLockImpl(path, parent, lock);
	}

	@Override
	public void close() {
		LOG.trace("Released read lock for '{}'", path);
		lock.readLock().unlock();
		super.close();
	}
}
