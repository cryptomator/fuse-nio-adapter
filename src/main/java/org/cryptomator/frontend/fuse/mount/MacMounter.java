package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

class MacMounter implements Mounter {

	private static final Logger LOG = LoggerFactory.getLogger(MacMounter.class);
	private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final int[] OSXFUSE_MINIMUM_SUPPORTED_VERSION = new int[]{3, 8, 2};
	private static final String OSXFUSE_VERSIONFILE_LOCATION = "/Library/Filesystems/osxfuse.fs/Contents/version.plist";
	private static final String OSXFUSE_VERSIONFILE_XPATH = "/plist/dict/key[.='CFBundleShortVersionString']/following-sibling::string[1]";
	private static final String PLIST_DTD_URL = "http://www.apple.com/DTDs/PropertyList-1.0.dtd";

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory);
		try {
			fuseAdapter.mount(envVars.getMountPoint(), false, false, envVars.getFuseFlags());
		} catch (RuntimeException e) {
			throw new CommandFailedException(e);
		}
		return new MacMount(fuseAdapter, envVars);
	}

	@Override
	public String[] defaultMountFlags() {
		// see: https://github.com/osxfuse/osxfuse/wiki/Mount-options
		try {
			return new String[]{
					"-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"),
					"-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"),
					"-oatomic_o_trunc",
					"-oauto_xattr",
					"-oauto_cache",
					"-omodules=iconv,from_code=UTF-8,to_code=UTF-8-MAC", // show files names in Unicode NFD encoding
					"-onoappledouble", // vastly impacts performance for some reason...
					"-odefault_permissions" // let the kernel assume permissions based on file attributes etc
			};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
			LOG.error("Did not find {} in document {}.", OSXFUSE_VERSIONFILE_XPATH, OSXFUSE_VERSIONFILE_LOCATION);
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


	/**
	 * @return Value for {@value OSXFUSE_VERSIONFILE_XPATH} in {@value OSXFUSE_VERSIONFILE_LOCATION} or <code>null</code> if this value is not present.
	 */
	private String getVersionString() {
		Path plistFile = Paths.get(OSXFUSE_VERSIONFILE_LOCATION);
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		XPath xPath = XPathFactory.newInstance().newXPath();
		try (InputStream in = Files.newInputStream(plistFile, StandardOpenOption.READ)) {
			DocumentBuilder docBuilder = domFactory.newDocumentBuilder();
			docBuilder.setEntityResolver(this::resolveEntity);
			Document doc = docBuilder.parse(in);
			NodeList nodeList = (NodeList) xPath.compile(OSXFUSE_VERSIONFILE_XPATH).evaluate(doc, XPathConstants.NODESET);
			Node node = nodeList.item(0);
			if (node == null) {
				return null; // not found
			} else {
				return node.getTextContent();
			}
		} catch (ParserConfigurationException | SAXException | XPathException e) {
			LOG.error("Could not parse " + OSXFUSE_VERSIONFILE_LOCATION + " to detect version of OSXFUSE.", e);
			return null;
		} catch (IOException e) {
			LOG.error("Could not read " + OSXFUSE_VERSIONFILE_LOCATION + " to detect version of OSXFUSE.", e);
			return null;
		}
	}

	private InputSource resolveEntity(String publicId, String systemId) {
		if (PLIST_DTD_URL.equals(systemId)) {
			// load DTD from local resource. fixes https://github.com/cryptomator/fuse-nio-adapter/issues/40
			return new InputSource(getClass().getResourceAsStream("/PropertyList-1.0.dtd"));
		} else {
			return null;
		}
	}

	private static class MacMount extends AbstractMount {

		private final ProcessBuilder revealCommand;
		private final ProcessBuilder unmountCommand;
		private final ProcessBuilder unmountForcedCommand;

		private MacMount(FuseNioAdapter fuseAdapter, EnvironmentVariables envVars) {
			super(fuseAdapter, envVars);
			Path mountPoint = envVars.getMountPoint();
			this.revealCommand = new ProcessBuilder("open", ".");
			this.revealCommand.directory(mountPoint.toFile());
			this.unmountCommand = new ProcessBuilder("umount", "--", mountPoint.getFileName().toString());
			this.unmountCommand.directory(mountPoint.getParent().toFile());
			this.unmountForcedCommand = new ProcessBuilder("umount", "-f", "--", mountPoint.getFileName().toString());
			this.unmountForcedCommand.directory(mountPoint.getParent().toFile());
		}

		@Override
		public ProcessBuilder getRevealCommand() {
			return revealCommand;
		}

		@Override
		public ProcessBuilder getUnmountCommand() {
			return unmountCommand;
		}

		@Override
		public ProcessBuilder getUnmountForcedCommand() {
			return unmountForcedCommand;
		}

	}

}
