package org.cryptomator.frontend.fuse.locks;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

abstract class PathLockImpl implements PathLock {

	protected final String path;
	protected final Optional<PathLock> parent;
	protected final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of the LockManager's cache

	protected PathLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock) {
		this.path = path;
		this.parent = parent;
		this.lock = lock;
	}

	@Override
	public void close() {
		parent.ifPresent(PathLock::close);
	}

	@Override
	public DataLock lockDataForReading() {
		return null;
	}

	@Override
	public DataLock lockDataForWriting() {
		return null;
	}

}
