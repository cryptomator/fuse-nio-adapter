package org.cryptomator.frontend.fuse.mount;

import jnr.ffi.Platform;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.serce.jnrfuse.FuseException;
import ru.serce.jnrfuse.utils.WinPathUtils;

class WindowsMounter extends AbstractMounter {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsMounter.class);
	private static final boolean IS_APPLICABLE = Platform.getNativePlatform().getOS() == Platform.OS.WINDOWS && isWinFspInstalled();

	@Override
	public String[] defaultMountFlags() {
		return new String[]{"-ouid=-1", "-ogid=-1"};
	}

	@Override
	public boolean isApplicable() {
		return IS_APPLICABLE;
	}

	@Override
	protected Mount createMountObject(FuseNioAdapter fuseNioAdapter, EnvironmentVariables envVars) {
		return new WindowsMount(fuseNioAdapter, envVars);
	}

	private static boolean isWinFspInstalled() {
		try {
			String path = WinPathUtils.getWinFspPath(); //Result only matters for debug-message; null-check is included in lib
			LOG.trace("Found WinFsp installation at {}", path);
			return true;
		} catch (FuseException exc) {
			LOG.debug("Failed to find a WinFsp installation; that's only a problem if you want to use FUSE on Windows. Exception text: \"{}\"", exc.getMessage());
			return false;
		}
	}

	private static class WindowsMount extends AbstractMount {

		private WindowsMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars.getMountPoint());
		}

		@Override
		protected void unmountInternal() {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			fuseAdapter.umount();
		}

		@Override
		protected void unmountForcedInternal() {
			fuseAdapter.unmountForced();
		}

	}
}
