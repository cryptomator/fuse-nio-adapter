package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.cryptomator.jfuse.api.Fuse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

class MacMountedVolume extends AbstractMount {

	private static final Logger LOG = LoggerFactory.getLogger(MacMountedVolume.class);

	private boolean unmounted;

	public MacMountedVolume(Fuse fuse, FuseNioAdapter adapter, Path mountPoint) {
		super(fuse, adapter, mountPoint);
	}

	@Override
	public void unmount() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("umount", "--", mountpoint.getFileName().toString());
		command.directory(mountpoint.getParent().toFile());
		unmount(command, "`umount`");
	}

	@Override
	public void unmountForced() throws UnmountFailedException {
		ProcessBuilder command = new ProcessBuilder("umount", "-f", "--", mountpoint.getFileName().toString());
		command.directory(mountpoint.getParent().toFile());
		unmount(command, "`umount -f`");
	}

	private void unmount(ProcessBuilder command, String cmdDescription) throws UnmountFailedException {
		try {
			Process p = command.start();
			ProcessHelper.waitForSuccess(p, 10, cmdDescription);
			fuse.close();
			unmounted = true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UnmountFailedException(e);
		} catch (TimeoutException | IOException e) {
			throw new UnmountFailedException(e);
		} catch (ProcessHelper.CommandFailedException e) {
			if (e.stderr.contains("not currently mounted")) {
				LOG.info("{} already unmounted. Nothing to do.", mountpoint);
			} else {
				LOG.warn("{} failed with exit code {}:\nSTDOUT: {}\nSTDERR: {}\n", cmdDescription, e.exitCode, e.stdout, e.stderr);
				throw new UnmountFailedException(e);
			}
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
