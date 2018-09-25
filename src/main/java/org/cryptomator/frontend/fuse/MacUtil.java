package org.cryptomator.frontend.fuse;

import java.nio.file.Path;

public class MacUtil {

	public static final String APPLEDOUBLE_PREFIX = "._";
	public static final String DSSTORE_FILENAME = ".DS_STORE";


	public static boolean isAppleDouble(Path p) {
		String filename = p.getFileName().toString();
		return filename.startsWith(APPLEDOUBLE_PREFIX) || filename.equals(DSSTORE_FILENAME);
	}

}
