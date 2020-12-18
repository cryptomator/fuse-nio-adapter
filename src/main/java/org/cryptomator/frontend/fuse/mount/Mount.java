package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface Mount extends AutoCloseable {

	/**
	 * Attempts to reveal the mounted FUSE volume in the operating system's default file manager.
	 *
	 * @throws CommandFailedException
	 * @deprecated Use {@link #getMountPoint()} and reveal it yourself (e.g. using JavaFX's <code>application.getHostServices().showDocument(mountPoint)</code>)
	 */
	@Deprecated
	void revealInFileManager() throws CommandFailedException;

	/**
	 * Returns this Mount's mount point.
	 *
	 * @return The mount point
	 * @throws IllegalStateException If not currently mounted
	 */
	Path getMountPoint();

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
