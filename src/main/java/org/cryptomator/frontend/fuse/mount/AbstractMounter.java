package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.MountFailedException;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractMounter implements Mounter {

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars) throws FuseMountException {
		var builder = Fuse.builder();
		var fuseAdapter = AdapterFactory.createReadWriteAdapter(directory, //
				builder.errno(), //
				AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
				envVars.getFileNameTranscoder());
		try {
			var fuse = builder.build(fuseAdapter);
			fuse.mount("fuse-nio-adapter", envVars.getMountPoint(), envVars.getFuseFlags());
			return createMountObject(fuseAdapter, fuse, envVars);
		} catch (MountFailedException e) {
			throw new FuseMountException(e);
		}
	}

	@Override
	public abstract String[] defaultMountFlags();

	@Override
	public abstract boolean isApplicable();

	protected abstract Mount createMountObject(FuseNioAdapter fuseNioAdapter, Fuse fuse, EnvironmentVariables envVars);
}
