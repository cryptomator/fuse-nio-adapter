package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;

public abstract class AbstractMounter implements Mounter {

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars, boolean debug) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory, //
				AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
				envVars.getFileNameTranscoder());
		try {
			fuseAdapter.mount(envVars.getMountPoint(), false, debug, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
		return createMountObject(fuseAdapter, envVars);
	}

	@Override
	public abstract String[] defaultMountFlags();

	@Override
	public abstract boolean isApplicable();

	protected abstract Mount createMountObject(FuseNioAdapter fuseNioAdapter, EnvironmentVariables envVars);
}
