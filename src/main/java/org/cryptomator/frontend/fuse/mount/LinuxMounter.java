package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class LinuxMounter implements Mounter {

	private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		try {
			fuseAdapter.mount(envVars.getMountPoint(), false, false, envVars.getFuseFlags());
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

		private static final String DEFAULT_REVEALCOMMAND_LINUX = "xdg-open";

		private final ProcessBuilder revealCommand;
		private final ProcessBuilder unmountCommand;
		private final ProcessBuilder unmountForcedCommand;

		private LinuxMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars);
			Path mountPoint = envVars.getMountPoint();
			String[] command = envVars.getRevealCommand().orElse(DEFAULT_REVEALCOMMAND_LINUX).split("\\s+");
			this.revealCommand = new ProcessBuilder(ObjectArrays.concat(command, mountPoint.toString()));
			this.unmountCommand = new ProcessBuilder("fusermount", "-u", "--", mountPoint.getFileName().toString());
			this.unmountCommand.directory(mountPoint.getParent().toFile());
			this.unmountForcedCommand = new ProcessBuilder("fusermount", "-u", "-z", "--", mountPoint.getFileName().toString());
			this.unmountForcedCommand.directory(mountPoint.getParent().toFile());
		}

		@Override
		public ProcessBuilder getRevealCommand() {
			return revealCommand;
		}

		@Override
		public ProcessBuilder getUnmountCommand() {
			return unmountCommand;
		}

		@Override
		public ProcessBuilder getUnmountForcedCommand() {
			return unmountForcedCommand;
		}
	}
}
