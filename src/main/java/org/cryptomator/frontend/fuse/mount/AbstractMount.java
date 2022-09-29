package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.jfuse.api.Fuse;

import java.nio.file.Path;
import java.util.function.Consumer;

abstract class AbstractMount implements Mount {

	protected final FuseNioAdapter fuseAdapter;
	protected final Fuse fuse;
	protected final Path mountPoint;

	public AbstractMount(FuseNioAdapter fuseAdapter, Fuse fuse, Path mountPoint) {
		this.fuseAdapter = fuseAdapter;
		this.fuse = fuse;
		this.mountPoint = mountPoint;
	}

	@Override
	public Path getMountPoint() {
		return mountPoint;
	}

	@Override
	public void reveal(Revealer revealer) throws Exception {
		revealer.reveal(mountPoint);
	}

	@Override
	public void close() throws FuseMountException {
		try {
			this.fuse.close();
			this.fuseAdapter.close();
		} catch (Exception e) {
			throw new FuseMountException(e);
		}
	}
}