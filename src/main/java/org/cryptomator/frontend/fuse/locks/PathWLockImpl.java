package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathWLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathWLockImpl.class);

	private PathWLockImpl(List<String> pathComponents, Optional<PathLock> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		super(pathComponents, parent, lock, dataLockSupplier);
	}

	public static PathLockImpl create(List<String> pathComponents, Optional<PathLock> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		lock.writeLock().lock();
		LOG.trace("Acquired write path lock for '{}'", pathComponents);
		return new PathWLockImpl(pathComponents, parent, lock, dataLockSupplier);
	}

	@Override
	public void close() {
		LOG.trace("Released write path lock for '{}'", pathComponents);
		lock.writeLock().unlock();
		super.close();
	}
}
