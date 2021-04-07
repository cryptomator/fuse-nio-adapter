package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractMounter implements Mounter {

	private static final int MOUNT_TIMEOUT_MILLIS = 10000;
	private static final AtomicInteger MOUNT_COUNTER = new AtomicInteger(0);

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars, Consumer<Throwable> onFuseExit, boolean debug) throws FuseMountException {
		AtomicReference<Throwable> exception = new AtomicReference<>();
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory, //
				AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
				envVars.getFileNameTranscoder());
		//real mount op
		var mountThread = new Thread(() -> {
			try {
				fuseAdapter.mount(envVars.getMountPoint(), true, debug, envVars.getFuseFlags());
			} catch (Exception e) {
				exception.set(e);
			} finally {
				onFuseExit.accept(exception.get());
			}
		});
		mountThread.setName("fuseMount-" + MOUNT_COUNTER.getAndIncrement() + "-main");
		mountThread.setDaemon(true);
		mountThread.start();

		// wait for mounted() is called, unlocking the barrier
		try {
			fuseAdapter.awaitInitCall(MOUNT_TIMEOUT_MILLIS);
			return createMountObject(fuseAdapter, envVars);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FuseMountException("Mounting operation interrupted.");
		} catch (TimeoutException e) {
			if (exception.get() != null) {
				throw new FuseMountException(exception.get());
			} else {
				throw new FuseMountException(e);
			}
		}
	}

	@Override
	public abstract String[] defaultMountFlags();

	@Override
	public abstract boolean isApplicable();

	protected abstract Mount createMountObject(FuseNioAdapter fuseNioAdapter, EnvironmentVariables envVars);
}
