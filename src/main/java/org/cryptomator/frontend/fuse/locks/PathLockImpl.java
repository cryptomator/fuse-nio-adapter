package org.cryptomator.frontend.fuse.locks;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

abstract class PathLockImpl implements PathLock {

	protected final String path;
	protected final Optional<PathLock> parent;
	protected final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of the LockManager's cache
	private final Function<String, ReadWriteLock> dataLockSupplier;

	protected PathLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		this.path = path;
		this.parent = parent;
		this.lock = lock;
		this.dataLockSupplier = dataLockSupplier;
	}

	@Override
	public void close() {
		parent.ifPresent(PathLock::close);
	}

	@Override
	public DataLock lockDataForReading() {
		ReadWriteLock dataLock = dataLockSupplier.apply(path);
		return DataRLockImpl.create(path, dataLock);
	}

	@Override
	public DataLock lockDataForWriting() {
		ReadWriteLock dataLock = dataLockSupplier.apply(path);
		return DataWLockImpl.create(path, dataLock);
	}

}
