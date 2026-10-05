package org.cryptomator.frontend.fuse;

import org.cryptomator.frontend.fuse.MacUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class MacUtilTest {

	@Test
	public void testDetectsAppleDoubleAndDSStore() {
		Path pathToAppleDouble = Path.of("/foo/bar/._AppleDoubleFile");
		Path pathToDSStore = Path.of("/foo/bar/baz/.DS_Store");
		Assertions.assertTrue(MacUtil.isAppleDoubleOrDStoreName(pathToAppleDouble));
		Assertions.assertTrue(MacUtil.isAppleDoubleOrDStoreName(pathToDSStore));
	}

	@Test
	public void testIgnoresOtherFiles() {
		Path pathToSomeFile = Path.of("/foo/bar/someFileMaybeContaining._");
		Path pathToSomeFile2 = Path.of("/foo/bar/.DS_StoreYolo");
		Assertions.assertFalse(MacUtil.isAppleDoubleOrDStoreName(pathToSomeFile));
		Assertions.assertFalse(MacUtil.isAppleDoubleOrDStoreName(pathToSomeFile2));
	}

}
