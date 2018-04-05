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

	protected void mount(String... additionalFuseOptions) throws CommandFailedException {
		try {
			fuseAdapter.mount(envVars.getMountPath(), false, false, ObjectArrays.concat(getFuseOptions(), additionalFuseOptions, String.class));
		} catch (Exception e) {
			throw new CommandFailedException(e);
		}
	}

	protected abstract String[] getFuseOptions();

	@Override
	public void close() throws CommandFailedException {
		try {
			fuseAdapter.umount();
		} catch (Exception e) {
			throw new CommandFailedException(e);
		} finally {
			try {
				fuseAdapter.close();
			} catch (Exception e) {
				throw new CommandFailedException(e);
			}
		}
	}

}
