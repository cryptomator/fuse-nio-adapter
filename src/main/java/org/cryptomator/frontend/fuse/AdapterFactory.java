package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class AdapterFactory {

	private AdapterFactory() {
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root) {
		FuseNioAdapterModule module = new FuseNioAdapterModule(root);
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().fuseNioAdapterModule(module).build();
		return comp.readOnlyAdapter();
	}

}
