package org.cryptomator.frontend.fuse.mount;

import jnr.ffi.Platform;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;

class WindowsMounter implements Mounter {

	private static final boolean IS_WINDOWS = Platform.getNativePlatform().getOS() == Platform.OS.WINDOWS;

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		try {
			fuseAdapter.mount(envVars.getMountPoint(), false, false, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
		return new WindowsMount(fuseAdapter, envVars);
	}

	@Override
	public String[] defaultMountFlags() {
		return new String[0];
	}

	@Override
	public boolean isApplicable() {
		return IS_WINDOWS;
	}

	private static class WindowsMount extends AbstractMount {

		private WindowsMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars);
		}
	}
}
