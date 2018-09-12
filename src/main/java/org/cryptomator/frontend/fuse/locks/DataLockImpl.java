package org.cryptomator.frontend.fuse.locks;

import java.util.concurrent.locks.ReadWriteLock;

abstract class DataLockImpl implements DataLock {

	protected final String path;
	protected final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of the LockManager's cache

	protected DataLockImpl(String path, ReadWriteLock lock) {
		this.path = path;
		this.lock = lock;
	}

}
