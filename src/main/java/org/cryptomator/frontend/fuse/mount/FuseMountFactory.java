package org.cryptomator.frontend.fuse.mount;

public class FuseMountFactory {

	private static FuseMountComponent COMP = DaggerFuseMountComponent.create();

	/**
	 * @return Mounter applicable on the current OS.
	 * @throws FuseNotSupportedException if the underlying OS does not support FUSE or the specific FUSE driver could not be found
	 */
	public static Mounter getMounter() throws FuseNotSupportedException {
		return COMP.applicableMounter().orElseThrow(FuseNotSupportedException::new);
	}

	public static boolean isFuseSupported() {
		return COMP.applicableMounter().isPresent();
	}

}
