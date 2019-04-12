package org.cryptomator.frontend.fuse.locks;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

abstract class PathLockImpl implements PathLock {

	protected final List<String> pathComponents;
	protected final Optional<PathLock> parent;
	protected final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of the LockManager's cache
	private final Function<List<String>, ReadWriteLock> dataLockSupplier;

	protected PathLockImpl(List<String> pathComponents, Optional<PathLock> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		this.pathComponents = pathComponents;
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
		ReadWriteLock dataLock = dataLockSupplier.apply(pathComponents);
		return DataRLockImpl.create(pathComponents, dataLock);
	}

	@Override
	public DataLock lockDataForWriting() {
		ReadWriteLock dataLock = dataLockSupplier.apply(pathComponents);
		return DataWLockImpl.create(pathComponents, dataLock);
	}

}
