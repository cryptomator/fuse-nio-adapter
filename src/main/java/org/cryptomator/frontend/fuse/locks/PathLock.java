package org.cryptomator.frontend.fuse.locks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Function;

public record PathLock(@Unmodifiable List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Lock lock, Function<List<String>, ReadWriteLock> dataLockSupplier) implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(PathLock.class);

	@FunctionalInterface
	interface Factory {
		@Nullable PathLock lock(List<String> pathComponents,  @Nullable PathLock parent, ReadWriteLock rwLock, Function<List<String>, ReadWriteLock> dataLockSupplier);
	}

	static @NotNull PathLock readLock(List<String> pathComponents,  @Nullable PathLock parent, ReadWriteLock rwLock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.readLock();
		lock.lock();
		LOG.trace("Acquired read path lock for '{}'", pathComponents);
		return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
	}

	static @NotNull PathLock writeLock(List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.writeLock();
		lock.lock();
		LOG.trace("Acquired write path lock for '{}'", pathComponents);
		return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
	}

	static @Nullable PathLock tryWriteLock(List<String> pathComponents,  @Nullable PathLock parent, ReadWriteLock rwLock, Function<List<String>, ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.writeLock();
		if (lock.tryLock()) {
			LOG.trace("Acquired write path lock for '{}'", pathComponents);
			return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
		} else {
			return null;
		}
	}

	public DataLock lockDataForReading() {
		ReadWriteLock dataLock = dataLockSupplier.apply(pathComponents);
		return DataLock.readLock(pathComponents, dataLock);
	}

	public DataLock lockDataForWriting() {
		ReadWriteLock dataLock = dataLockSupplier.apply(pathComponents);
		return DataLock.writeLock(pathComponents, dataLock);
	}

	@Override
	public void close() {
		LOG.trace("Released path lock for '{}'", pathComponents);
		lock.unlock();
		if (parent != null) {
			parent.close();
		}
	}

}
