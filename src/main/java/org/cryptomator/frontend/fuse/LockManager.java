package org.cryptomator.frontend.fuse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides a path-based locking mechanism as described by
 * <a href="https://people.eecs.berkeley.edu/~kubitron/courses/cs262a-F14/projects/reports/project6_report.pdf">Ritik Malhotra here</a>.
 */
@PerAdapter
public class LockManager {

	private static final char PATH_SEP = '/';
	private static final Splitter PATH_SPLITTER = Splitter.on(PATH_SEP).omitEmptyStrings();
	private static final Joiner PATH_JOINER = Joiner.on(PATH_SEP);

	private final ConcurrentMap<String, AtomicInteger> accessCounts = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

	@Inject
	public LockManager(){}

	/*
	 * Read locks:
	 */

	public PathLock getReadLock(String absolutePath) {
		List<String> pathComponents = PATH_SPLITTER.splitToList(absolutePath);
		Preconditions.checkArgument(!pathComponents.isEmpty(), "path must not be empty");
		return lockReadLock(pathComponents);
	}

	private PathLock lockReadLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		lockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getForLocking(path).readLock().lock();
		return new PathLock(() -> unlockReadLock(pathComponents));
	}

	private void unlockReadLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		unlockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getForUnlocking(path).readLock().unlock();
	}

	/*
	 * Write locks:
	 */

	public PathLock getWriteLock(String absolutePath) {
		List<String> pathComponents = PATH_SPLITTER.splitToList(absolutePath);
		Preconditions.checkArgument(!pathComponents.isEmpty(), "path must not be empty");
		return lockWriteLock(pathComponents);
	}

	private PathLock lockWriteLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		lockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getForLocking(path).writeLock().lock();
		return new PathLock(() -> unlockWriteLock(pathComponents));
	}

	private void unlockWriteLock(List<String> pathComponents) {
		List<String> parentPathComponents = parentPathComponents(pathComponents);
		unlockAncestors(parentPathComponents);
		String path = PATH_JOINER.join(pathComponents);
		getForUnlocking(path).writeLock().unlock();
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
		getForLocking(path).readLock().lock();
	}

	// recusively release locks for children frist
	private void unlockAncestors(List<String> pathComponents) {
		String path = PATH_JOINER.join(pathComponents);
		getForUnlocking(path).readLock().unlock();
		if (pathComponents.size() > 1) {
			unlockAncestors(parentPathComponents(pathComponents));
		}
	}

	// gets the lock for the given path, adding it on first lock
	private ReadWriteLock getForLocking(String path) {
		AtomicInteger counter = accessCounts.computeIfAbsent(path, p -> new AtomicInteger());
		counter.incrementAndGet();
		return locks.computeIfAbsent(path, p -> new ReentrantReadWriteLock());
	}

	// gets the lock for the given path, removing it on last unlockReadLock
	private ReadWriteLock getForUnlocking(String path) {
		AtomicInteger counter = accessCounts.computeIfPresent(path, (p, c) -> {
			if (c.decrementAndGet() == 0) {
				return null;
			} else {
				return c;
			}
		});
		if (counter == null) {
			return locks.remove(path);
		} else {
			return locks.get(path);
		}
	}

	int getLockCount(String absolutePath) {
		String relativePath = CharMatcher.is(PATH_SEP).trimFrom(absolutePath);
		return accessCounts.getOrDefault(relativePath, new AtomicInteger(0)).get();
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
