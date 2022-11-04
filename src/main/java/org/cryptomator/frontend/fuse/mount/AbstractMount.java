package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.integrations.mount.Mount;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

abstract class AbstractMount implements Mount {

	protected final Fuse fuse;
	protected final FuseNioAdapter fuseNioAdapter;
	protected final Path mountpoint;

	public AbstractMount(Fuse fuse, FuseNioAdapter fuseNioAdapter, Path mountpoint) {
		this.fuse = fuse;
		this.fuseNioAdapter = fuseNioAdapter;
		this.mountpoint = mountpoint;
	}

	@Override
	public Path getMountpoint() {
		return mountpoint;
	}

	@Override
	@MustBeInvokedByOverriders
	public void close() throws UnmountFailedException {
		try {
			fuse.close();
			fuseNioAdapter.close();
		} catch (TimeoutException e) {
			throw new UnmountFailedException("Fuse loop shutdown timed out.", e);
		} catch (IOException e) {
			throw new UnmountFailedException("Unmount succeeded, but I/O cleanup is stuck.", e); // TODO use a different kind of exception? (would require API change)
		}
	}
}
