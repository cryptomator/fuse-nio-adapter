package org.cryptomator.frontend.fuse.mount;

import java.nio.file.Path;

@FunctionalInterface
public interface Revealer {

	void reveal(Path path) throws RevealException;
}
