package org.cryptomator.frontend.fuse.locks;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

class PathLockBuilderImpl implements PathLockBuilder {

	private final String path;
	private final Optional<PathLockBuilder> parent;
	private final ReadWriteLock lock;

	PathLockBuilderImpl(String path, Optional<PathLockBuilder> parent, ReadWriteLock lock) {
		this.path = path;
		this.parent = parent;
		this.lock = lock;
	}

	public PathLock forReading() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathRLockImpl.create(path, parentLock, lock);
	}

	public PathLock forWriting() {
		Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
		return PathWLockImpl.create(path, parentLock, lock);
	}

}
