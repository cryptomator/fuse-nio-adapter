package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class AdapterFactory {

	private AdapterFactory() {
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).build();
		return comp.readOnlyAdapter();
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root) {
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().root(root).build();
		return comp.readWriteAdapter();
	}
}
