package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.awt.*;
import java.io.IOException;
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
	public void revealInFileManager() throws CommandFailedException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			try {
				Desktop.getDesktop().browse(mountPoint.toUri());
			} catch (IOException e) {
				throw new CommandFailedException(e);
			}
		} else {
			throw new CommandFailedException("API to browse files not supported.");
		}
	}

	@Override
	public void unmount() throws CommandFailedException {
		if (fuseAdapter.isInUse()) {
			throw new CommandFailedException("Unmount refused: There are open files or pending operations.");
		}

		unmountInternal();
	}

	@Override
	public void unmountForced() throws CommandFailedException {
		unmountForcedInternal();
	}

	protected abstract void unmountInternal() throws CommandFailedException;

	protected abstract void unmountForcedInternal() throws CommandFailedException;

	@Override
	public void close() throws CommandFailedException {
		if (this.fuseAdapter.isMounted()) {
			throw new IllegalStateException("Can not close file system adapter while still mounted.");
		}
		try {
			this.fuseAdapter.close();
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}
}