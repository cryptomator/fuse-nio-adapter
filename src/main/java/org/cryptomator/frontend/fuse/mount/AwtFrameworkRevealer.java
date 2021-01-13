package org.cryptomator.frontend.fuse.mount;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class AwtFrameworkRevealer implements Consumer<Path> {

	@Override
	public void accept(Path path) {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			try {
				Desktop.getDesktop().open(path.toFile());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			throw new RuntimeException("Reveal not possible: API to browse files not supported.");
		}
	}
}
