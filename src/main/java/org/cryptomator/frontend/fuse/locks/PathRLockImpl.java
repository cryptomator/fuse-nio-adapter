package org.cryptomator.frontend.fuse.locks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathRLockImpl extends PathLockImpl {

	private static final Logger LOG = LoggerFactory.getLogger(PathRLockImpl.class);

	private PathRLockImpl(List<String> pathComponents, Optional<PathLock> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		super(pathComponents, parent, lock, dataLockSupplier);
	}

	public static PathLockImpl create(List<String> pathComponents, Optional<PathLock> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		lock.readLock().lock();
		LOG.trace("Acquired read path lock for '{}'", pathComponents);
		return new PathRLockImpl(pathComponents, parent, lock, dataLockSupplier);
	}

	@Override
	public void close() {
		LOG.trace("Released read path lock for '{}'", pathComponents);
		lock.readLock().unlock();
		super.close();
	}
}
