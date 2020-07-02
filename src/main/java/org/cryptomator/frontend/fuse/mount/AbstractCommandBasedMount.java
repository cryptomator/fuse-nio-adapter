package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.util.concurrent.TimeUnit;

abstract class AbstractCommandBasedMount extends AbstractMount {

	public AbstractCommandBasedMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
		super(fuseAdapter, envVars);
	}

	protected abstract ProcessBuilder getRevealCommand();

	protected abstract ProcessBuilder getUnmountCommand();

	protected abstract ProcessBuilder getUnmountForcedCommand();

	@Override
	public void revealInFileManager() throws CommandFailedException {
		if (!fuseAdapter.isMounted()) {
			throw new CommandFailedException("Not currently mounted.");
		}
		Process proc = ProcessUtil.startAndWaitFor(getRevealCommand(), 5, TimeUnit.SECONDS);
		ProcessUtil.assertExitValue(proc, 0);
	}

	@Override
	public void unmount() throws CommandFailedException {
		if (!fuseAdapter.isMounted()) {
			return;
		}
		Process proc = ProcessUtil.startAndWaitFor(getUnmountCommand(), 5, TimeUnit.SECONDS);
		ProcessUtil.assertExitValue(proc, 0);
		fuseAdapter.umount();
	}

	@Override
	public void unmountForced() throws CommandFailedException {
		if (!fuseAdapter.isMounted()) {
			return;
		}
		Process proc = ProcessUtil.startAndWaitFor(getUnmountForcedCommand(), 5, TimeUnit.SECONDS);
		ProcessUtil.assertExitValue(proc, 0);
		fuseAdapter.umount();
	}
}
