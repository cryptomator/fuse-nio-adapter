package org.cryptomator.frontend.fuse.mount;

public class MountFactory {

	public static FuseMount createMountObject() {
		return DaggerFuseMountComponent.create().fuseMount();
	}

}
