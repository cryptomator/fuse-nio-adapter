package org.cryptomator.frontend.fuse.locks;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.stream.Stream;

class FilePaths {

	private static final char PATH_SEP = '/';
	private static final String ROOT = "";
	private static final Splitter PATH_SPLITTER = Splitter.on(PATH_SEP).omitEmptyStrings();
	private static final Joiner PATH_JOINER = Joiner.on(PATH_SEP);

	@Unmodifiable
	public static List<String> toComponents(String pathRelativeToRoot) {
		return Stream.concat(Stream.of(ROOT), PATH_SPLITTER.splitToStream(pathRelativeToRoot)).toList();
	}

	public static String toPath(List<String> pathComponents) {
		return PATH_JOINER.join(pathComponents);
	}

	public static List<String> parentPathComponents(List<String> pathComponents) {
		assert pathComponents.size() > 0;
		return pathComponents.subList(0, pathComponents.size() - 1);
	}

	public static String normalizePath(String path) {
		return ROOT + PATH_SEP + PATH_JOINER.join(PATH_SPLITTER.split(path));
	}

}
