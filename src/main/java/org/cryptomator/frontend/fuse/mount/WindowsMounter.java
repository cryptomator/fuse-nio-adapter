package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class WindowsMounter implements Mounter {

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

		private WindowsMount(Path directory, EnvironmentVariables envVar) throws CommandFailedException {
			String rootString = envVar.get(EnvironmentVariable.MOUNTPATH);
			if (rootString == null) {
				throw new CommandFailedException("No drive Letter given.");
			}
			try {
				this.mountPoint = Paths.get(rootString).toAbsolutePath();
			} catch (InvalidPathException e) {
				throw new CommandFailedException(e);
			}
			this.mountName = envVar.getOrDefault(EnvironmentVariable.MOUNTNAME, "vault");
			this.revealCommand = new ProcessBuilder("explorer", "/root,", mountPoint.toString());
			this.fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		}

		private void mount(String... additionalMountParams) {
			fuseAdapter.mount(mountPoint, false, false, ObjectArrays.concat(getMountParameters(), additionalMountParams, String.class));
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
		public Path getMountPoint() {
			return mountPoint;
		}

		@Override
		public void revealMountPathInFilesystemmanager() throws CommandFailedException {
			try {
				ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
			} catch (ProcessUtil.CommandTimeoutException e) {
				throw new CommandFailedException(e.getMessage());
			}
		}

		@Override
		public void close() throws Exception {
			fuseAdapter.umount();
			fuseAdapter.close();
		}
	}
}
