package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class AdapterFactory {

	private AdapterFactory() {
	}

	public static FuseNioAdapter createReadOnlyAdapter(Path root, int uid, int gid) {
		FuseNioAdapterModule module = new FuseNioAdapterModule(root, uid, gid);
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().fuseNioAdapterModule(module).build();
		return comp.readOnlyAdapter();
	}

	public static FuseNioAdapter createReadWriteAdapter(Path root, int uid, int gid) {
		FuseNioAdapterModule module = new FuseNioAdapterModule(root, uid, gid);
		FuseNioAdapterComponent comp = DaggerFuseNioAdapterComponent.builder().fuseNioAdapterModule(module).build();
		return comp.readWriteAdapter();
	}
}
