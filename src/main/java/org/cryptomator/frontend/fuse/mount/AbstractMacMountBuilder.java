package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.mount.MountBuilder;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

abstract class AbstractMacMountBuilder extends AbstractMountBuilder {

	protected boolean readOnly;

	public AbstractMacMountBuilder(Path vfsRoot) {
		super(vfsRoot);
	}

	@Override
	public MountBuilder setReadOnly(boolean mountReadOnly) {
		this.readOnly = mountReadOnly;
		return this;
	}

	/**
	 * Combines the {@link #setMountFlags(String) mount flags} with any additional option that might have
	 * been set separately.
	 *
	 * @return Mutable set of all currently set mount options
	 */
	protected Set<String> combinedMountFlags() {
		var combined = super.combinedMountFlags();
		if (readOnly) {
			combined.add("-r");
		}
		if (volumeName != null && !volumeName.isBlank()) {
			combined.add("-ovolname=" + volumeName);
		}
		return combined;
	}

}
