package org.cryptomator.frontend.fuse.mount;

public interface Mount extends AutoCloseable {

	/**
	 * Attempts to reveal the mounted FUSE volume in the operating system's default file manager.
	 *
	 * @throws CommandFailedException
	 */
	void revealInFileManager() throws CommandFailedException;

	/**
	 * Gracefully attempts to unmount the FUSE volume.
	 *
	 * @throws CommandFailedException
	 */
	void unmount() throws CommandFailedException;

	/**
	 * Forcefully unmounts the FUSE volume and releases corresponding resources.
	 *
	 * @throws CommandFailedException
	 */
	void unmountForced() throws CommandFailedException;

	/**
	 * Releases associated resources
	 * @throws CommandFailedException If closing failed
	 * @throws IllegalStateException If still mounted
	 */
	@Override
	void close() throws CommandFailedException, IllegalStateException;
}
