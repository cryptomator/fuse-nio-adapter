package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FuseMount {

	private final FuseEnvironmentFactory environmentFactory;

	private FuseNioAdapter adapter;
	private FuseEnvironment environment;

	/**
	 * @param environmentFactory
	 */
	public FuseMount(FuseEnvironmentFactory environmentFactory) {
		this.environmentFactory = environmentFactory;
	}

	public void mount(Path mountSource, EnvironmentVariables envVar, String... additionalMountParameters) throws CommandFailedException {
		try (FuseNioAdapter fs = AdapterFactory.createReadWriteAdapter(mountSource)) {
			adapter = fs;
			environment = environmentFactory.create(envVar);
			try {
				adapter.mount(Paths.get(environment.getMountPoint().toString()), false, false, ObjectArrays.concat(environment.getMountParameters(), additionalMountParameters, String.class));
			} catch (Exception e) {
				throw new CommandFailedException("Unable to mount Filesystem.", e);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new CommandFailedException("Filesystem cannnot be initalized: Invalid Path.");
		}
	}

	public void unmount() throws CommandFailedException {
		if (adapter != null) {
			try {
				adapter.umount();
			} catch (Exception e) {
				throw new CommandFailedException(e);
			}
		}
	}

	public void reveal() throws CommandFailedException {
		environment.revealMountPathInFilesystemmanager();
	}

	public void cleanUp() throws CommandFailedException {
		environment.cleanUp();
	}

	public Path getMountPoint() {
		return environment.getMountPoint();
	}

}
