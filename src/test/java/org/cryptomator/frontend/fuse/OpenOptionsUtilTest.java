package org.cryptomator.frontend.fuse;

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import jnr.constants.platform.OpenFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

public class OpenOptionsUtilTest {

	@ParameterizedTest
	@MethodSource("openOptionsProvider")
	public void testOpenFlagsMaskToSet(Set<OpenOption> expectedOptions, Set<OpenFlags> flags) {
		BitMaskEnumUtil enumUtil = Mockito.mock(BitMaskEnumUtil.class);
		Mockito.verifyNoMoreInteractions(enumUtil);
		OpenOptionsUtil util = new OpenOptionsUtil(enumUtil);
		Set<OpenOption> options = util.fuseOpenFlagsToNioOpenOptions(flags);
		Assertions.assertEquals(expectedOptions, options);
	}

	static Stream<Arguments> openOptionsProvider() {
		return Stream.of( //
				Arguments.of(Sets.newHashSet(StandardOpenOption.READ), EnumSet.of(OpenFlags.O_RDONLY)), //
				Arguments.of(Sets.newHashSet(StandardOpenOption.WRITE), EnumSet.of(OpenFlags.O_WRONLY)), //
				Arguments.of(Sets.newHashSet(StandardOpenOption.WRITE), EnumSet.of(OpenFlags.O_WRONLY, OpenFlags.O_RDONLY)), // write wins
				Arguments.of(Sets.newHashSet(StandardOpenOption.READ, StandardOpenOption.WRITE), EnumSet.of(OpenFlags.O_RDWR)), //
				Arguments.of(Sets.newHashSet(StandardOpenOption.READ, StandardOpenOption.WRITE), EnumSet.of(OpenFlags.O_RDWR, OpenFlags.O_WRONLY, OpenFlags.O_RDONLY)), //
				Arguments.of(Sets.newHashSet(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), EnumSet.of(OpenFlags.O_WRONLY, OpenFlags.O_TRUNC)) //
		);
	}

}
