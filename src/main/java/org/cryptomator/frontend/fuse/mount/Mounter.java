package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface Mounter {

	default Mount mount(Path directory, EnvironmentVariables envVars) throws FuseMountException {
		return mount(directory, envVars, ignored -> {});
	}

	default Mount mount(Path directory, EnvironmentVariables envVars, Consumer<Throwable> onFuseExit) throws FuseMountException {
		return mount(directory, envVars, onFuseExit, false);
	}

	Mount mount(Path directory, EnvironmentVariables envVars, Consumer<Throwable> onFuseExit, boolean debug) throws FuseMountException;

	String[] defaultMountFlags();

	boolean isApplicable();

	default FileNameTranscoder defaultFileNameTranscoder() {
		return FileNameTranscoder.transcoder();
	}

}
