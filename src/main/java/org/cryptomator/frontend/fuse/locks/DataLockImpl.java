package org.cryptomator.frontend.fuse.locks;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

abstract class DataLockImpl implements DataLock {

	protected final List<String> pathComponents;
	protected final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of the LockManager's cache

	protected DataLockImpl(List<String> pathComponents, ReadWriteLock lock) {
		this.pathComponents = pathComponents;
		this.lock = lock;
	}

}
