package org.cryptomator.frontend.fuse.locks;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a path-based locking mechanism as described by
 * <a href="https://people.eecs.berkeley.edu/~kubitron/courses/cs262a-F14/projects/reports/project6_report.pdf">Ritik Malhotra here</a>.
 *
 * <p>
 * Usage Example 1:
 * <pre>
 *     try (PathLock pathLock = lockManager.lockForReading("/foo/bar/baz"); // path is not manipulated, thus read-locking
 *          DataLock dataLock = pathLock.lockDataForWriting()) { // content is manipulated, thus write-locking
 *          // write to file
 *     }
 * </pre>
 *
 * <p>
 * Usage Example 2:
 * <pre>
 *     try (PathLock srcPathLock = lockManager.lockForReading("/foo/bar/original");
 *          DataLock srcDataLock = srcPathLock.lockDataForReading(); // content will only be read, thus read-locking
 *          PathLock dstPathLock = lockManager.lockForWriting("/foo/bar/copy"); // file will be created, thus write-locking
 *          DataLock dstDataLock = srcPathLock.lockDataForWriting()) {
 *          // copy from /foo/bar/original to /foo/bar/copy
 *     }
 * </pre>
 */
public class LockManager {

	private static final Logger LOG = LoggerFactory.getLogger(LockManager.class);

	private final LoadingCache<List<String>, ReadWriteLock> pathLocks;
	private final LoadingCache<List<String>, ReadWriteLock> dataLocks;

	public LockManager() {
		Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder().weakValues();
		if (LOG.isDebugEnabled()) {
			cacheBuilder.removalListener(this::removedReadWriteLock);
		}
		this.pathLocks = cacheBuilder.build(this::createReadWriteLock);
		this.dataLocks = cacheBuilder.build(this::createReadWriteLock);
	}

	public PathLock tryLockForWriting(String path) throws AlreadyLockedException {
		var pathComponents = FilePaths.toComponents(path);
		var lock = lock(pathComponents, PathLock::tryWriteLock);
		if (lock != null) {
			return lock;
		} else {
			throw new AlreadyLockedException();
		}
	}

	public PathLock lockForReading(String path) {
		var pathComponents = FilePaths.toComponents(path);
		return lock(pathComponents, PathLock::readLock);
	}

	public PathLock lockForWriting(String path) {
		var pathComponents = FilePaths.toComponents(path);
		return lock(pathComponents, PathLock::writeLock);
	}

	private @Nullable PathLock lock(List<String> pathComponents, PathLock.Factory factory) {
		if (pathComponents.isEmpty()) {
			return null;
		}

		List<String> parentPathComponents = FilePaths.parentPathComponents(pathComponents);
		PathLock parentLock = lock(parentPathComponents, PathLock::readLock);
		ReadWriteLock rwLock = pathLocks.get(pathComponents);
		return factory.lock(pathComponents, parentLock, rwLock, dataLocks::get);
	}

	private ReadWriteLock createReadWriteLock(List<String> key) {
		LOG.trace("Creating ReadWriteLock for {}", key);
		return new ReentrantReadWriteLock();
	}

	private void removedReadWriteLock(List<String> key, ReentrantReadWriteLock value, RemovalCause removalCause) {
		LOG.trace("Deleting ReadWriteLock for {}", key);
	}

	/*
	 * Support functions:
	 */

	@VisibleForTesting
	boolean isPathLocked(String path) {
		ReadWriteLock lock = pathLocks.getIfPresent(FilePaths.toComponents(path));
		if (lock == null) {
			return false;
		} else if (lock.writeLock().tryLock()) {
			try {
				return false; // if we're able to get an exclusive lock, this path is not locked.
			} finally {
				lock.writeLock().unlock();
			}
		} else {
			return true;
		}
	}

}
