package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.FileNameTranscoder;
import org.cryptomator.frontend.fuse.FuseNioAdapter;
import org.cryptomator.frontend.fuse.VersionCompare;
import org.cryptomator.jfuse.api.Fuse;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.concurrent.TimeUnit;

class MacMounter extends AbstractMounter {

	private static final Logger LOG = LoggerFactory.getLogger(MacMounter.class);
	private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
	private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
	private static final String MACFUSE_MINIMUM_SUPPORTED_VERSION = "4.0.4";
	private static final String MACFUSE_VERSIONFILE_LOCATION = "/Library/Filesystems/macfuse.fs/Contents/version.plist";
	private static final String MACFUSE_VERSIONFILE_XPATH = "/plist/dict/key[.='CFBundleShortVersionString']/following-sibling::string[1]";
	private static final String PLIST_DTD_URL = "http://www.apple.com/DTDs/PropertyList-1.0.dtd";

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
					"-onoappledouble", // vastly impacts performance for some reason...
					"-odefault_permissions" // let the kernel assume permissions based on file attributes etc
			};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public FileNameTranscoder defaultFileNameTranscoder() {
		return FileNameTranscoder.transcoder().withFuseNormalization(Normalizer.Form.NFD);
	}

	@Override
	public boolean isApplicable() {
		return IS_MAC && Files.exists(Paths.get("/usr/local/lib/libosxfuse.2.dylib")) && installedVersionSupported();
	}

	@Override
	protected Mount createMountObject(FuseNioAdapter fuseNioAdapter, Fuse fuse, EnvironmentVariables envVars) {
		return new MacMount(fuseNioAdapter, fuse, envVars);
	}

	public boolean installedVersionSupported() {
		String installedVersion = getInstalledVersion(MACFUSE_VERSIONFILE_LOCATION, MACFUSE_VERSIONFILE_XPATH);
		if (installedVersion == null) {
			return false;
		} else {
			return VersionCompare.compareVersions(installedVersion, MACFUSE_MINIMUM_SUPPORTED_VERSION) >= 0;
		}
	}

	private String getInstalledVersion(String plistFileLocation, String versionXPath) {
		Path plistFile = Paths.get(plistFileLocation);
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		XPath xPath = XPathFactory.newInstance().newXPath();
		try (InputStream in = Files.newInputStream(plistFile, StandardOpenOption.READ)) {
			DocumentBuilder docBuilder = domFactory.newDocumentBuilder();
			docBuilder.setEntityResolver(this::resolveEntity);
			Document doc = docBuilder.parse(in);
			NodeList nodeList = (NodeList) xPath.compile(versionXPath).evaluate(doc, XPathConstants.NODESET);
			Node node = nodeList.item(0);
			if (node == null) {
				LOG.error("Did not find {} in document {}.", versionXPath, plistFileLocation);
				return null; // not found
			} else {
				return node.getTextContent();
			}
		} catch (ParserConfigurationException | SAXException | XPathException e) {
			LOG.error("Could not parse " + plistFileLocation + " to detect version of macFUSE.", e);
			return null;
		} catch (IOException e) {
			LOG.error("Could not read " + plistFileLocation + " to detect version of macFUSE.", e);
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

		private MacMount(FuseNioAdapter fuseAdapter, Fuse fuse, EnvironmentVariables envVars) {
			super(fuseAdapter, fuse, envVars.getMountPoint());
		}

		@Override
		public void unmountGracefully() throws FuseMountException {
			ProcessBuilder command = new ProcessBuilder("umount", "--", mountPoint.getFileName().toString());
			command.directory(mountPoint.getParent().toFile());
			Process proc = ProcessUtil.startAndWaitFor(command, 5, TimeUnit.SECONDS);
			assertUmountSucceeded(proc);
		}

		private void assertUmountSucceeded(Process proc) throws FuseMountException {
			if (proc.exitValue() == 0) {
				return;
			}
			try {
				String stderr = ProcessUtil.toString(proc.getErrorStream(), StandardCharsets.US_ASCII);
				if (stderr.contains("not currently mounted")) {
					LOG.info("Already unmounted");
				} else {
					throw new FuseMountException("Unmount failed. STDERR: " + stderr);
				}
			} catch (IOException e) {
				throw new FuseMountException(e);
			}
		}

	}

}
