import org.cryptomator.frontend.fuse.mount.FuseTMountProvider;
import org.cryptomator.frontend.fuse.mount.LinuxFuseMountProvider;
import org.cryptomator.frontend.fuse.mount.MacFuseMountProvider;
import org.cryptomator.frontend.fuse.mount.WinFspNetworkMountProvider;
import org.cryptomator.integrations.mount.MountService;
import org.cryptomator.frontend.fuse.mount.WinFspMountProvider;

module org.cryptomator.frontend.fuse {
	requires org.cryptomator.jfuse;
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.common; // TODO try to remove
	requires com.github.benmanes.caffeine;
	requires static org.jetbrains.annotations;

	provides MountService with LinuxFuseMountProvider, MacFuseMountProvider, FuseTMountProvider, WinFspMountProvider, WinFspNetworkMountProvider;
}