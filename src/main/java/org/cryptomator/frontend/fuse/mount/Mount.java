package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

public interface Mount extends AutoCloseable {

	Path getMountPoint();

	void revealMountPathInFilesystemmanager() throws CommandFailedException;

}
