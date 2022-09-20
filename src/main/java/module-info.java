module org.cryptomator.frontend.fuse {
	exports org.cryptomator.frontend.fuse;
	exports org.cryptomator.frontend.fuse.locks;
	exports org.cryptomator.frontend.fuse.mount;

	requires org.cryptomator.jfuse.api;
	requires javax.inject;
	requires java.desktop;
	requires org.slf4j;
	requires java.xml;
	requires dagger;
	requires com.google.common;
}