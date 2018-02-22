package org.cryptomator.frontend.fuse.mount;

import org.apache.commons.lang3.ArrayUtils;
import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Paths;

public class FuseMount {

	public static final Logger LOG = LoggerFactory.getLogger(FuseMount.class);

	private final FuseNioAdapter adapter;

	private FuseEnvironment environment;
	private boolean canBeEnhanced;

	/**
	 * TODO: the adapter should be injected as well!
	 * @param environment
	 */
	@Inject
	public FuseMount(FuseEnvironment environment) {
		this.adapter = AdapterFactory.createReadWriteAdapter(Paths.get("Y:\\"));
		this.environment = environment;
		this.canBeEnhanced = true;
	}

	public void mount(EnvironmentVariables envVar, String... additionalMountParameters ) throws CommandFailedException {
		environment.makeEnvironment(envVar);
		try{
			canBeEnhanced = false;
			adapter.mount(Paths.get(environment.getMountPoint()), false, false, ArrayUtils.addAll(environment.getMountParameters(), additionalMountParameters));
		} catch (Exception e){
			throw new CommandFailedException("Unable to mount Filesystem.", e);
		}
	}

	public void unmount() throws CommandFailedException{
		try {
			adapter.umount();
		} catch(Exception e){
			throw new CommandFailedException(e);
		}
	}

	public void cleanUp() throws CommandFailedException{
			environment.cleanUp();
	}

	public void useExtraMountDir(){
		try{
			enhanceEnvironment(new AdditionalDirectoryDecorator());
		} catch (IllegalStateException e){
			LOG.warn("Already mounted, please unmount & clean the MountObject before trying to enhance again.");
		}
	}

	private void enhanceEnvironment(FuseEnvironmentDecorator fed){
		if (canBeEnhanced){
			environment = fed.setParent(environment);
		}
		else{
			throw new IllegalStateException("Environment Already in use!");
		}

	}

}
