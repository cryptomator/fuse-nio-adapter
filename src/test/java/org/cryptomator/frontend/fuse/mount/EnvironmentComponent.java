package org.cryptomator.frontend.fuse.mount;

import dagger.Component;

import java.util.Optional;

@Component(modules=FuseMountModule.class)
public interface EnvironmentComponent {

	Optional<FuseEnvironmentFactory> fuseEnvironmentFactory();
}
