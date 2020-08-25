package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.FuseFS;

public interface FuseNioAdapter extends FuseFS, AutoCloseable {

	boolean isMounted();

	/**
	 * Sets mounted to false.
	 * <p>
	 * Allows custom unmount implementations to prevent subsequent invocations of {@link #umount()} to run into illegal states.
	 */
	void setUnmounted();
}
