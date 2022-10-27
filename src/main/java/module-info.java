import org.cryptomator.frontend.fuse.mount.WinFspMountProvider;

module org.cryptomator.frontend.fuse {
	exports org.cryptomator.frontend.fuse;
	exports org.cryptomator.frontend.fuse.locks;
	exports org.cryptomator.frontend.fuse.mount;

	requires org.cryptomator.jfuse;
	requires javax.inject;
	requires java.desktop;
	requires org.slf4j;
	requires java.xml;
	requires dagger;
	requires com.google.common;
	requires org.cryptomator.integrations.api;

	provides org.cryptomator.integrations.mount.FilesystemMountProvider with WinFspMountProvider;
}