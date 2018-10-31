package org.cryptomator.frontend.fuse;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import jnr.constants.Constant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BitMaskEnumUtilTest {

	@ParameterizedTest
	@MethodSource("argumentsProvider")
	public void testBitMaskToSet(Set<TestEnum> set, long mask) {
		BitMaskEnumUtil util = new BitMaskEnumUtil();
		Set<TestEnum> actual = util.bitMaskToSet(TestEnum.class, mask);
		Assertions.assertEquals(set, actual);
	}

	@ParameterizedTest
	@MethodSource("argumentsProvider")
	public void testSetToBitMask(Set<TestEnum> set, long mask) {
		BitMaskEnumUtil util = new BitMaskEnumUtil();
		long actual = util.setToBitMask(set);
		Assertions.assertEquals(mask, actual);
	}

	static Stream<Arguments> argumentsProvider() {
		return Stream.of( //
				Arguments.of(EnumSet.noneOf(TestEnum.class), 0l), //
				Arguments.of(EnumSet.of(TestEnum.ONE), 1l), //
				Arguments.of(EnumSet.of(TestEnum.TWO), 2l), //
				Arguments.of(EnumSet.of(TestEnum.FOUR), 4l), //
				Arguments.of(EnumSet.of(TestEnum.ONE, TestEnum.TWO), 3l), //
				Arguments.of(EnumSet.allOf(TestEnum.class), 7l) //
		);
	}

	private enum TestEnum implements Constant {
		ONE(0x1l),
		TWO(0x2l),
		FOUR(0x4l);

		private final long value;
		TestEnum(long value) { this.value = value; }
		public final int intValue() { return (int) value; }
		public final long longValue() { return value; }
		public final boolean defined() { return true; }

	}
}
