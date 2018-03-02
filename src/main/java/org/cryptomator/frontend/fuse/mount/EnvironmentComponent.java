package org.cryptomator.frontend.fuse.mount;

import dagger.Component;

@Component(modules=EnvironmentModule.class)
public interface EnvironmentComponent {

	FuseEnvironment fuseEnvironment();
}
