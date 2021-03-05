package org.cryptomator.frontend.fuse;

import dagger.BindsInstance;
import dagger.Component;
import org.cryptomator.frontend.fuse.encoding.BufferEncoder;

import javax.inject.Named;
import java.nio.file.Path;

@PerAdapter
@Component(modules = {FuseNioAdapterModule.class})
public interface FuseNioAdapterComponent {

	ReadOnlyAdapter readOnlyAdapter();

	ReadWriteAdapter readWriteAdapter();

	@Component.Builder
	interface Builder {

		@BindsInstance
		Builder root(@Named("root") Path root);

		@BindsInstance
		Builder maxFileNameLength(@Named("maxFileNameLength") int maxFileNameLength);

		@BindsInstance
		Builder toFuseEncoder(@Named("toFuseEncoder") BufferEncoder toFuseEncoder);

		FuseNioAdapterComponent build();
	}


}
