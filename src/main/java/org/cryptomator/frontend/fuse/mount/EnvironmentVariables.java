package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class EnvironmentVariables {

	private final Path mountPath;
	private final Optional<String> mountName;
	private final Optional<String> revealCommand;

	private EnvironmentVariables(Path mountPath, Optional<String> mountName, Optional<String> revealCommand) {
		this.mountPath = mountPath;
		this.mountName = mountName;
		this.revealCommand = revealCommand;
	}



	public static EnvironmentVariablesBuilder create() {
		return new EnvironmentVariablesBuilder();
	}

	public Path getMountPath() {
		return mountPath;
	}

	public Optional<String> getMountName() {
		return mountName;
	}

	public Optional<String> getRevealCommand() {
		return revealCommand;
	}

	public static class EnvironmentVariablesBuilder {

		private Path mountPath = null;
		private Optional<String> mountName = Optional.empty();
		private Optional<String> revealCommand = Optional.empty();

		public EnvironmentVariablesBuilder withMountPath(Path mountPath) {
			this.mountPath = mountPath;
			return this;
		}

		public EnvironmentVariablesBuilder withMountName(String mountName) {
			this.mountName = Optional.ofNullable(mountName);
			return this;
		}

		public EnvironmentVariablesBuilder withRevealCommand(String revealCommand) {
			this.revealCommand = Optional.ofNullable(revealCommand);
			return this;
		}

		public EnvironmentVariables build() {
			return new EnvironmentVariables(mountPath, mountName, revealCommand);
		}

	}
}
