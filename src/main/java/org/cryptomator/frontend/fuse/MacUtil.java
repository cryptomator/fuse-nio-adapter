package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class MacUtil {

	public static final String APPLEDOUBLE_PREFIX = "._";
	public static final String DSSTORE_FILENAME = ".DS_Store";


	/**
	 * Checks if the resource name of the given path is a MacOS specific AppleDouble or DS_Store filename
	 *
	 * @param p the path of the resource to be checked
	 * @return true if the resource name fits into the AppleDpuble or DS_Store naming scheme
	 */
	public static boolean isAppleDoubleOrDStoreName(Path p) {
		String filename = p.getFileName().toString();
		return filename.startsWith(APPLEDOUBLE_PREFIX) || filename.equals(DSSTORE_FILENAME);
	}

}
