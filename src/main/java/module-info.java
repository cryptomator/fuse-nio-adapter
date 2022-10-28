import org.cryptomator.frontend.fuse.mount.FuseTMountProvider;
import org.cryptomator.frontend.fuse.mount.LinuxFuseProvider;
import org.cryptomator.frontend.fuse.mount.MacFuseMountProvider;
import org.cryptomator.integrations.mount.MountProvider;
import org.cryptomator.frontend.fuse.mount.WinFspMountProvider;

module org.cryptomator.frontend.fuse {
	requires org.cryptomator.jfuse;
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.common; // TODO try to remove
	requires com.github.benmanes.caffeine;
	requires static org.jetbrains.annotations;

	provides MountProvider with LinuxFuseProvider, MacFuseMountProvider, FuseTMountProvider, WinFspMountProvider;
}