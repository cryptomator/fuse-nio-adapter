package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

class MacMountedVolume implements MountProvider.MountedVolume {

	private static final Logger LOG = LoggerFactory.getLogger(MacMountedVolume.class);

	private final Fuse fuse;
	private final Path mountPoint;
	private boolean unmounted;

	public MacMountedVolume(Fuse fuse, Path mountPoint) {
		this.fuse = fuse;
		this.mountPoint = mountPoint;
	}

	@Override
	public Path getAccessPoint() {
		return mountPoint;
	}

	@Override
	public void reveal(Consumer<Path> cmd) {
		cmd.accept(mountPoint);
	}

	@Override
	public void unmout() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("umount", "--", mountPoint.getFileSystem().toString());
		command.directory(mountPoint.getParent().toFile());
		try {
			Process p = command.start();
			p.waitFor(10, TimeUnit.SECONDS);
			fuse.close();
			unmounted = true;
		} catch (TimeoutException | IOException | InterruptedException e) {
			throw new UnmountFailedException(e);
		}
	}

	@Override
	public void unmountForced() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("umount", "--", "-f", mountPoint.getFileSystem().toString());
		command.directory(mountPoint.getParent().toFile());
		try {
			Process p = command.start();
			if (!p.waitFor(10, TimeUnit.SECONDS)) {
				LOG.warn("force unmount timed out. Pulling the plug now...");
			}
			fuse.close();
			unmounted = true;
		} catch (TimeoutException | IOException | InterruptedException e) {
			throw new UnmountFailedException(e);
		}
	}

	@Override
	public void close() throws UnmountFailedException {
		if (!unmounted) {
			unmountForced();
		}
	}
}
