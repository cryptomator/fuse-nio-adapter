package org.cryptomator.frontend.fuse.mount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

class MacMounter implements Mounter {

	private static final Logger LOG = LoggerFactory.getLogger(MacMounter.class);
	private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
	private static final int[] OSXFUSE_MINIMUM_SUPPORTED_VERSION = new int[]{3, 8, 2};
	private static final Path OSXFUSE_VERSIONFILE_LOCATION = Paths.get("/Library/Filesystems/osxfuse.fs/Contents/version.plist");
	private static final String OSXFUSE_XML_VERSION_TEXT = "CFBundleShortVersionString";

	@Override
	public Mount mount(Path directory, EnvironmentVariables envVars, String... additionalMountParams) throws CommandFailedException {
		MacMount mount = new MacMount(directory, envVars);
		mount.mount(additionalMountParams);
		return mount;
	}

	/**
	 * @return <code>true</code> if on OS X and osxfuse with a higher version than the minimum supported one is installed.
	 */
	@Override
	public boolean isApplicable() {
		return IS_MAC && Files.exists(Paths.get("/usr/local/lib/libosxfuse.2.dylib")) && installedVersionSupported();
	}

	public boolean installedVersionSupported() {
		String versionString = getVersionString();
		if (versionString == null) {
			LOG.error("Did not find {} in document {}.", OSXFUSE_XML_VERSION_TEXT, OSXFUSE_VERSIONFILE_LOCATION);
			return false;
		}

		Integer[] parsedVersion = Arrays.stream(versionString.split("\\.")).map(s -> Integer.valueOf(s)).toArray(Integer[]::new);
		for (int i = 0; i < OSXFUSE_MINIMUM_SUPPORTED_VERSION.length && i < parsedVersion.length; i++) {
			if (parsedVersion[i] < OSXFUSE_MINIMUM_SUPPORTED_VERSION[i]) {
				return false;
			} else if (parsedVersion[i] > OSXFUSE_MINIMUM_SUPPORTED_VERSION[i]) {
				return true;
			}
		}

		if (OSXFUSE_MINIMUM_SUPPORTED_VERSION.length <= parsedVersion.length) {
			return true;
		} else {
			return false;
		}
	}


	private String getVersionString() {
		String version = null;
		try (InputStream in = Files.newInputStream(OSXFUSE_VERSIONFILE_LOCATION, StandardOpenOption.READ)) {
			XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(in);
			while (reader.hasNext()) {
				reader.next();
				if (reader.getEventType() == XMLStreamReader.CHARACTERS && OSXFUSE_XML_VERSION_TEXT.equalsIgnoreCase(reader.getText())) {
					reader.next();
					reader.next();
					reader.next();
					version = reader.getElementText();
				}
			}
		} catch (XMLStreamException | FactoryConfigurationError e) {
			LOG.error("Could not parse file {} to detect version of OSXFUSE.", OSXFUSE_VERSIONFILE_LOCATION);
		} catch (IOException e1) {
			LOG.error("Could not read file {} to detect version of OSXFUSE.", OSXFUSE_VERSIONFILE_LOCATION);
		}
		return version;
	}

	private static class MacMount extends AbstractMount {

		private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

		private final String mountName;
		private final ProcessBuilder revealCommand;

		private MacMount(Path directory, EnvironmentVariables envVars) {
			super(directory, envVars);
			this.mountName = envVars.getMountName().orElse("vault");
			this.revealCommand = new ProcessBuilder("open", ".");
			this.revealCommand.directory(envVars.getMountPath().toFile());
		}

		@Override
		protected String[] getFuseOptions() {
			// see: https://github.com/osxfuse/osxfuse/wiki/Mount-options
			ArrayList<String> mountOptions = new ArrayList<>();
			try {
				mountOptions.add("-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"));
				mountOptions.add("-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			mountOptions.add("-oatomic_o_trunc");
			mountOptions.add("-ovolname=" + mountName);
			mountOptions.add("-oauto_xattr");
			mountOptions.add("-oauto_cache");
			mountOptions.add("-onoappledouble"); // vastly impacts performance for some reason...
			mountOptions.add("-s"); // otherwise we still have race conditions (especially when disabling noappledouble and copying dirs to mount)
			mountOptions.add("-odefault_permissions"); // let the kernel assume permissions based on file attributes etc
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public void revealInFileManager() throws CommandFailedException {
			Process proc = ProcessUtil.startAndWaitFor(revealCommand, 5, TimeUnit.SECONDS);
			ProcessUtil.assertExitValue(proc, 0);
		}

	}

}
