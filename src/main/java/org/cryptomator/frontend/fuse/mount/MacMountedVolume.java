package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

class MacMountedVolume extends AbstractMount {

	private boolean unmounted;

	public MacMountedVolume(Fuse fuse, FuseNioAdapter adapter, Path mountPoint) {
		super(fuse, adapter, mountPoint);
	}

	@Override
	public void unmount() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("diskutil", "unmount", mountpoint.getFileName().toString());
		command.directory(mountpoint.getParent().toFile());
		unmount(command, "`diskutil unmount`");
	}

	@Override
	public void unmountForced() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("diskutil", "unmount", "force", mountpoint.getFileName().toString());
		command.directory(mountpoint.getParent().toFile());
		unmount(command, "`diskutil unmount force`");
	}

	private void unmount(ProcessBuilder command, String cmdDescription) throws UnmountFailedException {
		try {
			Process p = command.start();
			ProcessHelper.waitForSuccess(p, 10, cmdDescription, UnmountFailedException::new);
			fuse.close();
			unmounted = true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UnmountFailedException(e);
		} catch (TimeoutException | IOException e) {
			throw new UnmountFailedException(e);
		}
	}

	@Override
	public void close() throws UnmountFailedException, IOException {
		if (!unmounted) {
			unmountForced();
		}
		super.close();
	}
}
