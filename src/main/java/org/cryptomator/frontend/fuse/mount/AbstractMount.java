package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;

abstract class AbstractMount implements Mount {

	private final EnvironmentVariables envVars;
	private final FuseNioAdapter fuseAdapter;

	public AbstractMount(Path directory, EnvironmentVariables envVars) {
		this.envVars = envVars;
		this.fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
	}

	protected void mount(String... additionalMountParams) throws CommandFailedException {
		try {
			fuseAdapter.mount(envVars.getMountPath(), false, false, ObjectArrays.concat(getMountParameters(), additionalMountParams, String.class));
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}

	protected abstract String[] getMountParameters();

	@Override
	public void close() throws CommandFailedException {
		try {
			fuseAdapter.umount();
			fuseAdapter.close();
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}

}
