package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WindowsUtilTest {

	@Test
	public void testGetLocalizedErrorMessage() {
		Assertions.assertNotEquals("NUTHIN'",WindowsUtil.windowsWorkaround());
	}
}
