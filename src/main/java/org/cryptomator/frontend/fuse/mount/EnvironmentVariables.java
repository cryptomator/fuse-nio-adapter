package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;

import java.nio.file.Path;
import java.util.Optional;

public class EnvironmentVariables {

	private final Path mountPoint;
	private final Optional<FileNameTranscoder> fileNameTranscoder;
	private final String[] fuseFlags;

	private EnvironmentVariables(Path mountPoint, String[] fuseFlags, Optional<FileNameTranscoder> fileNameTranscoder) {
		this.mountPoint = mountPoint;
		this.fuseFlags = fuseFlags;
		this.fileNameTranscoder = fileNameTranscoder;
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

	public Optional<FileNameTranscoder> getFileNameTranscoder() {
		return fileNameTranscoder;
	}

	public static class EnvironmentVariablesBuilder {

		private Path mountPoint = null;
		private String[] fuseFlags;
		private Optional<FileNameTranscoder> fileNameTranscoder = Optional.empty();

		public EnvironmentVariablesBuilder withMountPoint(Path mountPoint) {
			this.mountPoint = mountPoint;
			return this;
		}

		public EnvironmentVariablesBuilder withFlags(String[] fuseFlags) {
			this.fuseFlags = fuseFlags;
			return this;
		}

		public EnvironmentVariablesBuilder withFileNameTranscoder(FileNameTranscoder fileNameTranscoder) {
			this.fileNameTranscoder = Optional.of(fileNameTranscoder);
			return this;
		}

		public EnvironmentVariables build() {
			return new EnvironmentVariables(mountPoint, fuseFlags, fileNameTranscoder);
		}

	}
}
