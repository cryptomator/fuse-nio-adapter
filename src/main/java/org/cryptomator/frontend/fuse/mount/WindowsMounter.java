package org.cryptomator.frontend.fuse.mount;

import jnr.ffi.Platform;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
		return new String[] {"-ouid=-1", "-ogid=-1"};
	}

	@Override
	public boolean isApplicable() {
		return IS_WINDOWS;
	}

	private static class WindowsMount extends AbstractMount {

		private final ProcessBuilder revealCommand;

		//Copy from AbstractCommandBasedMount
		public ProcessBuilder getRevealCommand() {
			return revealCommand;
		}

		//Copy from AbstractCommandBasedMount
		@Override
		public void revealInFileManager() throws CommandFailedException {
			if (!fuseAdapter.isMounted()) {
				throw new CommandFailedException("Not currently mounted.");
			}
			Process proc = ProcessUtil.startAndWaitFor(getRevealCommand(), 5, TimeUnit.SECONDS);
			//That's the only difference because Windows Explorer only returns 1 as exit value (instead of 0)
			//See: https://github.com/cryptomator/dokany-nio-adapter/blob/6c6e1d44129dff0f078958e2f35acaa2178d1754/src/main/java/org/cryptomator/frontend/dokany/Mount.java#L37-L38
			ProcessUtil.assertExitValue(proc, 1);
		}

		private WindowsMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars);
			//Copy from dokany-nio-adapter
			this.revealCommand = new ProcessBuilder("explorer", "/root,", envVars.getMountPoint().toString());
		}
	}
}
