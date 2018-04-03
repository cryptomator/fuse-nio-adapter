package org.cryptomator.frontend.fuse.mount;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import java.util.Optional;
import java.util.Set;

@Module
class FuseMountModule {

	@Provides
	@IntoSet
	static Mounter provideWindowsFuseEnvironment() {
		return new WindowsMounter();
	}

	@Provides
	@IntoSet
	static Mounter provideLinuxEnvironment() {
		return new LinuxMounter();
	}

	@Provides
	@IntoSet
	static Mounter provideMacFuseEnvironment() {
		return new MacMounter();
	}

	@Provides
	static Optional<Mounter> provideEnvironment(Set<Mounter> envs) {
		return envs.stream().filter(Mounter::isApplicable).findFirst();
	}

}
