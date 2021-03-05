package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import org.cryptomator.frontend.fuse.encoding.BufferEncoder;

@Module
class FuseNioAdapterModule {

	@Provides
	@PerAdapter
	protected FileStore provideRootFileStore(@Named("root") Path root) {
		try {
			return Files.getFileStore(root);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Provides
	@PerAdapter
	protected BufferEncoder provideToFuseEncoder(@Named("toFuseEncoder") BufferEncoder enc){
		return enc;
	}

}
