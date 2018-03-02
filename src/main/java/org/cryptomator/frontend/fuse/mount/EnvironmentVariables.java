package org.cryptomator.frontend.fuse.mount;

import java.util.HashMap;
import java.util.Map;

public class EnvironmentVariables extends HashMap<EnvironmentVariable, String> {

	private EnvironmentVariables(Map<EnvironmentVariable, String> params) {
		super(params);
	}

	public static EnvironmentVariablesBuilder create() {
		return new EnvironmentVariablesBuilder();
	}

	public static class EnvironmentVariablesBuilder {

		private final Map<EnvironmentVariable, String> params = new HashMap<>();

		private EnvironmentVariablesBuilder with(EnvironmentVariable key, String value) {
			if (value != null) {
				params.put(key, value);
			}
			return this;
		}

		/**
		 * Adds a MountPath to the EnvironmentVariables
		 *
		 * @param value
		 * @return
		 */
		public EnvironmentVariablesBuilder withMountPath(String value) {
			return with(EnvironmentVariable.MOUNTPATH, value);
		}

		public EnvironmentVariablesBuilder withMountName(String value) {
			return with(EnvironmentVariable.MOUNTNAME, value);
		}

		public EnvironmentVariablesBuilder withRevealCommand(String value) {
			return with(EnvironmentVariable.REVEALCOMMAND, value);
		}

		public EnvironmentVariables build() {
			return new EnvironmentVariables(params);
		}

	}
}
