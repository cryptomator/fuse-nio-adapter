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
	public static Mounter provideLinuxEnvironment() {
		return new LinuxMounter();
	}

	@Provides
	@IntoSet
	public static Mounter provideMacFuseEnvironment() {
		return new MacMounter();
	}

	@Provides
	public static Optional<Mounter> provideEnvironment(Set<Mounter> envs) {
		return envs.stream().filter(Mounter::isApplicable).findAny();
	}

}
