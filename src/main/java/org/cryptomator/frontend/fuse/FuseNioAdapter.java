package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.FuseFS;

public interface FuseNioAdapter extends FuseFS, AutoCloseable {

	boolean isMounted();

	/**
	 * Checks if it is not safe to unmount this filesystemin.
	 *
	 * Important: This function only checks if it is _not_ safe and a return value of {@code false} should not be considered as an unmount can be safely executed.
	 * The file system may be in state where it cannot be determined, hence this method returns false.
	 *
	 * API note: It is the task of the fs developer to decide what "not safe" means. For example, "not safe" can mean there are open resources or pending operations.
	 * Additionally no guarantees about the validity of the result after the call is made, i.e. the result may be immediately outdated.
	 *
	 * @return true if the filesystem is in a state, where an unmount should not be performed.
	 */
	boolean isNotSafeToUnmount();

	/**
	 * Sets mounted to false.
	 * <p>
	 * Allows custom unmount implementations to prevent subsequent invocations of {@link #umount()} to run into illegal states.
	 */
	void setUnmounted();
}
