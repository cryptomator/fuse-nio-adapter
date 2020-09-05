package org.cryptomator.frontend.fuse.mount;

import jnr.ffi.Platform;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.FuseException;
import ru.serce.jnrfuse.utils.WinPathUtils;

import java.nio.file.Path;

class WindowsMounter implements Mounter {

	private static final boolean IS_WINDOWS = Platform.getNativePlatform().getOS() == Platform.OS.WINDOWS;
	private static final Logger LOG = LoggerFactory.getLogger(WindowsMounter.class);

	@Override
	public synchronized Mount mount(Path directory, boolean blocking, boolean debug, EnvironmentVariables envVars) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		try {
			fuseAdapter.mount(envVars.getMountPoint(), blocking, debug, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
		return new WindowsMount(fuseAdapter, envVars);
	}

	@Override
	public String[] defaultMountFlags() {
		return new String[]{"-ouid=-1", "-ogid=-1"};
	}

	@Override
	public boolean isApplicable() {
		return IS_WINDOWS && isWinFspInstalled();
	}

	private boolean isWinFspInstalled() {
		try {
			String path = WinPathUtils.getWinFspPath(); //Result only matters for debug-message; null-check is included in lib
			LOG.trace("Found WinFsp installation at {}", path);
			return true;
		} catch(FuseException exc) {
			LOG.debug("Failed to find a WinFsp installation; that's only a problem if you want to use FUSE on Windows. Exception text: \"{}\"", exc.getMessage());
			return false;
		}
	}

	private static class WindowsMount extends AbstractMount {

		private WindowsMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars.getMountPoint());
		}

		@Override
		public void unmount() {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			fuseAdapter.umount();
		}

		@Override
		public void unmountForced() {
			unmount();
		}

	}
}
