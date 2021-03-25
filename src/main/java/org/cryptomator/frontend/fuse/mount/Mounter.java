package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;

import java.nio.file.Path;

public interface Mounter {

	default Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException {
		return mount(directory, envVars, false);
	}

	Mount mount(Path directory, EnvironmentVariables envVars, boolean debug) throws CommandFailedException;

	String[] defaultMountFlags();

	boolean isApplicable();

	default FileNameTranscoder defaultFileNameTranscoder() {
		return FileNameTranscoder.transcoder();
	}

}
