package org.cryptomator.frontend.fuse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class WindowsUtilTest {

	@Test
	@EnabledOnOs(OS.WINDOWS)
	public void testGetLocalizedErrorMessageWindows() {
		var errorMsg = WindowsUtil.getLocalizedMessageForSharingViolation();
		Assertions.assertTrue(errorMsg.isPresent());
		Assertions.assertFalse(errorMsg.get().isBlank());
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	public void testGetLocalizedErrorMessageNotWindows() {
		var errorMsg = WindowsUtil.getLocalizedMessageForSharingViolation();
		Assertions.assertTrue(errorMsg.isEmpty());
	}
}
