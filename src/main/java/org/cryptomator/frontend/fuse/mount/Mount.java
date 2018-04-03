package org.cryptomator.frontend.fuse.mount;

public interface Mount extends AutoCloseable {

	/**
	 * Attempts to reveal the mounted FUSE volume in the operating system's default file manager.
	 *
	 * @throws CommandFailedException
	 */
	void revealInFileManager() throws CommandFailedException;

	/**
	 * Unmounts the FUSE volume and releases corresponding resources.
	 *
	 * @throws CommandFailedException
	 */
	void close() throws CommandFailedException;

}
