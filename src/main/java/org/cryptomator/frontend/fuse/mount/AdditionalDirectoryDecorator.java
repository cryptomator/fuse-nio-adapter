package org.cryptomator.frontend.fuse.mount;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryNotEmptyException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdditionalDirectoryDecorator extends FuseEnvironmentDecorator {

	private String createdDirectoryName;
	private ProcessBuilder revealCommand;

	/**
	 * Assumption: the last paraemter of a command is the mount path
	 *
	 * @param envVar
	 * @throws CommandFailedException
	 */
	@Override
	public void makeEnvironment(EnvironmentVariables envVar) throws CommandFailedException {
		parent.makeEnvironment(envVar);
		createdDirectoryName = envVar.get(EnvironmentVariable.MOUNTNAME);
		try {
			createDirIfNotExist(
					Paths.get(parent.getMountPoint(), "/", createdDirectoryName)
			);
		} catch (IOException e) {
			throw new CommandFailedException(e);
		}
		List<String> commands = parent.getRevealCommands();
		commands.set(commands.size() - 1, parent.getMountPoint() + "/" + createdDirectoryName + "/");
		this.revealCommand = new ProcessBuilder(commands);
	}

	private void createDirIfNotExist(Path p) throws IOException {
		try {
			if (Files.isDirectory(p)) {
				try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(p)) {
					if (!dirStream.iterator().hasNext()) {
						return;
					} else {
						throw new DirectoryNotEmptyException("Directory not empty.");
					}
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
		return parent.getMountPoint() + "/" + createdDirectoryName;
	}

	@Override
	public void revealMountPathInFilesystemmanager() throws CommandFailedException {
		try {
			ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
		} catch (ProcessUtil.CommandTimeoutException e) {
			throw new CommandFailedException(e.getMessage());
		}
	}

	@Override
	public List<String> getRevealCommands() {
		return new ArrayList<>(parent.getRevealCommands());
	}

	@Override
	public void cleanUp() throws CommandFailedException {
		parent.cleanUp();
		//delete additional dir
		try {
			Files.delete(Paths.get(parent.getMountPoint(), "/", createdDirectoryName));
		} catch (IOException e) {
			//LOG.warn("Could not delete mount directory of vault " + vaultSettings.mountName().get());
			throw new CommandFailedException("Could not delete additional directory.");
		}
	}

	@Override
	public boolean isApplicable() {
		//only the base class can decide this
		return parent.isApplicable();
	}

}
