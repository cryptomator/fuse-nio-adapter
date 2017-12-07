package org.cryptomator.frontend.fuse;

import dagger.Component;

@PerAdapter
@Component(modules = {FuseNioAdapterModule.class})
public interface FuseNioAdapterComponent {

	ReadOnlyAdapter readOnlyAdapter();

	ReadWriteAdapter readWriteAdapter();

}
