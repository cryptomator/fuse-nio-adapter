package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface Mounter {

	Mount mount(Path directory, EnvironmentVariables envVars) throws FuseMountException;

	String[] defaultMountFlags();

	boolean isApplicable();

	default FileNameTranscoder defaultFileNameTranscoder() {
		return FileNameTranscoder.transcoder();
	}

}
