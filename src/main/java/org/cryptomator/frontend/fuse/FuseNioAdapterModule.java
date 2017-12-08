package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

@Module
class FuseNioAdapterModule {

	private final Path root;

	FuseNioAdapterModule(Path root) {
		this.root = root;
	}

	@Provides
	@PerAdapter
	@Named("root")
	public Path provideRootPath() {
		return root;
	}

	@Provides
	@PerAdapter
	protected FileStore provideRootFileStore() {
		try {
			return Files.getFileStore(root);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
