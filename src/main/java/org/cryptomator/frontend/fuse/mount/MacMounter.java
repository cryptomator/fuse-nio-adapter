package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class MacMounter implements Mounter {

	private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

	@Override
	public Mount mount(Path directory, EnvironmentVariables envVars, String... additionalMountParams) throws CommandFailedException {
		MacMount mount = new MacMount(directory, envVars);
		mount.mount(additionalMountParams);
		return mount;
	}

	/**
	 * @return <code>true</code> if on OS X and osxfuse is installed.
	 */
	@Override
	public boolean isApplicable() {
		return IS_MAC && Files.exists(Paths.get("/usr/local/lib/libosxfuse.2.dylib"));
	}

	private static class MacMount extends AbstractMount {

		private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

		private final String mountName;
		private final ProcessBuilder revealCommand;

		private MacMount(Path directory, EnvironmentVariables envVars) {
			super(directory, envVars);
			this.mountName = envVars.getMountName().orElse("vault");
			this.revealCommand = new ProcessBuilder("open", ".");
			this.revealCommand.directory(envVars.getMountPath().toFile());
		}

		@Override
		protected String[] getFuseOptions() {
			ArrayList<String> mountOptions = new ArrayList<>();
			try {
				mountOptions.add("-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"));
				mountOptions.add("-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			mountOptions.add("-oatomic_o_trunc");
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oauto_xattr");
			mountOptions.add("-onoappledouble"); // vastly impacts performance for some reason...
			mountOptions.add("-s"); // otherwise we still have race conditions (especially when disableing noappledouble and copying dirs to mount)
			mountOptions.add("-odefault_permissions"); // no idea what this does :D
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public void revealInFileManager() throws CommandFailedException {
			Process proc = ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
			ProcessUtil.assertExitValue(proc, 0);
		}

	}

}
