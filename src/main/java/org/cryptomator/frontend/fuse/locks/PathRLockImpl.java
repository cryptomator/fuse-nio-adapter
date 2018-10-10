package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathRLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathRLockImpl.class);

	private PathRLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		super(path, parent, lock, dataLockSupplier);
	}

	static PathLockImpl create(String path, Optional<PathLock> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		lock.readLock().lock();
		LOG.trace("Acquired read path lock for '{}'", path);
		return new PathRLockImpl(path, parent, lock, dataLockSupplier);
	}

	@Override
	public void close() {
		LOG.trace("Released read path lock for '{}'", path);
		lock.readLock().unlock();
		super.close();
	}
}
