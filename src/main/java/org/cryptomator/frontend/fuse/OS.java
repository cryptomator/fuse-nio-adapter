package org.cryptomator.frontend.fuse;

enum OS {
	LINUX,
	MAC,
	WINDOWS,
	UNKNOWN;

	private static String osName() {
		class Holder {
			private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
		}
		return Holder.OS_NAME;
	}

	public static OS current() {
		var name = osName();
		if (name.contains("linux")) {
			return LINUX;
		} else if (name.contains("mac")) {
			return MAC;
		} else if (name.contains("windows")) {
			return WINDOWS;
		} else {
			return UNKNOWN;
		}
	}

	public boolean isCurrent() {
		return equals(OS.current());
	}
}