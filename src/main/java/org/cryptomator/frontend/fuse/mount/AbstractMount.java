package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

abstract class AbstractMount implements Mount {

	protected final FuseNioAdapter fuseAdapter;
	protected final EnvironmentVariables envVars;

	public AbstractMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
		Preconditions.checkArgument(fuseAdapter.isMounted());

		this.fuseAdapter = fuseAdapter;
		this.envVars = envVars;
	}

	@Override
	public void revealInFileManager() throws CommandFailedException {
		//NO-OP
	}

	@Override
	public void unmount() throws CommandFailedException {
		if (!this.fuseAdapter.isMounted()) {
			return;
		}
		try {
			this.fuseAdapter.umount();
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}

	@Override
	public void unmountForced() throws CommandFailedException {
		//That's not optimal, but there is no other way (that I know of)
		//to force the unmount using WinFSP (Linux and Mac override this anyway)
		unmount();
	}

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