package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;
import java.util.Optional;

public class EnvironmentVariables {

	private final Path mountPoint;
	private final String[] fuseFlags;
	private final Optional<String> revealCommand;

	private EnvironmentVariables(Path mountPoint, String[] fuseFlags, Optional<String> revealCommand) {
		this.mountPoint = mountPoint;
		this.fuseFlags = fuseFlags;
		this.revealCommand = revealCommand;
	}

	public static EnvironmentVariablesBuilder create() {
		return new EnvironmentVariablesBuilder();
	}

	public Path getMountPoint() {
		return mountPoint;
	}

	public String[] getFuseFlags() {
		return fuseFlags;
	}

	public Optional<String> getRevealCommand() {
		return revealCommand;
	}

	public static class EnvironmentVariablesBuilder {

		private Path mountPoint = null;
		private String[] fuseFlags;
		private Optional<String> revealCommand = Optional.empty();

		public EnvironmentVariablesBuilder withMountPoint(Path mountPoint) {
			this.mountPoint = mountPoint;
			return this;
		}

		public EnvironmentVariablesBuilder withFlags(String[] fuseFlags) {
			this.fuseFlags = fuseFlags;
			return this;
		}

		public EnvironmentVariablesBuilder withRevealCommand(String revealCommand) {
			this.revealCommand = Optional.ofNullable(revealCommand);
			return this;
		}

		public EnvironmentVariables build() {
			return new EnvironmentVariables(mountPoint, fuseFlags, revealCommand);
		}

	}
}
