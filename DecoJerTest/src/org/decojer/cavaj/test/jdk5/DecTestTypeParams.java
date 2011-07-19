package org.decojer.cavaj.test.jdk5;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.accessibility.Accessible;

// multiple bounds only with "type parameters", no super, no ?
public class DecTestTypeParams<T extends Integer & Cloneable & Accessible, U>
		extends ArrayList<T> implements List<T> {

	// super only with "type arguments", not multiple bounds
	private Map<? extends T, ? super T> getBothBounds() {
		return null;
	}

	private List<? extends List<U>> getIntegerLowerBound() {
		return null;
	}

	private List<? super List<? extends U>> getIntegerUpperBound() {
		return null;
	}
}