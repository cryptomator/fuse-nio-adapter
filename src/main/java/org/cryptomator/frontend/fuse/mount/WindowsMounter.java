package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WindowsMounter extends AbstractMounter {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsMounter.class);
	private static final boolean IS_APPLICABLE = OperatingSystem.CURRENT == OperatingSystem.WINDOWS; // && isWinFspInstalled();

	@Override
	public String[] defaultMountFlags() {
		return new String[]{"-ouid=-1", "-ogid=-1"};
	}

	@Override
	public boolean isApplicable() {
		return IS_APPLICABLE;
	}

	@Override
	protected Mount createMountObject(FuseNioAdapter fuseNioAdapter, Fuse fuse, EnvironmentVariables envVars) {
		return new WindowsMount(fuseNioAdapter, fuse, envVars);
	}

//	private static boolean isWinFspInstalled() {
//		try {
//			String path = WinPathUtils.getWinFspPath(); //Result only matters for debug-message; null-check is included in lib
//			LOG.trace("Found WinFsp installation at {}", path);
//			return true;
//		} catch (FuseException exc) {
//			LOG.debug("Failed to find a WinFsp installation; that's only a problem if you want to use FUSE on Windows. Exception text: \"{}\"", exc.getMessage());
//			return false;
//		}
//	}

	private static class WindowsMount extends AbstractMount {

		private WindowsMount(FuseNioAdapter fuseAdapter, Fuse fuse, EnvironmentVariables envVars) {
			super(fuseAdapter, fuse, envVars.getMountPoint());
		}

	}
}
