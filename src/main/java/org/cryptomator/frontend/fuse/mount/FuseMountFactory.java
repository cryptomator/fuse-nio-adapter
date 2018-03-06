package org.cryptomator.frontend.fuse.mount;

public class FuseMountFactory {

	/**
	 * Creates a FuseMount object
	 *
	 * @return fm - FuseMount object
	 * @throws FuseNotSupportedException if the underlying os does not support FUSE or the specific FUSE driver could not be found
	 */
	public static FuseMount createMountObject() throws FuseNotSupportedException {
		return DaggerFuseMountComponent.create().fuseMount().orElseThrow(FuseNotSupportedException::new);
	}

}
