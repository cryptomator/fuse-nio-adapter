package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AdditionalDirectoryDecorator extends FuseEnvironmentDecorator{

	private String createdDirectoryName;

	@Override
	public void makeEnvironment(EnvironmentVariables envVar) throws CommandFailedException {
		parent.makeEnvironment(envVar);
		createdDirectoryName = envVar.get(EnvironmentVariable.MOUNTNAME);
		try {
			createDirIfNotExist(
					Paths.get(parent.getMountPoint(),"/", createdDirectoryName)
			);
		} catch (IOException e) {
			throw new CommandFailedException(e);
		}
	}

	private void createDirIfNotExist(Path p) throws IOException {
		try {
			if (Files.isDirectory(p)) {
					if (Files.newDirectoryStream(p).iterator().hasNext()) {
						return;
					} else {
						throw new DirectoryNotEmptyException("Directory not empty.");
					}
			} else {
				Files.createDirectory(p);
			}
		} catch (IOException e) {
			throw e;
		}
	}

	@Override
	public String[] getMountParameters() throws CommandFailedException {
		return parent.getMountParameters();
	}

	@Override
	public String getMountPoint() {
		return parent.getMountPoint()+"/"+ createdDirectoryName;
	}

	@Override
	public void revealMountPathInFilesystemmanager() throws CommandFailedException {
		parent.revealMountPathInFilesystemmanager();
	}

	@Override
	public void cleanUp() throws CommandFailedException{
		parent.cleanUp();
		//delete additional dir
		try {
			Files.deleteIfExists(Paths.get(parent.getMountPoint(),"/", createdDirectoryName));
		} catch (IOException e) {
			//LOG.warn("Could not delete mount directory of vault " + vaultSettings.mountName().get());
			throw new CommandFailedException(e);
		}
	}

	@Override
	public boolean isApplicable() {
		//only the base class can decide this
		return parent.isApplicable();
	}

}
