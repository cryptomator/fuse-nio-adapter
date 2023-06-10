package org.cryptomator.frontend.fuse.locks;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FilePaths {

	private static final String PATH_SEP = "/";
	private static final String ROOT = "";
	private static final Pattern PATH_REGEX = Pattern.compile(PATH_SEP);

	@Unmodifiable
	public static List<String> toComponents(String pathRelativeToRoot) {
		return Stream.concat(Stream.of(ROOT), PATH_REGEX
				.splitAsStream(pathRelativeToRoot)
				.filter(Predicate.not(String::isEmpty))).toList();
	}

	public static String toPath(List<String> pathComponents) {
		return String.join(PATH_SEP, pathComponents);
	}

	public static List<String> parentPathComponents(List<String> pathComponents) {
		assert !pathComponents.isEmpty();
		return pathComponents.subList(0, pathComponents.size() - 1);
	}

	public static String normalizePath(String path) {
		return ROOT + PATH_SEP + PATH_REGEX
				.splitAsStream(path)
				.filter(Predicate.not(String::isEmpty))
				.collect(Collectors.joining(PATH_SEP));
	}

}
