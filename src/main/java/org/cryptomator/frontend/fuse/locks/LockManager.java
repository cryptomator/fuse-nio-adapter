package org.cryptomator.frontend.fuse.locks;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import org.cryptomator.frontend.fuse.PerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a path-based locking mechanism as described by
 * <a href="https://people.eecs.berkeley.edu/~kubitron/courses/cs262a-F14/projects/reports/project6_report.pdf">Ritik Malhotra here</a>.
 *
 * <p>
 * Usage Example 1:
 * <pre>
 *     try (PathLock pathLock = lockManager.createPathLock("/foo/bar/baz").forReading(); // path is not manipulated, thus read-locking
 *          DataLock dataLock = pathLock.lockDataForWriting()) { // content is manipulated, thus write-locking
 *          // write to file
 *     }
 * </pre>
 *
 * <p>
 * Usage Example 2:
 * <pre>
 *     try (PathLock srcPathLock = lockManager.createPathLock("/foo/bar/original").forReading();
 *          DataLock srcDataLock = srcPathLock.lockDataForReading(); // content will only be read, thus read-locking
 *          PathLock dstPathLock = lockManager.createPathLock("/foo/bar/copy").forWriting(); // file will be created, thus write-locking
 *          DataLock dstDataLock = srcPathLock.lockDataForWriting()) {
 *          // copy from /foo/bar/original to /foo/bar/copy
 *     }
 * </pre>
 */
@PerAdapter
public class LockManager {

	private static final Logger LOG = LoggerFactory.getLogger(LockManager.class);

	private final LoadingCache<List<String>, ReadWriteLock> pathLocks;
	private final LoadingCache<List<String>, ReadWriteLock> dataLocks;

	@Inject
	public LockManager() {
		CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().weakValues();
		if (LOG.isDebugEnabled()) {
			cacheBuilder.removalListener(this::onLockRemoval);
		}
		this.pathLocks = cacheBuilder.build(new LockLoader());
		this.dataLocks = cacheBuilder.build(new LockLoader());
	}

	public PathLockBuilder createPathLock(String path) {
		List<String> pathComponents = FilePaths.toComponents(path);
		assert !pathComponents.isEmpty();
		return createPathLock(pathComponents);
	}

	private PathLockBuilder createPathLock(List<String> pathComponents) {
		if (pathComponents.isEmpty()) {
			return null;
		}

		List<String> parentPathComponents = FilePaths.parentPathComponents(pathComponents);
		PathLockBuilder parentLockBuilder = createPathLock(parentPathComponents);
		ReadWriteLock lock = pathLocks.getUnchecked(pathComponents);
		return new PathLockBuilderImpl(pathComponents, Optional.ofNullable(parentLockBuilder), lock, dataLocks::getUnchecked);
	}

	private void onLockRemoval(RemovalNotification<String, ReentrantReadWriteLock> notification) {
		LOG.trace("Deleting ReadWriteLock for {}", notification.getKey());
	}

	private static class LockLoader extends CacheLoader<List<String>, ReadWriteLock> {

		@Override
		public ReadWriteLock load(List<String> key) {
			LOG.trace("Creating ReadWriteLock for {}", key);
			return new ReentrantReadWriteLock();
		}
	}

	/*
	 * Support functions:
	 */

	// visible for testing
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
