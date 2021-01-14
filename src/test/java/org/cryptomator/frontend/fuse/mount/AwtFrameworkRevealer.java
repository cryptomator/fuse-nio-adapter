package org.cryptomator.frontend.fuse.mount;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

public class AwtFrameworkRevealer implements Revealer {

	@Override
	public void reveal(Path path) throws RevealException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			try {
				Desktop.getDesktop().open(path.toFile());
			} catch (IOException e) {
				throw new RevealException(e);
			}
		} else {
			throw new RevealException("Desktop API to browse files not supported.");
		}
	}
}
