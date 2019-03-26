package org.cryptomator.frontend.fuse.locks;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

class PathLockBuilderImpl implements PathLockBuilder {

	private final List<String> pathComponents;
	private final Optional<PathLockBuilder> parent;
	private final ReadWriteLock lock;
	private final Function<List<String>, ReadWriteLock> dataLockSupplier;

	PathLockBuilderImpl(List<String> pathComponents, Optional<PathLockBuilder> parent, ReadWriteLock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		this.pathComponents = pathComponents;
		this.parent = parent;
		this.lock = lock;
		this.dataLockSupplier = dataLockSupplier;
	}

	public PathLock forReading() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathRLockImpl.create(pathComponents, parentLock, lock, dataLockSupplier);
	}

	public PathLock forWriting() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathWLockImpl.create(pathComponents, parentLock, lock, dataLockSupplier);
	}

}
