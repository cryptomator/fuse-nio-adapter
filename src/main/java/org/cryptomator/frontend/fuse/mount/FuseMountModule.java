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
	static FuseEnvironmentFactory provideWindowsFuseEnvironment() {
		return new WindowsFuseEnvironmentFactory();
	}

	@Provides
	@IntoSet
	static FuseEnvironmentFactory provideLinuxEnvironment() {
		return new LinuxFuseEnvironmentFactory();
	}

	@Provides
	@IntoSet
	static FuseEnvironmentFactory provideMacFuseEnvironment() {
		return new MacFuseEnvironmentFactory();
	}

	@Provides
	static Optional<FuseEnvironmentFactory> provideEnvironment(Set<FuseEnvironmentFactory> envs) {
		return envs.stream().filter(FuseEnvironmentFactory::isApplicable).findFirst();
	}

	@Provides
	static Optional<FuseMount> provideFuseMount(Optional<FuseEnvironmentFactory> environment) {
		return environment.map(FuseMount::new);
	}

}
