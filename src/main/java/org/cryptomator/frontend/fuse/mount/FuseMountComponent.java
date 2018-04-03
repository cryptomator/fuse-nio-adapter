package org.cryptomator.frontend.fuse.mount;

import dagger.Component;

import java.util.Optional;

@Component(modules = FuseMountModule.class)
interface FuseMountComponent {

	Optional<Mounter> applicableMounter();

}
