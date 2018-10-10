package org.cryptomator.frontend.fuse.locks;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathLockBuilderImpl implements PathLockBuilder {

	private final String path;
	private final Optional<PathLockBuilder> parent;
	private final ReadWriteLock lock;
	private final Function<String, ReadWriteLock> dataLockSupplier;

	PathLockBuilderImpl(String path, Optional<PathLockBuilder> parent, ReadWriteLock lock, Function<String, ReadWriteLock> dataLockSupplier) {
		this.path = path;
		this.parent = parent;
		this.lock = lock;
		this.dataLockSupplier = dataLockSupplier;
	}

	public PathLock forReading() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathRLockImpl.create(path, parentLock, lock, dataLockSupplier);
	}

	public PathLock forWriting() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathWLockImpl.create(path, parentLock, lock, dataLockSupplier);
	}

}
