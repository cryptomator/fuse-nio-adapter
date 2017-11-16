package org.cryptomator.frontend.fuse;

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

}
