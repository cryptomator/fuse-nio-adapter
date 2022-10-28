import org.cryptomator.frontend.fuse.mount.FuseTMountProvider;
import org.cryptomator.frontend.fuse.mount.LinuxFuseProvider;
import org.cryptomator.frontend.fuse.mount.MacFuseMountProvider;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.frontend.fuse.mount.WinFspMountProvider;

module org.cryptomator.frontend.fuse {
	requires org.cryptomator.jfuse;
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.common;

	provides MountProvider with LinuxFuseProvider, MacFuseMountProvider, FuseTMountProvider, WinFspMountProvider;

	// integrations-api needs reflective access to check annotations on the mount providers:
	opens org.cryptomator.frontend.fuse.mount to org.cryptomator.integrations.api;
}