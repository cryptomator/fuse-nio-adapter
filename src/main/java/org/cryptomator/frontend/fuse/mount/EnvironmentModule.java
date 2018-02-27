package org.cryptomator.frontend.fuse.mount;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import java.util.Set;

@Module
public class EnvironmentModule {

	@Provides
	static FallbackEnvironment provideFallbackEnvironment() {
		return new FallbackEnvironment();
	}

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
	static FuseEnvironment provideEnvironment(FallbackEnvironment fallback, Set<FuseEnvironment> envs) {
		return envs.stream().filter(FuseEnvironment::isApplicable).findFirst().orElse(fallback);
	}

}
