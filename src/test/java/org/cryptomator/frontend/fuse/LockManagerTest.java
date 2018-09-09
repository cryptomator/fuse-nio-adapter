package org.cryptomator.frontend.fuse;

import org.cryptomator.frontend.fuse.LockManager.PathLock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LockManagerTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
	}

	private static final Logger LOG = LoggerFactory.getLogger(LockManagerTest.class);

	@Test
	public void testLockCountDuringLock() {
		LockManager lockManager = new LockManager();
		Assertions.assertEquals(0, lockManager.getLockCount("/foo"));
		Assertions.assertEquals(0, lockManager.getLockCount("/foo/bar"));
		Assertions.assertEquals(0, lockManager.getLockCount("/foo/bar/baz"));
		try (PathLock lock1 = lockManager.getReadLock("/foo/bar/baz")) {
			Assertions.assertEquals(1, lockManager.getLockCount("/foo"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar/baz"));
			try (PathLock lock2 = lockManager.getReadLock("/foo/bar/baz")) {
				Assertions.assertEquals(2, lockManager.getLockCount("/foo"));
				Assertions.assertEquals(2, lockManager.getLockCount("/foo/bar"));
				Assertions.assertEquals(2, lockManager.getLockCount("/foo/bar/baz"));
			}
			Assertions.assertEquals(1, lockManager.getLockCount("/foo"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar/baz"));
			try (PathLock lock3 = lockManager.getReadLock("/foo/bar/baz")) {
				Assertions.assertEquals(2, lockManager.getLockCount("/foo"));
				Assertions.assertEquals(2, lockManager.getLockCount("/foo/bar"));
				Assertions.assertEquals(2, lockManager.getLockCount("/foo/bar/baz"));
			}
			Assertions.assertEquals(1, lockManager.getLockCount("/foo"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar"));
			Assertions.assertEquals(1, lockManager.getLockCount("/foo/bar/baz"));
		}
		Assertions.assertEquals(0, lockManager.getLockCount("/foo"));
		Assertions.assertEquals(0, lockManager.getLockCount("/foo/bar"));
		Assertions.assertEquals(0, lockManager.getLockCount("/foo/bar/baz"));
	}

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
				try (PathLock lock = lockManager.getReadLock("/foo/bar/baz")) {
					LOG.info("ENTER thread {}", threadnum);
					counter.incrementAndGet();
					Thread.sleep(50);
					maxCounter.set(Math.max(counter.get(), maxCounter.get()));
					counter.decrementAndGet();
					LOG.info("LEAVE thread {}", threadnum);
				} catch (InterruptedException e) {
					LOG.error("thread interrupted", e);
				}
				done.countDown();
			});
		}

		done.await();
		LOG.info("all done");
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
				try (PathLock lock = lockManager.getWriteLock("/foo/bar/baz")) {
					LOG.info("ENTER thread {}", threadnum);
					counter.incrementAndGet();
					Thread.sleep(10);
					maxCounter.set(Math.max(counter.get(), maxCounter.get()));
					counter.decrementAndGet();
					LOG.info("LEAVE thread {}", threadnum);
				} catch (InterruptedException e) {
					LOG.error("thread interrupted", e);
				}
				done.countDown();
			});
		}

		done.await();
		LOG.info("all done");
		Assertions.assertEquals(1, maxCounter.get());
	}

}
