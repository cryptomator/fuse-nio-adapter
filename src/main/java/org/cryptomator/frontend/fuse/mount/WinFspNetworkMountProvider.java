package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.mount.MountBuilder;
import org.cryptomator.integrations.mount.MountCapability;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.cryptomator.integrations.mount.MountCapability.MOUNT_AS_DRIVE_LETTER;
import static org.cryptomator.integrations.mount.MountCapability.MOUNT_FLAGS;
import static org.cryptomator.integrations.mount.MountCapability.READ_ONLY;
import static org.cryptomator.integrations.mount.MountCapability.UNMOUNT_FORCED;
import static org.cryptomator.integrations.mount.MountCapability.VOLUME_NAME;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WinFspNetworkMountProvider extends WinFspMountProvider {

	private static final Pattern RESERVED_CHARS = Pattern.compile("[^a-zA-Z0-9-._~]+"); // all but unreserved chars according to https://www.rfc-editor.org/rfc/rfc3986#section-2.3

	@Override
	public String displayName() {
		return "WinFsp";
	}

	@Override
	public Set<MountCapability> capabilities() {
		// no MOUNT_WITHIN_EXISTING_PARENT support here
		return EnumSet.of(MOUNT_FLAGS, MOUNT_AS_DRIVE_LETTER, UNMOUNT_FORCED, READ_ONLY, VOLUME_NAME);
	}

	@Override
	public MountBuilder forFileSystem(Path vfsRoot) {
		return new WinFspNetworkMountBuilder(vfsRoot);
	}


	private static class WinFspNetworkMountBuilder extends WinFspMountBuilder {

		private String volumeName;

		public WinFspNetworkMountBuilder(Path vfsRoot) {
			super(vfsRoot);
		}

		@Override
		public MountBuilder setVolumeName(String volumeName) {
			if(RESERVED_CHARS.matcher(volumeName).find()) {
				throw new IllegalArgumentException("Volume name must satisfy the regular expression "+RESERVED_CHARS.pattern());
			}
			this.volumeName = volumeName;

			return this;
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

		@Override
		protected Set<String> combinedMountFlags() {
			var combined = super.combinedMountFlags();
			if (volumeName != null && !volumeName.isBlank()) {
				combined.add("-oVolumePrefix=/localhost/" + volumeName);
			} else {
				combined.add("-oVolumePrefix=/localhost/" + UUID.randomUUID());
			}
			return combined;
		}
	}
}
