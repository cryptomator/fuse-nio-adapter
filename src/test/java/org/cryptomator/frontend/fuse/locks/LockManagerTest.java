package org.cryptomator.frontend.fuse.locks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class LockManagerTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
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
			try (PathLock lock1 = lockManager.lockForReading("/foo/bar/baz")) {
				Assertions.assertTrue(lockManager.isPathLocked("/foo"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				try (PathLock lock2 = lockManager.lockForReading("/foo/bar/baz")) {
					Assertions.assertNotSame(lock1, lock2);
					Assertions.assertTrue(lockManager.isPathLocked("/foo"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
					Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				}
				Assertions.assertTrue(lockManager.isPathLocked("/foo"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar"));
				Assertions.assertTrue(lockManager.isPathLocked("/foo/bar/baz"));
				try (PathLock lock3 = lockManager.lockForReading("/foo/bar/baz")) {
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
		@DisplayName("read locks are shared")
		public void testMultipleReadLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			CyclicBarrier ready = new CyclicBarrier(numThreads);

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					for (int i = 0; i < numThreads; i++) {
						int threadnum = i;
						executor.submit(() -> {
							try (PathLock lock = lockManager.lockForReading("/foo/bar/baz")) {
								LOG.trace("ENTER thread {}", threadnum);
								ready.await();
								LOG.trace("LEAVE thread {}", threadnum);
							} catch (InterruptedException | BrokenBarrierException e) {
								LOG.error("thread interrupted", e);
							}
						});
					}
				}
			});
		}

		@Test
		@DisplayName("write locks are exclusive")
		public void testMultipleWriteLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			AtomicBoolean occupied = new AtomicBoolean(false);
			AtomicBoolean success = new AtomicBoolean(true);

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					for (int i = 0; i < numThreads; i++) {
						int threadnum = i;
						executor.submit(() -> {
							try (PathLock lock = lockManager.lockForWriting("/foo/bar/baz")) {
								LOG.trace("ENTER thread {}", threadnum);
								boolean wasFree = occupied.compareAndSet(false, true);
								Thread.sleep(10); // give other threads the chance to reach this point
								if (!wasFree) {
									success.set(false);
								}
								occupied.set(false);
								LOG.trace("LEAVE thread {}", threadnum);
							} catch (InterruptedException e) {
								LOG.error("thread interrupted", e);
							}
						});
					}
				}
			});
			Assertions.assertTrue(success.get());
		}

		@Test
		@DisplayName("tryLockForWriting() succeeds for unlocked resource")
		public void testTryLockForWriting() {
			LockManager lockManager = new LockManager();

			Assertions.assertDoesNotThrow(() -> {
				try (PathLock lock = lockManager.tryLockForWriting("/foo/bar/baz")) {
					// no-op
				}
			});
		}

		@Test
		@DisplayName("tryLockForWriting() fails with exception if path already locked for writing")
		public void testTryLockForWritingWhenAlreadyWriteLocked() {
			LockManager lockManager = new LockManager();
			AtomicBoolean exceptionThrown = new AtomicBoolean();

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (PathLock existingLock = lockManager.lockForWriting("/foo/bar/baz");
					 var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					executor.submit(() -> {
						try (PathLock lock = lockManager.tryLockForWriting("/foo/bar/baz")) {
							exceptionThrown.set(false);
						} catch (AlreadyLockedException e) {
							exceptionThrown.set(true);
						}
					});
				}
			});

			Assertions.assertTrue(exceptionThrown.get());
		}


		@Test
		@DisplayName("tryLockForWriting() fails with exception if path already locked for reading")
		public void testTryLockForWritingWhenAlreadyReadLocked() {
			LockManager lockManager = new LockManager();
			AtomicBoolean exceptionThrown = new AtomicBoolean();

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (PathLock existingLock = lockManager.lockForReading("/foo/bar/baz");
					 var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					executor.submit(() -> {
						try (PathLock lock = lockManager.tryLockForWriting("/foo/bar/baz")) {
							exceptionThrown.set(false);
						} catch (AlreadyLockedException e) {
							exceptionThrown.set(true);
						}
					});
				}
			});

			Assertions.assertTrue(exceptionThrown.get());
		}

	}

	@Nested
	@DisplayName("DataLocks")
	class DataLockTests {

		@Test
		@DisplayName("read locks are shared")
		public void testMultipleReadLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			CyclicBarrier ready = new CyclicBarrier(numThreads);

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					for (int i = 0; i < numThreads; i++) {
						int threadnum = i;
						executor.submit(() -> {
							try (PathLock pathLock = lockManager.lockForReading("/foo/bar/baz"); //
								 DataLock dataLock = pathLock.lockDataForReading()) {
								LOG.trace("ENTER thread {}", threadnum);
								ready.await();
								LOG.trace("LEAVE thread {}", threadnum);
							} catch (InterruptedException | BrokenBarrierException e) {
								LOG.error("thread interrupted", e);
							}
						});
					}
				}
			});
		}

		@Test
		@DisplayName("write locks are exclusive")
		public void testMultipleWriteLocks() {
			LockManager lockManager = new LockManager();
			int numThreads = 8;
			AtomicBoolean occupied = new AtomicBoolean(false);
			AtomicBoolean success = new AtomicBoolean(true);

			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { // deadlock protection
				try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
					for (int i = 0; i < numThreads; i++) {
						int threadnum = i;
						executor.submit(() -> {
							try (PathLock pathLock = lockManager.lockForReading("/foo/bar/baz"); //
								 DataLock dataLock = pathLock.lockDataForWriting()) {
								LOG.trace("ENTER thread {}", threadnum);
								boolean wasFree = occupied.compareAndSet(false, true);
								Thread.sleep(10); // give other threads the chance to reach this point
								if (!wasFree) {
									success.set(false);
								}
								occupied.set(false);
								LOG.trace("LEAVE thread {}", threadnum);
							} catch (InterruptedException e) {
								LOG.error("thread interrupted", e);
							}
						});
					}
				}
			});
			Assertions.assertTrue(success.get());
		}

	}

}
