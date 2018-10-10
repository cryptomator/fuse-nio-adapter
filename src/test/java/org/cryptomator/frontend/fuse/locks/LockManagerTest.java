package org.cryptomator.frontend.fuse.locks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LockManagerTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
	}

	private static final Logger LOG = LoggerFactory.getLogger(LockManagerTest.class);

	@Nested
	@DisplayName("PathLocks")
	class PathLockTests {

		@Test
		public void testLockCountDuringLock() {
			LockManager lockManager = new LockManager();
			Assertions.assertFalse(lockManager.isPathLocked("/foo"));
			Assertions.assertFalse(lockManager.isPathLocked("/foo/bar"));
			Assertions.assertFalse(lockManager.isPathLocked("/foo/bar/baz"));
			try (PathLock lock1 = lockManager.createPathLock("/foo/bar/baz").forReading()) {
				Assertions.assertTrue(lockManager.isPathLocked("/foo"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				try (PathLock lock2 = lockManager.createPathLock("/foo/bar/baz").forReading()) {
					Assertions.assertNotSame(lock1, lock2);
					Assertions.assertTrue(lockManager.isPathLocked("/foo"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				}
				Assertions.assertTrue(lockManager.isPathLocked("/foo"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				try (PathLock lock3 = lockManager.createPathLock("/foo/bar/baz").forReading()) {
					Assertions.assertNotSame(lock1, lock3);
					Assertions.assertTrue(lockManager.isPathLocked("/foo"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				}
				Assertions.assertTrue(lockManager.isPathLocked("/foo"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
			}
			Assertions.assertFalse(lockManager.isPathLocked("/foo"));
			Assertions.assertFalse(lockManager.isPathLocked("/foo/bar"));
			Assertions.assertFalse(lockManager.isPathLocked("/foo/bar/baz"));
		}

		@Test
		public void testMultipleReadLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
			CountDownLatch done = new CountDownLatch(numThreads);
			AtomicInteger counter = new AtomicInteger();
			AtomicInteger maxCounter = new AtomicInteger();

			for (int i = 0; i < numThreads; i++) {
				int threadnum = i;
				threadPool.submit(() -> {
					try (PathLock lock = lockManager.createPathLock("/foo/bar/baz").forReading()) {
						LOG.debug("ENTER thread {}", threadnum);
						counter.incrementAndGet();
						Thread.sleep(200);
						maxCounter.set(Math.max(counter.get(), maxCounter.get()));
						counter.decrementAndGet();
						LOG.debug("LEAVE thread {}", threadnum);
					} catch (InterruptedException e) {
						LOG.error("thread interrupted", e);
					}
					done.countDown();
				});
			}

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { // deadlock protection
				done.await();
			});
			Assertions.assertEquals(numThreads, maxCounter.get());
		}

		@Test
		public void testMultipleWriteLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
			CountDownLatch done = new CountDownLatch(numThreads);
			AtomicInteger counter = new AtomicInteger();
			AtomicInteger maxCounter = new AtomicInteger();

			for (int i = 0; i < numThreads; i++) {
				int threadnum = i;
				threadPool.submit(() -> {
					try (PathLock lock = lockManager.createPathLock("/foo/bar/baz").forWriting()) {
						LOG.debug("ENTER thread {}", threadnum);
						counter.incrementAndGet();
						Thread.sleep(10);
						maxCounter.set(Math.max(counter.get(), maxCounter.get()));
						counter.decrementAndGet();
						LOG.debug("LEAVE thread {}", threadnum);
					} catch (InterruptedException e) {
						LOG.error("thread interrupted", e);
					}
					done.countDown();
				});
			}

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { // deadlock protection
				done.await();
			});
			Assertions.assertEquals(1, maxCounter.get());
		}

	}

	@Nested
	@DisplayName("DataLocks")
	class DataLockTests {

		@Test
		public void testMultipleReadLocks() throws InterruptedException {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
			CountDownLatch done = new CountDownLatch(numThreads);
			AtomicInteger counter = new AtomicInteger();
			AtomicInteger maxCounter = new AtomicInteger();

			for (int i = 0; i < numThreads; i++) {
				int threadnum = i;
				threadPool.submit(() -> {
					try (PathLock pathLock = lockManager.createPathLock("/foo/bar/baz").forReading(); //
						 DataLock dataLock = pathLock.lockDataForReading()) {
						LOG.debug("ENTER thread {}", threadnum);
						counter.incrementAndGet();
						Thread.sleep(50);
						maxCounter.set(Math.max(counter.get(), maxCounter.get()));
						counter.decrementAndGet();
						LOG.debug("LEAVE thread {}", threadnum);
					} catch (InterruptedException e) {
						LOG.error("thread interrupted", e);
					}
					done.countDown();
				});
			}

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { // deadlock protection
				done.await();
			});
			Assertions.assertEquals(numThreads, maxCounter.get());
		}

		@Test
		public void testMultipleWriteLocks() throws InterruptedException {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
			CountDownLatch done = new CountDownLatch(numThreads);
			AtomicInteger counter = new AtomicInteger();
			AtomicInteger maxCounter = new AtomicInteger();

			for (int i = 0; i < numThreads; i++) {
				int threadnum = i;
				threadPool.submit(() -> {
					try (PathLock pathLock = lockManager.createPathLock("/foo/bar/baz").forReading(); //
						 DataLock dataLock = pathLock.lockDataForWriting()) {
						LOG.debug("ENTER thread {}", threadnum);
						counter.incrementAndGet();
						Thread.sleep(10);
						maxCounter.set(Math.max(counter.get(), maxCounter.get()));
						counter.decrementAndGet();
						LOG.debug("LEAVE thread {}", threadnum);
					} catch (InterruptedException e) {
						LOG.error("thread interrupted", e);
					}
					done.countDown();
				});
			}

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> { // deadlock protection
				done.await();
			});
			Assertions.assertEquals(1, maxCounter.get());
		}

	}

}
