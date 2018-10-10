package org.cryptomator.frontend.fuse.locks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class FilePathsTest {

	@Test
	public void testToComponents() {
		Assertions.assertArrayEquals(new String[]{""}, FilePaths.toComponents("").toArray());
		Assertions.assertArrayEquals(new String[]{""}, FilePaths.toComponents("/").toArray());

		Assertions.assertArrayEquals(new String[]{"", "foo", "bar"}, FilePaths.toComponents("/foo/bar").toArray());
		Assertions.assertArrayEquals(new String[]{"", "foo", "bar"}, FilePaths.toComponents("foo/bar").toArray());
		Assertions.assertArrayEquals(new String[]{"", "foo", "bar"}, FilePaths.toComponents("foo/bar/").toArray());
		Assertions.assertArrayEquals(new String[]{"", "foo", "bar"}, FilePaths.toComponents("/foo/bar/").toArray());
		Assertions.assertArrayEquals(new String[]{"", "foo", "bar"}, FilePaths.toComponents("/foo///bar/").toArray());
	}

	@Test
	public void testToPath() {
		Assertions.assertEquals("", FilePaths.toPath(Arrays.asList()));
		Assertions.assertEquals("", FilePaths.toPath(Arrays.asList("")));
		Assertions.assertEquals("foo", FilePaths.toPath(Arrays.asList("foo")));
		Assertions.assertEquals("foo/bar", FilePaths.toPath(Arrays.asList("foo", "bar")));
		Assertions.assertEquals("/foo/bar", FilePaths.toPath(Arrays.asList("", "foo", "bar")));
	}

	@Test
	public void testParentPathComponents() {
		Assertions.assertArrayEquals(new String[]{}, FilePaths.parentPathComponents(Arrays.asList("")).toArray());
		Assertions.assertArrayEquals(new String[]{""}, FilePaths.parentPathComponents(Arrays.asList("", "foo")).toArray());
		Assertions.assertArrayEquals(new String[]{"", "foo"}, FilePaths.parentPathComponents(Arrays.asList("", "foo", "bar")).toArray());
	}

	@Test
	public void testNormalizePath() {
		Assertions.assertEquals("/", FilePaths.normalizePath(""));
		Assertions.assertEquals("/", FilePaths.normalizePath("/"));
		Assertions.assertEquals("/foo", FilePaths.normalizePath("foo"));
		Assertions.assertEquals("/foo", FilePaths.normalizePath("//foo"));
		Assertions.assertEquals("/foo/bar", FilePaths.normalizePath("foo/bar//"));
		Assertions.assertEquals("/foo/bar", FilePaths.normalizePath("/foo///bar"));

	}

}
