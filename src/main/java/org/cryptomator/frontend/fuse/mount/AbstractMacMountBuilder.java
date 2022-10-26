package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.mount.MountProvider;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract class AbstractMacMountBuilder implements MountProvider.MountBuilder {

	protected final Path vfsRoot;
	protected Path mountPoint;
	protected Set<String> mountFlags;
	protected boolean readOnly;

	public AbstractMacMountBuilder(Path vfsRoot) {
		this.vfsRoot = vfsRoot;
	}

	@Override
	public MountProvider.MountBuilder setMountpoint(Path mountPoint) {
		this.mountPoint = mountPoint;
		return this;
	}

	@Override
	public MountProvider.MountBuilder setMountFlags(String mountFlagsString) {
		// we assume that each flag starts with "-"
		this.mountFlags = Pattern.compile("\\s?-").splitAsStream(mountFlagsString).map("-"::concat).collect(Collectors.toUnmodifiableSet());
		return this;
	}

	@Override
	public MountProvider.MountBuilder setReadOnly(boolean mountReadOnly) {
		this.readOnly = mountReadOnly;
		return this;
	}

	/**
	 * Combines the {@link #setMountFlags(String) mount flags} with any additional option that might have
	 * been set separately.
	 *
	 * @return All currently set mount options
	 */
	protected String[] combinedMountFlags() {
		var combined = new HashSet<>(mountFlags);
		if (readOnly) {
			combined.add("-r");
		}
		return combined.toArray(String[]::new);
	}

}
