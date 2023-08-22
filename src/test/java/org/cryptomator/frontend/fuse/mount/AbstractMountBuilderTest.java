package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.integrations.mount.Mount;
import org.cryptomator.integrations.mount.MountFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Arrays;

public class AbstractMountBuilderTest {

	TestMountBuilder inTest = new TestMountBuilder();

	@ParameterizedTest
	@CsvSource(textBlock = """
			-asd,-asd
			-asd\t-qwe,-asd;-qwe
			 -asd ,-asd
			-abc qwe,-abc qwe
			\t -foo file/to/use -bar 4 -b  az not,-foo file/to/use;-bar 4;-b  az not
			""")
	public void testSetMountFlags(String toTest, String expectedRaw) {
		var expected = Arrays.stream(expectedRaw.split(";"));

		inTest.setMountFlags(toTest);
		expected.forEach( exp -> Assertions.assertTrue(inTest.mountFlags.contains(exp)));
	}


	class TestMountBuilder extends AbstractMountBuilder {

		public TestMountBuilder() {
			super(Path.of("abc"));
		}

		@Override
		public Mount mount() throws MountFailedException {
			return null;
		}
	}
}
