package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a path-based locking mechanism as described by
 * <a href="https://people.eecs.berkeley.edu/~kubitron/courses/cs262a-F14/projects/reports/project6_report.pdf">Ritik Malhotra here</a>.
 *
 * <p>
 * Usage Example 1:
 * <pre>
 *     try (PathLock pathLock = lockManager.lockPathForReading("/foo/bar/baz"); // path is not manipulated, thus read-locking
 *          DataLock dataLock = pathLock.lockDataForWriting()) { // content is manipulated, thus write-locking
 *          // write to file
 *     }
 * </pre>
 *
 * <p>
 * Usage Example 2:
 * <pre>
 *     try (PathLock srcPathLock = lockManager.lockPathForReading("/foo/bar/original");
 *          DataLock srcDataLock = srcPathLock.lockDataForReading(); // content will only be read, thus read-locking
 *          PathLock dstPathLock = lockManager.lockPathForWriting("/foo/bar/copy"); // file will be created, thus write-locking
 *          DataLock dstDataLock = srcPathLock.lockDataForWriting()) {
 *          // copy from /foo/bar/original to /foo/bar/copy
 *     }
 * </pre>
 */
@PerAdapter
public class LockManager {

	private static final Logger LOG = LoggerFactory.getLogger(LockManager.class);
	private static final char PATH_SEP = '/';
	private static final Splitter PATH_SPLITTER = Splitter.on(PATH_SEP).omitEmptyStrings();
	private static final Joiner PATH_JOINER = Joiner.on(PATH_SEP);

	private final LoadingCache<String, ReadWriteLock> pathLocks;
	private final LoadingCache<String, ReadWriteLock> dataLocks;

	@Inject
	public LockManager() {
		CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().weakValues();
		if (LOG.isDebugEnabled()) {
			cacheBuilder.removalListener(this::onLockRemoval);
		}
		this.pathLocks = cacheBuilder.build(new LockLoader());
		this.dataLocks = cacheBuilder.build(new LockLoader());
	}

	/*
	 * Read locks:
	 */

	public PathLockBuilder createPathLock(String absolutePath) {
		List<String> pathComponents = PATH_SPLITTER.splitToList(absolutePath);
		if (pathComponents.isEmpty()) {
			return new NullPathLockBuilder(); // empy path (fs root) is not lockable
		} else {
			return createPathLock(pathComponents);
		}
	}

	private PathLockBuilder createPathLock(List<String> pathComponents) {
		PathLockBuilder parentLockBuilder = null;
		if (pathComponents.isEmpty()) {
			return null;
		} else if (pathComponents.size() > 1) {
			parentLockBuilder = createPathLock(parentPathComponents(pathComponents));
		}
		String path = PATH_JOINER.join(pathComponents);
		ReadWriteLock lock = pathLocks.getUnchecked(path);
		return new PathLockBuilderImpl(path, Optional.ofNullable(parentLockBuilder), lock);
	}

	public interface PathLockBuilder {
		PathLock forReading();
		PathLock forWriting();
	}

	private static class PathLockBuilderImpl implements PathLockBuilder {

		private final String path;
		private final Optional<PathLockBuilder> parent;
		private final ReadWriteLock lock;

		private PathLockBuilderImpl(String path, Optional<PathLockBuilder> parent, ReadWriteLock lock) {
			this.path = path;
			this.parent = parent;
			this.lock = lock;
		}

		public PathLock forReading() {
			Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
			lock.readLock().lock();
			LOG.trace("Acquired READ PATH lock for {}", path);
			return new PathLockImpl(path, parentLock, lock, lock.readLock());
		}

		public PathLock forWriting() {
			Optional<PathLock> parentLock = parent.map(PathLockBuilder::forReading);
			lock.writeLock().lock();
			LOG.trace("Acquired WRITE PATH lock for {}", path);
			return new PathLockImpl(path, parentLock, lock, lock.writeLock());
		}

	}

	private static class PathLockImpl implements PathLock {

		private final String path;
		private final Optional<PathLock> parent;
		private final ReadWriteLock lock; // keep reference to avoid lock being GC'ed out of our cache
		private final Lock heldLock;

		public PathLockImpl(String path, Optional<PathLock> parent, ReadWriteLock lock, Lock heldLock) {
			this.path = path;
			this.parent = parent;
			this.lock = lock;
			this.heldLock = heldLock;
		}

		@Override
		public void close() {
			heldLock.unlock();
			LOG.trace("Released PATH lock for {}", path);
			parent.ifPresent(PathLock::close);
		}

		@Override
		public DataLock lockDataForReading() {
			return null;
		}

		@Override
		public DataLock lockDataForWriting() {
			return null;
		}
	}

	/*
	 * Support functions:
	 */

	private void onLockRemoval(RemovalNotification<String, ReentrantReadWriteLock> notification) {
		LOG.trace("Removing Lock for {}", notification.getKey());
	}

	// visible for testing
	boolean isPathLocked(String absolutePath) {
		String relativePath = CharMatcher.is(PATH_SEP).trimFrom(absolutePath);
		ReadWriteLock lock = pathLocks.getIfPresent(relativePath);
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

	// visible for testing
	void cleanup() {
		pathLocks.cleanUp();
	}

	private List<String> parentPathComponents(List<String> pathComponents) {
		return pathComponents.subList(0, pathComponents.size() - 1);
	}


	interface PathLock extends AutoCloseable {

		@Override
		void close();

		default DataLock lockDataForReading() {
			return null;
		}

		default DataLock lockDataForWriting() {
			return null;
		}
	}

	private static class LockLoader extends CacheLoader<String, ReadWriteLock> {
		@Override
		public ReadWriteLock load(String key) {
			LOG.trace("Creating Lock for {}", key);
			return new ReentrantReadWriteLock();
		}
	}

	private static class NullPathLockBuilder implements PathLockBuilder {

		@Override
		public PathLock forReading() {
			return new NullPathLock();
		}

		@Override
		public PathLock forWriting() {
			return new NullPathLock();
		}

	}

	private static class NullPathLock implements PathLock {

		@Override
		public void close() {
			// no-op
		}

		@Override
		public DataLock lockDataForReading() {
			return null;
		}

		@Override
		public DataLock lockDataForWriting() {
			return null;
		}
	}

	public static class DataLock implements AutoCloseable {

		@Override
		public void close() {
			// no-op
		}
	}

}
