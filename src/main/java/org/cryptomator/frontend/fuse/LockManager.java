package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a path-based locking mechanism as described by
 * <a href="https://people.eecs.berkeley.edu/~kubitron/courses/cs262a-F14/projects/reports/project6_report.pdf">Ritik Malhotra here</a>.
 */
@PerAdapter
public class LockManager {

	private static final Logger LOG = LoggerFactory.getLogger(LockManager.class);
	private static final char PATH_SEP = '/';
	private static final Splitter PATH_SPLITTER = Splitter.on(PATH_SEP).omitEmptyStrings();
	private static final Joiner PATH_JOINER = Joiner.on(PATH_SEP);

	private final ConcurrentMap<String, ReentrantReadWriteLock> pathLocks = new ConcurrentHashMap<>();

	@Inject
	public LockManager(){}

	/*
	 * Read locks:
	 */

	public PathLock lockPathForReading(String absolutePath) {
		List<String> pathComponents = PATH_SPLITTER.splitToList(absolutePath);
		Preconditions.checkArgument(!pathComponents.isEmpty(), "path must not be empty");
		return lockPathForReading(pathComponents);
	}

	private PathLock lockPathForReading(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		lockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).readLock().lock();
		return new PathLock(() -> unlockReadLock(pathComponents));
	}

	private void unlockReadLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).readLock().unlock();
		removeLockIfUnused(path);
		unlockAncestors(parentPathComponents);
	}

	/*
	 * Write locks:
	 */

	public PathLock lockPathForWriting(String absolutePath) {
		List<String> pathComponents = PATH_SPLITTER.splitToList(absolutePath);
		Preconditions.checkArgument(!pathComponents.isEmpty(), "path must not be empty");
		return lockPathForWriting(pathComponents);
	}

	private PathLock lockPathForWriting(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		lockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).writeLock().lock();
		return new PathLock(() -> unlockWriteLock(pathComponents));
	}

	private void unlockWriteLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).writeLock().unlock();
		removeLockIfUnused(path);
		unlockAncestors(parentPathComponents);
	}

	/*
	 * Support functions:
	 */

	// recusively acquire locks for parents first
	private void lockAncestors(List<String> pathComponents) {
		if (pathComponents.size() > 1) {
			lockAncestors(parentPathComponents(pathComponents));
		}
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).readLock().lock();
	}

	// recusively release locks for children frist
	private void unlockAncestors(List<String> pathComponents) {
		String path = PATH_JOINER.join(pathComponents);
		getPathLock(path).readLock().unlock();
		removeLockIfUnused(path);
		if (pathComponents.size() > 1) {
			unlockAncestors(parentPathComponents(pathComponents));
		}
	}

	private ReadWriteLock getPathLock(String path) {
		return pathLocks.computeIfAbsent(path, p -> {
			LOG.trace("Creating Lock for {}", path);
			return new ReentrantReadWriteLock(true);
		});
	}

	private void removeLockIfUnused(String path) {
		pathLocks.compute(path, (p, l) -> {
			if (l.writeLock().tryLock()) { // if we can become the exlusive lock holder
				try {
					if (!l.hasQueuedThreads()) { // and if nobody else is waiting for a lock
						LOG.trace("Removing Lock for {}", path);
						return null; // then remove this map entry
					}
				} finally {
					l.writeLock().unlock();
				}
			}
			return l; // per default: leave map unchanged
		});
	}

	// visible for testing
	boolean isLocked(String absolutePath) {
		String relativePath = CharMatcher.is(PATH_SEP).trimFrom(absolutePath);
		return pathLocks.containsKey(relativePath);
	}

	private List<String> parentPathComponents(List<String> pathComponents) {
		return pathComponents.subList(0, pathComponents.size() - 1);
	}

	public static class PathLock implements AutoCloseable {

		private final Runnable unlockFunction;

		private PathLock(Runnable unlockFunction) {
			this.unlockFunction = unlockFunction;
		}

		@Override
		public void close() {
			unlockFunction.run();
		}
	}

}
