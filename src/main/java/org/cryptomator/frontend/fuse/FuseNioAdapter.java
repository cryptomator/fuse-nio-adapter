package org.cryptomator.frontend.fuse;

import ru.serce.jnrfuse.FuseFS;

public interface FuseNioAdapter extends FuseFS, AutoCloseable {

	boolean isMounted();

	/**
	 * Sets mounted to false. Other than defined in {@link FuseFS#umount()} the behaviour of this method can be configured via {@link #setUmountBehaviour(UmountBehaviour)}.
	 */
	@Override
	void umount();

	/**
	 * Specifies how this instance behaves during {@link #umount()}. Defaults to {@link UmountBehaviour#DEFAULT}.
	 *
	 * @param behaviour
	 */
	void setUmountBehaviour(UmountBehaviour behaviour);

	enum UmountBehaviour {

		/**
		 * Invoke default unmount behaviour as defined by {@link FuseFS#umount()}.
		 */
		DEFAULT,

		/**
		 * Only mark the file system as unmounted. Useful after successfully unmounting manually.
		 */
		FLAG_ONLY;
	}
}
