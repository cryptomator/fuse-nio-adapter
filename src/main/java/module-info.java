import org.cryptomator.frontend.fuse.mount.FuseTMountProvider;
import org.cryptomator.frontend.fuse.mount.LinuxFuseProvider;
import org.cryptomator.frontend.fuse.mount.MacFuseMountProvider;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.frontend.fuse.mount.WinFspMountProvider;

module org.cryptomator.frontend.fuse {
	exports org.cryptomator.frontend.fuse;
	exports org.cryptomator.frontend.fuse.locks;

	requires org.cryptomator.jfuse;
	requires org.cryptomator.integrations.api;
	requires javax.inject;
	requires java.desktop;
	requires org.slf4j;
	requires java.xml;
	requires dagger;
	requires com.google.common;

	provides MountProvider with LinuxFuseProvider, MacFuseMountProvider, FuseTMountProvider, WinFspMountProvider;
}