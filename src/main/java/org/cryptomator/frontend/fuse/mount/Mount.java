package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface Mount extends AutoCloseable {

	/**
	 * Attempts to reveal the mounted FUSE volume. This method <i>may</i> choose to ignore the given <code>revealer</code>. If the revealer is invoked, it <i>must</i> reveal the given path.
	 *
	 * @param revealer Object containing necessary commands to show the Mount content to the user.
	 */
	void reveal(Revealer revealer) throws Exception;

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
	 * @throws FuseMountException
	 */
	void unmount() throws FuseMountException;

	/**
	 * Forcefully unmounts the FUSE volume and releases corresponding resources.
	 *
	 * @throws FuseMountException
	 */
	void unmountForced() throws FuseMountException;

	/**
	 * Releases associated resources
	 *
	 * @throws FuseMountException If closing failed
	 * @throws IllegalStateException If still mounted
	 */
	@Override
	void close() throws FuseMountException, IllegalStateException;
}
