package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.MacUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MacUtilTest {

	@Test
	public void testDetectsAppleDoubleAndDSStore() {
		Path pathToAppleDouble = Paths.get("/foo/bar/._AppleDoubleFile");
		Path pathToDSStore = Paths.get("/foo/bar/baz/.DS_Store");
		Assertions.assertTrue(MacUtil.isAppleDoubleOrDStoreName(pathToAppleDouble));
		Assertions.assertTrue(MacUtil.isAppleDoubleOrDStoreName(pathToDSStore));
	}

	@Test
	public void testIgnoresOtherFiles() {
		Path pathToSomeFile = Paths.get("/foo/bar/someFileMaybeContaining._");
		Path pathToSomeFile2 = Paths.get("/foo/bar/.DS_StoreYolo");
		Assertions.assertFalse(MacUtil.isAppleDoubleOrDStoreName(pathToSomeFile));
		Assertions.assertFalse(MacUtil.isAppleDoubleOrDStoreName(pathToSomeFile2));
	}

}
