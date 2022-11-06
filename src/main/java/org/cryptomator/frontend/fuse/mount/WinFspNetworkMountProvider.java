package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.mount.MountBuilder;
import org.cryptomator.integrations.mount.MountCapability;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_AS_DRIVE_LETTER;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.READ_ONLY;
import static org.cryptomator.integrations.mount.MountCapability.UNMOUNT_FORCED;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WinFspNetworkMountProvider extends WinFspMountProvider {

	@Override
	public String displayName() {
		return "WinFSP (Network Drive)";
	}

	@Override
	public Set<MountCapability> capabilities() {
		// no MOUNT_WITHIN_EXISTING_PARENT support here
		return EnumSet.of(MOUNT_FLAGS, MOUNT_AS_DRIVE_LETTER, UNMOUNT_FORCED, READ_ONLY);
	}

	@Override
	public String getDefaultMountFlags(String mountName) {
		return super.getDefaultMountFlags(mountName) + " -oVolumePrefix=/localhost/" + mountName; // TODO: instead of /localhost/ we can use any made-up hostname
	}

	@Override
	public MountBuilder forFileSystem(Path vfsRoot) {
		return new WinFspNetworkMountBuilder(vfsRoot);
	}


	private static class WinFspNetworkMountBuilder extends WinFspMountBuilder {
		public WinFspNetworkMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountBuilder setMountpoint(Path mountPoint) {
			if (mountPoint.getRoot().equals(mountPoint)) { // MOUNT_AS_DRIVE_LETTER
				this.mountPoint = mountPoint;
				return this;
			} else {
				throw new IllegalArgumentException("mount point must be a drive letter");
			}
		}
	}
}
