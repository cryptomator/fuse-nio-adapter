package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;

abstract class AbstractMount implements Mount {

	protected final FuseNioAdapter fuseAdapter;
	protected final Path mountPoint;

	public AbstractMount(FuseNioAdapter fuseAdapter, Path mountPoint) {
		Preconditions.checkArgument(fuseAdapter.isMounted());
		this.fuseAdapter = fuseAdapter;
		this.mountPoint = mountPoint;
	}

	@Override
	public Path getMountPoint() {
		Preconditions.checkState(fuseAdapter.isMounted(), "Not currently mounted.");
		return mountPoint;
	}

	@Override
	public void reveal(Revealer revealer) throws Exception {
		revealer.reveal(mountPoint);
	}

	@Override
	public void unmount() throws FuseMountException {
		if (fuseAdapter.isInUse()) {
			throw new FuseMountException("Unmount refused: There are open files or pending operations.");
		}

		unmountInternal();
	}

	@Override
	public void unmountForced() throws FuseMountException {
		unmountForcedInternal();
	}

	protected abstract void unmountInternal() throws FuseMountException;

	protected abstract void unmountForcedInternal() throws FuseMountException;

	@Override
	public void close() throws FuseMountException {
		if (this.fuseAdapter.isMounted()) {
			throw new IllegalStateException("Can not close file system adapter while still mounted.");
		}
		try {
			this.fuseAdapter.close();
		} catch (Exception e) {
			throw new FuseMountException(e);
		}
	}
}