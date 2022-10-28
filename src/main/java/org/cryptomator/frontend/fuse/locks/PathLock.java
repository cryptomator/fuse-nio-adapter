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
import java.util.function.Supplier;

/**
 * A path lock, either for reading (shared) or writing (exclusive).
 *
 * @param pathComponents   The path, split into path components
 * @param parent           The corresponding path lock for the parent path
 * @param rwLock           The read-write-lock. We need to store a strong reference while in use, because LockManager works with weeak references
 * @param lock             Either the {@code rwLock}'s read or its write lock
 * @param dataLockSupplier A supplier for a separate ReadWriteLock (not {@code rwLock}) to be used during {@link #lockDataForReading()} and {@link #lockDataForWriting()}
 */
public record PathLock(@Unmodifiable List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Lock lock, Supplier<ReadWriteLock> dataLockSupplier) implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(PathLock.class);

	@FunctionalInterface
	interface Factory {
		@Nullable PathLock lock(List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Supplier<ReadWriteLock> dataLockSupplier);
	}

	static @NotNull PathLock readLock(List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Supplier<ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.readLock();
		lock.lock();
		LOG.trace("Acquired read path lock for '{}'", pathComponents);
		return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
	}

	static @NotNull PathLock writeLock(List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Supplier<ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.writeLock();
		lock.lock();
		LOG.trace("Acquired write path lock for '{}'", pathComponents);
		return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
	}

	static @Nullable PathLock tryWriteLock(List<String> pathComponents, @Nullable PathLock parent, ReadWriteLock rwLock, Supplier<ReadWriteLock> dataLockSupplier) {
		var lock = rwLock.writeLock();
		if (lock.tryLock()) {
			LOG.trace("Acquired write path lock for '{}'", pathComponents);
			return new PathLock(pathComponents, parent, rwLock, lock, dataLockSupplier);
		} else {
			return null;
		}
	}

	public DataLock lockDataForReading() {
		ReadWriteLock dataLock = dataLockSupplier.get();
		return DataLock.readLock(pathComponents, dataLock);
	}

	public DataLock lockDataForWriting() {
		ReadWriteLock dataLock = dataLockSupplier.get();
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
