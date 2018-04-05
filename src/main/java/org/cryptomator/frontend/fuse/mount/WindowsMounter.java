package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class WindowsMounter implements Mounter {

	@Override
	public Mount mount(Path directory, EnvironmentVariables envVars, String... additionalMountParams) throws CommandFailedException {
		WindowsMount mount = new WindowsMount(directory, envVars);
		mount.mount(additionalMountParams);
		return mount;
	}

	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("windows"); // TODO check for WinFSP
	}

	private static class WindowsMount extends AbstractMount {

		private final String mountName;
		private final ProcessBuilder revealCommand;

		private WindowsMount(Path directory, EnvironmentVariables envVars) {
			super(directory, envVars);
			this.mountName = envVars.getMountName().orElse("vault");
			this.revealCommand = new ProcessBuilder("explorer", "/root,", envVars.getMountPath().toString());
		}

		@Override
		protected String[] getFuseOptions() {
			ArrayList<String> mountOptions = new ArrayList<>(8);
			mountOptions.add(("-oatomic_o_trunc"));
			mountOptions.add("-ouid=-1");
			mountOptions.add("-ogid=-1");
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oFileInfoTimeout=5000"); // TODO: measure the performance effect of FileInfoTimeout
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public void revealInFileManager() throws CommandFailedException {
			Process proc = ProcessUtil.startAndWaitFor(revealCommand,5, TimeUnit.SECONDS);
			ProcessUtil.assertExitValue(proc, 0);
		}

	}
}
