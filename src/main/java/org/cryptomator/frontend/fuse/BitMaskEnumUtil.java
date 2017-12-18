package org.cryptomator.frontend.fuse;

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import jnr.constants.Constant;

@PerAdapter
public class BitMaskEnumUtil {

	@Inject
	public BitMaskEnumUtil() {
	}

	public <E extends Enum & Constant> Set<E> bitMaskToSet(Class<E> clazz, long mask) {
		Set<E> result = EnumSet.noneOf(clazz);
		for (E e : clazz.getEnumConstants()) {
			if ((e.longValue() & mask) == e.longValue()) {
				result.add(e);
			}
		}
		return result;
	}

}
