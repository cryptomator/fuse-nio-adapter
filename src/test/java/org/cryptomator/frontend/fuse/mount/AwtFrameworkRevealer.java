package org.cryptomator.frontend.fuse.mount;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

public class AwtFrameworkRevealer implements Revealer {

	@Override
	public void reveal(Path path) throws IOException, UnsupportedOperationException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Desktop.getDesktop().open(path.toFile());
		} else {
			throw new UnsupportedOperationException("Desktop API to browse files not supported.");
		}
	}

}
