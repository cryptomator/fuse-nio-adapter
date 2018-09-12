package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathWLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathWLockImpl.class);

	private PathWLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		super(path, parent, lock, dataLockSupplier);
	}

	static PathLockImpl create(String path, Optional<PathLock> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		lock.writeLock().lock();
		LOG.trace("Acquired write path lock for '{}'", path);
		return new PathWLockImpl(path, parent, lock, dataLockSupplier);
	}

	@Override
	public void close() {
		LOG.trace("Released write path lock for '{}'", path);
		lock.writeLock().unlock();
		super.close();
	}
}
