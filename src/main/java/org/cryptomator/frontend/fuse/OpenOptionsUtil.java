package org.cryptomator.frontend.fuse;

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import jnr.constants.platform.OpenFlags;

@PerAdapter
public class OpenOptionsUtil {

	private final BitMaskEnumUtil bitMaskUtil;

	@Inject
	public OpenOptionsUtil(BitMaskEnumUtil bitMaskUtil) {
		this.bitMaskUtil = bitMaskUtil;
	}

	public Set<OpenOption> fuseOpenFlagsToNioOpenOptions(long mask) {
		Set<OpenFlags> flags = bitMaskUtil.bitMaskToSet(OpenFlags.class, mask);
		return fuseOpenFlagsToNioOpenOptions(flags);
	}

	public Set<OpenOption> fuseOpenFlagsToNioOpenOptions(Set<OpenFlags> flags) {
		Set<OpenOption> result = new HashSet<>();
		// https://linux.die.net/man/3/open:
		if (flags.contains(OpenFlags.O_RDWR)) {
			result.add(StandardOpenOption.READ);
			result.add(StandardOpenOption.WRITE);
		} else if (flags.contains(OpenFlags.O_WRONLY)) {
			result.add(StandardOpenOption.WRITE);
		} else if (flags.contains(OpenFlags.O_RDONLY)) {
			result.add(StandardOpenOption.READ);
		}
		if (flags.contains(OpenFlags.O_APPEND)) {
			result.add(StandardOpenOption.APPEND);
		}
		if (flags.contains(OpenFlags.O_TRUNC)) {
			result.add(StandardOpenOption.TRUNCATE_EXISTING);
		}
		return result;
	}
}
