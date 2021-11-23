package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.FuseFS;

import java.util.concurrent.TimeoutException;

public interface FuseNioAdapter extends FuseFS, AutoCloseable {

	boolean isMounted();

	/**
	 * Checks if the filesystem is in use (and therefore an unmount attempt should be avoided).
	 * <p>
	 * Important: This function only checks, like the name suggests, if the filesystem is busy and used. A return value of {@code false} should not be considered as an unmount can be safely and successfully executed and thus an unmount may fail.
	 * <p>
	 * API note: It is the task of the fs developer to decide what "in use" means. For example, "in use" can mean there are open resources or pending operations.
	 * Additionally no guarantees about the validity of the result after the call is made, i.e. the result may be immediately outdated.
	 *
	 * @return true if the filesystem is in use
	 */
	boolean isInUse();

	/**
	 * Sets mounted to false.
	 * <p>
	 * Allows custom unmount implementations to prevent subsequent invocations of {@link #umount()} to run into illegal states.
	 */
	void setUnmounted();

	/**
	 * If the init() callback of fuse_operations is implemented, this method blocks until it is called or a specified timeout is hit. Otherwise returns directly.
	 *
	 * @param timeOutMillis the timeout in milliseconds to wait until the init() call
	 * @throws InterruptedException If the waiting thread is interrupted.
	 * @throws TimeoutException If the waiting thread waits longer than the specified {@code timeout}.
	 */
	void awaitInitCall(long timeOutMillis) throws InterruptedException, TimeoutException;

	void unmountForced();
}
