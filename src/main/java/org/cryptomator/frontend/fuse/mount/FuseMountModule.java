package org.cryptomator.frontend.fuse.mount;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import java.util.Optional;
import java.util.Set;

@Module
public class FuseMountModule {

	@Provides
	@IntoSet
	static FuseEnvironment provideWindowsFuseEnvironment() {
		return new WindowsFuseEnvironment();
	}

	@Provides
	@IntoSet
	static FuseEnvironment provideLinuxEnvironment() {
		return new LinuxFuseEnvironment();
	}

	@Provides
	@IntoSet
	static FuseEnvironment provideMacFuseEnvironment() {
		return new MacFuseEnvironment();
	}

	@Provides
	static Optional<FuseEnvironment> provideEnvironment(Set<FuseEnvironment> envs) {
		return envs.stream().filter(FuseEnvironment::isApplicable).findFirst();
	}

	@Provides
	static Optional<FuseMount> provideFuseMount(Optional<FuseEnvironment> environment) {
		return environment.map(FuseMount::new);
	}

}
