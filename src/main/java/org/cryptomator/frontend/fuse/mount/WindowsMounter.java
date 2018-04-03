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

	/**
	 * TODO: we should check more!
	 *
	 * @return
	 */
	@Override
	public boolean isApplicable() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	private static class WindowsMount implements Mount {

		private final Path mountPoint;
		private final String mountName;
		private final ProcessBuilder revealCommand;
		private final FuseNioAdapter fuseAdapter;

		private WindowsMount(Path directory, EnvironmentVariables envVars) {
			this.mountPoint = envVars.getMountPath().toAbsolutePath();
			this.mountName = envVars.getMountName().orElse("vault");
			this.revealCommand = new ProcessBuilder("explorer", "/root,", mountPoint.toString());
			this.fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		}

		private void mount(String... additionalMountParams) throws CommandFailedException {
			try {
				fuseAdapter.mount(mountPoint, false, false, ObjectArrays.concat(getMountParameters(), additionalMountParams, String.class));
			} catch (Exception e) {
				throw new CommandFailedException(e);
			}
		}

		/**
		 * TODO: measure the performance effect of FileInfoTimeout
		 *
		 * @return
		 */
		private String[] getMountParameters() {
			ArrayList<String> mountOptions = new ArrayList<>(8);
			mountOptions.add(("-oatomic_o_trunc"));
			mountOptions.add("-ouid=-1");
			mountOptions.add("-ogid=-1");
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oFileInfoTimeout=5000");
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public void revealInFileManager() throws CommandFailedException {
			ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
		}

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
}
