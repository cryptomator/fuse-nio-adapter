package org.cryptomator.frontend.fuse.mount;

import com.google.common.base.Preconditions;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

abstract class AbstractMount implements Mount {

	protected final FuseNioAdapter fuseAdapter;
	protected final EnvironmentVariables envVars;

	public AbstractMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
		Preconditions.checkArgument(fuseAdapter.isMounted());
		this.fuseAdapter = fuseAdapter;
		this.envVars = envVars;

	}

	protected void mount() throws CommandFailedException {
		try {
			fuseAdapter.mount(envVars.getMountPoint(), false, false, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
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

	@Override
	public void close() throws CommandFailedException {
		if (fuseAdapter.isMounted()) {
			throw new IllegalStateException("Can not close file system adapter while still mounted.");
		}
		try {
			fuseAdapter.close();
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}

}
