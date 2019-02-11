package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.FuseFS;

public interface FuseNioAdapter extends FuseFS, AutoCloseable {

	boolean isMounted();

	/**
	 * Sets mounted to false. Other than in {@link FuseFS#umount()} this will not actually attempt to unmount the device.
	 */
	@Override
	void umount();
}
