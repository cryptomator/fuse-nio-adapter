package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

class LinuxMounter implements Mounter {

	private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public synchronized Mount mount(Path directory, boolean blocking, boolean debug, EnvironmentVariables envVars) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		try {
			fuseAdapter.mount(envVars.getMountPoint(), blocking, debug, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
		return new LinuxMount(fuseAdapter, envVars);
	}

	@Override
	public String[] defaultMountFlags() {
		try {
			return new String[]{
					"-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"),
					"-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"),
					"-oauto_unmount"
			};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean isApplicable() {
		return IS_LINUX;
	}

	private static class LinuxMount extends AbstractMount {

		private final Optional<String> customRevealCommand;

		private LinuxMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars.getMountPoint());
			this.customRevealCommand = envVars.getRevealCommand();
		}

		@Override
		public void revealInFileManager() throws CommandFailedException {
			if (customRevealCommand.isPresent()) {
				ProcessBuilder command = new ProcessBuilder(ObjectArrays.concat(customRevealCommand.get().split("\\s+"), mountPoint.toString()));
				command.directory(mountPoint.getParent().toFile());
				Process proc = ProcessUtil.startAndWaitFor(command, 5, TimeUnit.SECONDS);
				ProcessUtil.assertExitValue(proc, 0);
			} else {
				super.revealInFileManager();
			}
		}

		@Override
		public void unmount() throws CommandFailedException {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			ProcessBuilder command = new ProcessBuilder("fusermount", "-u", "--", mountPoint.getFileName().toString());
			command.directory(mountPoint.getParent().toFile());
			Process proc = ProcessUtil.startAndWaitFor(command, 5, TimeUnit.SECONDS);
			ProcessUtil.assertExitValue(proc, 0);
			fuseAdapter.setUnmounted();
		}

		@Override
		public void unmountForced() throws CommandFailedException {
			if (!fuseAdapter.isMounted()) {
				return;
			}
			ProcessBuilder command = new ProcessBuilder("fusermount", "-u", "-z", "--", mountPoint.getFileName().toString());
			command.directory(mountPoint.getParent().toFile());
			Process proc = ProcessUtil.startAndWaitFor(command, 5, TimeUnit.SECONDS);
			ProcessUtil.assertExitValue(proc, 0);
			fuseAdapter.setUnmounted();
		}
	}
}
