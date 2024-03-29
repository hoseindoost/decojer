/*
 * $Id$
 *
 * This file is part of the DecoJer project.
 * Copyright (C) 2010-2011  André Pankraz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * In accordance with Section 7(b) of the GNU Affero General Public License,
 * a covered work must retain the producer line in every Java Source Code
 * that is created using DecoJer.
 */
package org.decojer.cavaj.model.types;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.decojer.cavaj.model.A;
import org.decojer.cavaj.model.AF;
import org.decojer.cavaj.model.Container;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.Element;
import org.decojer.cavaj.model.fields.ClassF;
import org.decojer.cavaj.model.fields.F;
import org.decojer.cavaj.model.methods.ClassM;
import org.decojer.cavaj.model.methods.M;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Type.
 *
 * @author André Pankraz
 */
public abstract class T implements Element {

	private static int assignKindsToFrom(final int kindTo, final int kindFrom) {
		final int kTo = (kindTo & 0xF) - 1;
		final int kFrom = (kindFrom & 0xF) - 1;
		if (kTo >= 0 && kFrom >= 0) {
			return AUTO_CONVERSION_ASSIGN_REDUCTION_FROM_TO[kFrom][kTo] | kindTo & kindFrom;
		}
		return kindTo & kindFrom;
	}

	@Nonnull
	public static T getDalvikIntT(final int literal) {
		int kinds = T.FLOAT.getKind();
		if (literal == 0) {
			// as REF for const/4 v0, throw v0 (just from Proguard as obfuscated unreachable code?)
			kinds |= T.REF.getKind() | T.BOOLEAN.getKind();
		} else if (literal == 1) {
			kinds |= T.BOOLEAN.getKind();
		}
		if (Character.MIN_VALUE <= literal && literal <= Character.MAX_VALUE) {
			kinds |= T.CHAR.getKind();
		}
		if (Byte.MIN_VALUE <= literal && literal <= Byte.MAX_VALUE) {
			kinds |= T.BYTE.getKind();
		} else if (Short.MIN_VALUE <= literal && literal <= Short.MAX_VALUE) {
			kinds |= T.SHORT.getKind();
		} else {
			kinds |= T.INT.getKind();
		}
		final PrimitiveT ret = getT(kinds);
		assert ret != null;
		return ret;
	}

	@Nonnull
	public static PrimitiveT getJvmIntT(final int literal) {
		int kinds = 0;
		if (literal == 0 || literal == 1) {
			kinds |= T.BOOLEAN.getKind();
		}
		if (Character.MIN_VALUE <= literal && literal <= Character.MAX_VALUE) {
			kinds |= T.CHAR.getKind();
		}
		if (Byte.MIN_VALUE <= literal && literal <= Byte.MAX_VALUE) {
			kinds |= T.BYTE.getKind();
		} else if (Short.MIN_VALUE <= literal && literal <= Short.MAX_VALUE) {
			kinds |= T.SHORT.getKind();
		} else {
			kinds |= T.INT.getKind();
		}
		final PrimitiveT ret = getT(kinds);
		assert ret != null;
		return ret;
	}

	@Nullable
	private static PrimitiveT getT(final int kinds) {
		if (kinds == 0) {
			return null;
		}
		PrimitiveT t = (PrimitiveT) KIND_2_TS.get(kinds);
		if (t != null) {
			return t;
		}
		final StringBuilder sb = new StringBuilder("{");
		for (final Kind k : Kind.values()) {
			if ((kinds & k.getKind()) != 0) {
				sb.append(k.getName()).append(",");
			}
		}
		t = new PrimitiveT(sb.substring(0, sb.length() - 1) + "}", kinds);
		KIND_2_TS.put(kinds, t);
		return t;
	}

	@Nonnull
	private static PrimitiveT getT(final Kind kind) {
		PrimitiveT t = (PrimitiveT) KIND_2_TS.get(kind.getKind());
		if (t != null) {
			return t;
		}
		t = new PrimitiveT(kind.getName(), kind.getKind());
		KIND_2_TS.put(kind.getKind(), t);
		return t;
	}

	@Nonnull
	private static PrimitiveT getT(final Kind... kinds) {
		// don't use types as input, restrict to kind-types
		int flags = 0;
		for (final Kind k : kinds) {
			flags |= k.getKind();
		}
		final PrimitiveT t = getT(flags);
		assert t != null;
		return t;
	}

	/**
	 * Intersect types: Find common super type. Use AND operation for kind - primitive multitypes:
	 * no conversion. No primitive reduction of source types, done through eventual following
	 * register read. Resulting reference type contains one class and multiple interfaces.
	 *
	 * If type is yet unknown, leave name empty.
	 *
	 * @param t1
	 *            type 1
	 * @param t2
	 *            type 2
	 * @return merged type
	 */
	@Nullable
	public static T intersect(@Nullable final T t1, @Nullable final T t2) {
		if (t1 == null || t2 == null) {
			return null;
		}
		if (t1.equals(t2)) {
			return t1;
		}
		final int kind = intersectKinds(t1.getKind(), t2.getKind());
		if (kind == 0) {
			if (t1.isAInt() && t2.isAInt()) {
				// TODO could also return AINT or temporary IntersectT?!
				return T.INT;
			}
			return null;
		}
		if ((kind & Kind.REF.getKind()) == 0) {
			return getT(kind);
		}
		if (t1 == T.REF) {
			return t2;
		}
		if (t2 == T.REF) {
			return t1;
		}
		if (t1.isAssignableFrom(t2)) {
			return t1;
		}
		if (t2.isAssignableFrom(t1)) {
			return t2;
		}
		if (t1.isArray() && t2.isArray()) {
			final T componentT1 = t1.getComponentT();
			final T componentT2 = t2.getComponentT();
			assert componentT1 != null : t1;
			assert componentT2 != null : t2;
			// covariant arrays, but super/interfaces are {Object,Cloneable,Serializable}, not
			// {superXY}[], and it doesn't work for primitives because no auto-conversion!
			if ((componentT1.getKind() & componentT2.getKind()) != 0) {
				final T intersectT = intersect(componentT1, componentT2);
				if (intersectT != null) {
					return t1.getDu().getArrayT(intersectT);
				}
			}
			// could fall through here to general algorithm, but following is always same result
			// TODO getDu().getArrayIntersection?, also in TestT?
			return new IntersectionT(t1.getDu().getObjectT(), t1.getDu().getArrayInterfaceTs());
		}
		// find common supertypes, raise in t1-hierarchy till assignable from t2
		T superT = null;
		final List<T> interfaceTs = Lists.newArrayList();
		final LinkedList<T> ts = Lists.newLinkedList();
		ts.add(t1);
		while (!ts.isEmpty()) {
			final T iT = ts.pollFirst();
			if (superT == null) {
				superT = iT.getSuperT();
				if (superT == null) {
					// TODO hmm really?
					if (iT.isUnresolvable()) {
						superT = T.REF;
					} else {
						superT = t1.getDu().getObjectT();
					}
				} else {
					if (!superT.isAssignableFrom(t2)) {
						ts.add(superT);
						superT = null;
					}
				}
			}
			for (final T interfaceT : iT.getInterfaceTs()) {
				if (interfaceT.isAssignableFrom(t2)) {
					if (!interfaceTs.contains(interfaceT)) {
						interfaceTs.add(interfaceT);
					}
				} else {
					ts.add(interfaceT);
				}
			}
		}
		if (interfaceTs.isEmpty()) {
			return superT;
		}
		if (interfaceTs.size() == 1 && (superT == null || superT.isObject())) {
			return interfaceTs.get(0);
		}
		return new IntersectionT(superT, interfaceTs.toArray(new T[interfaceTs.size()]));
	}

	private static int intersectKinds(final int kind1, final int kind2) {
		final int k1 = (kind1 & 0xF) - 1;
		final int k2 = (kind2 & 0xF) - 1;
		if (k1 >= 0 && k2 >= 0) {
			return AUTO_CONVERSION_INTERSECT_REDUCTION[k1][k2] | kind1 & kind2;
		}
		return kind1 & kind2;
	}

	private static boolean isAsciiDigit(final char c) {
		return '0' <= c && c <= '9';
	}

	/**
	 * Merge/union read/down/or types: Find common lower type. Use OR operation for kind.
	 *
	 * If type is yet unknown, leave name empty.
	 *
	 * @param t1
	 *            type 1
	 * @param t2
	 *            type 2
	 * @return merged type
	 */
	@Nullable
	public static T union(@Nullable final T t1, @Nullable final T t2) {
		if (t1 == null) {
			return t2;
		}
		if (t2 == null) {
			return t1;
		}
		if (t1.equals(t2)) {
			return t1;
		}
		final int kind = t1.getKind() | t2.getKind();
		if ((kind & Kind.REF.getKind()) == 0) {
			return getT(kind);
		}

		if (t1.getDu() == null) {
			return t2;
		}
		if (t2.getDu() == null) {
			return t1;
		}
		// TODO...bottom merge ref
		return t1;
	}

	@Nonnull
	private static final Map<Integer, T> KIND_2_TS = Maps.newHashMap();
	@Nonnull
	@SuppressWarnings("null")
	private static final Map<String, Object> MEMBER_NONE = Collections
			.unmodifiableMap(Maps.<String, Object> newHashMap());
	/**
	 * Java allows the automatic type conversion for primitives. For union-primitives we can reduce
	 * the possible from-types in case of assignments:<br>
	 * __s_ read ___i => __s_<br>
	 * __s_ read __si => __s_<br>
	 * __si read __s_ => __s_<br>
	 * __s_ read _b__ => ____<br>
	 * _bsi read c__i => _bsi
	 */
	@Nonnull
	private static int[][] AUTO_CONVERSION_ASSIGN_REDUCTION_FROM_TO = {
			/* ___i */{ 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1 },
			/* __s_ */{ 2, 2, 2, 0, 2, 2, 2, 0, 2, 2, 2, 0, 2, 2, 2 },
			/* __si */{ 3, 2, 3, 0, 3, 2, 3, 0, 3, 2, 3, 0, 3, 2, 3 },
			/* _b__ */{ 4, 4, 4, 4, 4, 4, 4, 0, 4, 4, 4, 4, 4, 4, 4 },
			/* _b_i */{ 5, 4, 5, 4, 5, 4, 5, 0, 5, 4, 5, 4, 5, 4, 5 },
			/* _bs_ */{ 6, 6, 6, 4, 6, 6, 6, 0, 6, 6, 6, 4, 6, 6, 6 },
			/* _bsi */{ 7, 6, 7, 4, 7, 6, 7, 0, 7, 6, 7, 4, 7, 6, 7 },
			/* c___ */{ 8, 0, 8, 0, 8, 0, 8, 8, 8, 8, 8, 8, 8, 8, 8 },
			/* c__i */{ 9, 0, 9, 0, 9, 0, 9, 8, 9, 8, 9, 8, 9, 8, 9 },
			/* c_s_ */{ 10, 2, 10, 0, 10, 2, 10, 8, 10, 10, 10, 8, 10, 10, 10 },
			/* c_si */{ 11, 2, 11, 0, 11, 2, 11, 8, 11, 10, 11, 8, 11, 10, 11 },
			/* cb__ */{ 12, 4, 12, 4, 12, 4, 12, 8, 12, 12, 12, 12, 12, 12, 12 },
			/* cb_i */{ 13, 4, 13, 4, 13, 4, 13, 8, 13, 12, 13, 12, 13, 12, 13 },
			/* cbs_ */{ 14, 6, 14, 4, 14, 6, 14, 8, 14, 14, 14, 12, 14, 14, 14 },
			/* cbsi */{ 15, 6, 15, 4, 15, 6, 15, 8, 15, 14, 15, 12, 15, 14, 15 } };

	/**
	 * Java allows the automatic type conversion for primitives. For union-primitives we can reduce
	 * the possible types in case of intersections:<br>
	 */
	@Nonnull
	private static int[][] AUTO_CONVERSION_INTERSECT_REDUCTION = {
			/* ___i */{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			/* __s_ */{ 1, 2, 3, 2, 3, 2, 2, 1, 1, 3, 3, 3, 3, 3, 3 },
			/* __si */{ 1, 3, 3, 3, 3, 3, 3, 1, 1, 3, 3, 3, 3, 3, 3 },
			/* _b__ */{ 1, 2, 3, 4, 5, 6, 1, 1, 1, 3, 3, 5, 5, 7, 7 },
			/* _b_i */{ 1, 3, 3, 5, 5, 7, 1, 1, 1, 3, 3, 5, 5, 7, 7 },
			/* _bs_ */{ 1, 2, 3, 6, 7, 7, 1, 1, 1, 3, 3, 7, 7, 7, 7 },
			/* _bsi */{ 1, 3, 3, 7, 7, 7, 7, 1, 1, 3, 3, 7, 7, 7, 7 },
			/* c___ */{ 1, 1, 1, 1, 1, 1, 1, 8, 9, 9, 9, 9, 9, 9, 9 },
			/* c__i */{ 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9 },
			/* c_s_ */{ 1, 3, 3, 3, 3, 3, 3, 9, 9, 11, 11, 11, 11, 11, 11 },
			/* c_si */{ 1, 3, 3, 3, 3, 3, 3, 9, 9, 11, 11, 11, 11, 11, 11 },
			/* cb__ */{ 1, 3, 3, 5, 5, 7, 7, 9, 9, 11, 11, 13, 13, 15, 15 },
			/* cb_i */{ 1, 3, 3, 5, 5, 7, 7, 9, 9, 11, 11, 13, 13, 15, 15 },
			/* cbs_ */{ 1, 3, 3, 7, 7, 7, 7, 9, 9, 11, 11, 15, 15, 15, 15 },
			/* cbsi */{ 1, 3, 3, 7, 7, 7, 7, 9, 9, 11, 11, 15, 15, 15, 15 } };
	/**
	 * Primitive type int.
	 */
	@Nonnull
	public static T INT = getT(Kind.INT);
	/**
	 * Primitive type short.
	 */
	@Nonnull
	public static T SHORT = getT(Kind.SHORT);
	/**
	 * Primitive type byte.
	 */
	@Nonnull
	public static T BYTE = getT(Kind.BYTE);
	/**
	 * Primitive type char.
	 */
	@Nonnull
	public static T CHAR = getT(Kind.CHAR);

	/**
	 * Primitive type boolean.
	 */
	@Nonnull
	public static T BOOLEAN = getT(Kind.BOOLEAN);
	/**
	 * Primitive type float.
	 */
	@Nonnull
	public static T FLOAT = getT(Kind.FLOAT);

	/**
	 * Primitive type long.
	 */
	@Nonnull
	public static T LONG = getT(Kind.LONG);
	/**
	 * Primitive type double.
	 */
	@Nonnull
	public static T DOUBLE = getT(Kind.DOUBLE);

	/**
	 * Primitive type void.
	 */
	@Nonnull
	public static T VOID = getT(Kind.VOID);
	/**
	 * Artificial type 'reference'.
	 */
	@Nonnull
	public static T REF = getT(Kind.REF);
	/**
	 * Artificial type 'return address' for JSR follow pc.
	 *
	 * Spec: No return address (a value of type returnAddress) may be loaded from a local variable.
	 */
	@Nonnull
	public static final T RET = getT(Kind.RET);
	/**
	 * Artificial type long part 2.
	 */
	@Nonnull
	public static T LONG2 = getT(Kind.LONG2);

	/**
	 * Artificial type double part 2.
	 */
	@Nonnull
	public static T DOUBLE2 = getT(Kind.DOUBLE2);

	/**
	 * Artificial type 'none'.
	 */
	@Nonnull
	public static T NONE = getT(Kind.NONE);

	/**
	 * Multi-type 'any (for array check)'.
	 */
	@Nonnull
	public static T ANY = getT(Kind.REF, Kind.INT, Kind.SHORT, Kind.BYTE, Kind.CHAR, Kind.BOOLEAN,
			Kind.FLOAT, Kind.LONG, Kind.DOUBLE);

	/**
	 * Multi-type 'any reference'.
	 */
	@Nonnull
	public static T AREF = getT(Kind.REF, Kind.RET);

	/**
	 * Multi-type 'any JVM int'.
	 */
	@Nonnull
	public static T AINT = getT(Kind.INT, Kind.SHORT, Kind.BYTE, Kind.CHAR, Kind.BOOLEAN);

	/**
	 * Multi-type 'any JVM int or ref (for null-checks)'.
	 */
	@Nonnull
	public static T AINTREF = getT(Kind.REF, Kind.INT, Kind.SHORT, Kind.BYTE, Kind.CHAR,
			Kind.BOOLEAN);

	/**
	 * Multi-type 'primitive'.
	 */
	@Nonnull
	public static T PRIMITIVE = getT(Kind.INT, Kind.SHORT, Kind.BYTE, Kind.CHAR, Kind.BOOLEAN,
			Kind.FLOAT, Kind.LONG, Kind.DOUBLE, Kind.VOID);

	/**
	 * Multi-type 'any small (8 bit)'.
	 */
	@Nonnull
	public static T SMALL = getT(Kind.BYTE, Kind.BOOLEAN);

	/**
	 * Multi-type 'any single (32 bit)'.
	 */
	@Nonnull
	public static T SINGLE = getT(Kind.INT, Kind.SHORT, Kind.BYTE, Kind.CHAR, Kind.BOOLEAN,
			Kind.FLOAT);

	/**
	 * Multi-type 'any wide (64 bit)'.
	 */
	@Nonnull
	public static T WIDE = getT(Kind.LONG, Kind.DOUBLE);

	/**
	 * Translation from JVM Array Opcodes: T_BOOLEAN = 4 to T_LONG = 11.
	 */
	@Nonnull
	public static final T[] TYPES = new T[] { null, null, null, null, T.BOOLEAN, T.CHAR, T.FLOAT,
			T.DOUBLE, T.BYTE, T.SHORT, T.INT, T.LONG };

	@Nonnull
	public static final T[] INTERFACES_NONE = new T[0];

	@Nonnull
	public static final T[] TYPE_ARGS_NONE = new T[0];

	@Nonnull
	public static final T[] TYPE_PARAMS_NONE = new T[0];

	/**
	 * Assign to given type. There are 3 possible outcomes: Cannot assign to given type, which
	 * returns {@code null}. Can assign to given type and primitive multitype reduction, which
	 * returns the reduced new type. Can assign without reduction, which returns unmodified
	 * {@code this}.
	 *
	 * @param t
	 *            assign to type
	 * @return {@code null} or reduced type or {@code this}
	 */
	@Nullable
	public T assignTo(@Nullable final T t) {
		if (t == null) {
			return null;
		}
		final int kind = assignKindsToFrom(t.getKind(), getKind());
		if (kind == 0) {
			if (isAInt() && t.isAInt()) {
				// don't reduce here
				return this;
			}
			return null;
		}
		if ((kind & Kind.REF.getKind()) == 0) {
			// reduced kind if primitive
			return getT(kind);
		}
		// null primitives are T.REF and should change their type to something meaningful,
		// but don't change VarT or to {T.REF, T.RET} etc.
		if (this instanceof PrimitiveT && !(t instanceof PrimitiveT)) {
			return t;
		}
		// no type reduction, can assign types to different single supertypes / interfaces
		if (t.isAssignableFrom(this)) {
			return this;
		}
		if (isAssignableFrom(t)) {
			// TODO many stuff will be checked at runtime, only rare class cases are explicitely not
			// possible! try to identify and classify such cases. Move method to R?
			// We have to add casting for Java, e.g. with CastT()
			// same for BOOL primitives etc.?
			// works: Comparable c = (Comparable) new Object();
			// works: Integer c = (Integer) new Object();
			// works: Set set = new HashSet(); Serializable s = (Serializable) set;
			return this;
		}
		return this;
	}

	/**
	 * Create type declaration for this type.
	 *
	 * @return type declaration
	 */
	public abstract boolean createTd();

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof T)) {
			return false;
		}
		if (getName().equals(((T) obj).getName())) {
			return true;
		}
		return false;
		// 'Lcom/google/common/collect/ImmutableList<TE;>.SubList;' to type
		// 'com.google.common.collect.ImmutableList$SubList'
		// FIXME currently this are ClassT without parent/enclosing...whats correct?
		// return getName().replaceAll("<[^>]+>", "").equals(((T) obj).getName());
	}

	/**
	 * Is extended signature compatible to given type?
	 *
	 * This function also makes changes to the types, especially it is setting raw types for type
	 * variables (see {@link VarT#eraseTo(T)}).
	 *
	 * JVM Spec: "A class type signature gives complete type information for a class or interface
	 * type. The class type signature must be formulated such that it can be reliably mapped to the
	 * binary name of the class it denotes by erasing any type arguments and converting each '.'
	 * character in the signature to a '$' character."
	 *
	 * The char '.' is already replaced by '$' in the name, '.' is used for folders instead of '/'.
	 *
	 * @param t
	 *            raw type
	 * @return {@code true} - is extended signature type for given type
	 */
	public boolean eraseTo(final T t) {
		return equals(t);
	}

	@Override
	public boolean getAf(final AF af) {
		return false;
	}

	@Nullable
	@Override
	public A[] getAs() {
		return null;
	}

	/**
	 * Get bound type for wildcard type.
	 *
	 * @return bound type for wildcard type
	 */
	@Nullable
	public T getBoundT() {
		return null;
	}

	/**
	 * Get component type of array type.
	 *
	 * @return component type of array type
	 *
	 * @see Class#isAssignableFrom(Class)
	 */
	@Nullable
	public T getComponentT() {
		return null;
	}

	/**
	 * Get dimensions of array type (0 if no array type).
	 *
	 * @return dimensions of array type (0 if no array type)
	 */
	public int getDimensions() {
		return 0;
	}

	@Override
	public DU getDu() {
		return null;
	}

	/**
	 * Get element type of array type (null if no array type).
	 *
	 * @return element type of array type (null if no array type)
	 */
	@Nullable
	public T getElementT() {
		return null;
	}

	/**
	 * Get enclosing method (including constructor).
	 *
	 * @return enclosing method
	 *
	 * @see ClassT#setEnclosingT(T)
	 * @see Class#getEnclosingMethod()
	 * @see Class#getEnclosingConstructor()
	 */
	@Nullable
	public M getEnclosingM() {
		return null; // overwrite in ClassT
	}

	/**
	 * Get outest enclosing (root) type.
	 *
	 * @return outest enclosing (root) type
	 */
	@Nonnull
	public T getEnclosingRootT() {
		T findT = this;
		for (T enclosingT = findT.getEnclosingT(); enclosingT != null; enclosingT = findT
				.getEnclosingT()) {
			findT = enclosingT;
		}
		return findT;
	}

	/**
	 * Get enclosing type.
	 *
	 * @return enclosing type
	 *
	 * @see T#setEnclosingT(T)
	 * @see Class#getEnclosingClass()
	 */
	@Nullable
	public T getEnclosingT() {
		return null; // overwrite in ClassT
	}

	/**
	 * Get enclosing type path from front to end.
	 *
	 * @return enclosing type path from front to end
	 */
	@Nonnull
	public T[] getEnclosingTs() {
		final List<T> enclosingTs = Lists.newArrayList();
		for (T enclosingT = getEnclosingT(); enclosingT != null; enclosingT = enclosingT
				.getEnclosingT()) {
			enclosingTs.add(0, enclosingT);
		}
		enclosingTs.add(this);
		final T[] ret = enclosingTs.toArray(new T[enclosingTs.size()]);
		assert ret != null : this;
		return ret;
	}

	/**
	 * Get field.<br>
	 * Unique identifier in JVM is: "name + descriptor" ({@link F})<br>
	 * Even though the Java language has the field name as unique identifier, obfuscated code could
	 * utilize the same name for different descriptors (see e.g. ojdbc6.jar).
	 *
	 * @param name
	 *            field name
	 * @param desc
	 *            field descriptor
	 * @return field
	 */
	@Nonnull
	public F getF(@Nonnull final String name, @Nonnull final String desc) {
		final String handle = name + ":" + desc;
		F f = (F) getMember().get(handle);
		if (f == null) {
			f = new ClassF(this, name, desc);
			getMember().put(handle, f);
		}
		return f;
	}

	/**
	 * Get the full name, including modifiers like generic arguments, parameterization, type
	 * annotations.
	 *
	 * @return full name
	 */
	@Nonnull
	public String getFullName() {
		return getName();
	}

	/**
	 * Get generic type for parameterized type.
	 *
	 * @return generic type for parameterized type
	 */
	@Nullable
	public T getGenericT() {
		return null;
	}

	/**
	 * Get inner name. Can derive for JVM > 5 from type names (compatibility rules), but not before.
	 * <br>
	 * <br>
	 * According to JLS3 "Binary Compatibility" (13.1) the binary name of non-package classes (not
	 * top level) is the binary name of the immediately enclosing class followed by a '$' followed
	 * by:<br>
	 * (for nested and inner classes): the simple name.<br>
	 * (for local classes): 1 or more digits followed by the simple name.<br>
	 * (for anonymous classes): 1 or more digits.<br>
	 * <br>
	 *
	 * JVM 5: {@code org.decojer.cavaj.test.DecTestInner$$Inner1$$$$_Inner$1$1AInner2}<br>
	 * Before JVM 5: {@code org.decojer.cavaj.test.DecTestInner$$1$AInner2}
	 *
	 * @return inner name
	 * @see Class#getSimpleName()
	 */
	@Nullable
	public String getInnerName() {
		return null; // overwrite in ClassT
	}

	/**
	 * Get interface types.
	 *
	 * @return interface types
	 *
	 * @see Class#getInterfaces()
	 */
	public abstract T[] getInterfaceTs();

	/**
	 * Get kind.
	 *
	 * @return kind
	 */
	public int getKind() {
		return Kind.REF.getKind(); // overwrite im PrimitiveT
	}

	/**
	 * Get method.<br>
	 * Unique identifier in JVM and Java language is: "name + descriptor"
	 *
	 * @param name
	 *            method name
	 * @param desc
	 *            method descriptor
	 * @return method
	 */
	@Nonnull
	public M getM(@Nonnull final String name, @Nonnull final String desc) {
		final String handle = name + desc;
		M m = (M) getMember().get(handle);
		if (m == null) {
			m = new ClassM(this, name, desc);
			getMember().put(handle, m);
		}
		return m;
	}

	/**
	 * Get members (methods, fields).
	 *
	 * @return members
	 */
	@Nonnull
	public Map<String, Object> getMember() {
		return MEMBER_NONE;
	}

	/**
	 * Get package name.
	 *
	 * @return package name or {@code null} for no package
	 */
	@Nullable
	public String getPackageName() {
		final String name = getName();
		final int pos = name.lastIndexOf('.');
		if (pos < 0) {
			return null;
		}
		return name.substring(0, pos);
	}

	/**
	 * Get primary name.
	 *
	 * @return primary name
	 */
	@Nonnull
	public String getPName() {
		final String name = getName();
		if (name.startsWith("{")) {
			return name;
		}
		final String packageName = getPackageName();
		if (packageName == null) {
			return name;
		}
		if (packageName.length() == 0) {
			return name;
		}
		final String ret = name.substring(packageName.length() + 1);
		assert ret != null : this;
		return ret;
	}

	/**
	 * Get qualifier type.
	 *
	 * @return qualifier type
	 */
	@Nullable
	public T getQualifierT() {
		if (isStatic()) {
			return null;
		}
		return getEnclosingT();
	}

	/**
	 * Get qualifier type path from front to end.
	 *
	 * @return qualifier type path from front to end
	 */
	public T[] getQualifierTs() {
		final List<T> qualifierTs = Lists.newArrayList();
		for (T qualifierT = getQualifierT(); qualifierT != null; qualifierT = qualifierT
				.getQualifierT()) {
			qualifierTs.add(0, qualifierT);
		}
		qualifierTs.add(this);
		final T[] ret = qualifierTs.toArray(new T[qualifierTs.size()]);
		assert ret != null : this;
		return ret;
	}

	/**
	 * Get raw type for modifying types (annotation, parameterized, variable) or {@code this}.
	 *
	 * @return raw type or {@code this}
	 */
	@Nonnull
	public T getRawT() {
		return this;
	}

	/**
	 * Get simple identifier: Often needed for AST creation, if anonymous don't return empty name
	 * but primary name.
	 *
	 * @return simple identifier
	 */
	@Nonnull
	public String getSimpleIdentifier() {
		final String simpleName = getSimpleName();
		if (simpleName.isEmpty()) {
			return getPName();
		}
		return simpleName;
	}

	/**
	 * Returns the simple name of the underlying class type as given in the source code. Returns an
	 * empty string if the underlying class is anonymous.<br>
	 * <br>
	 * Works for all Java versions, not just for JVM >= 5 like {@code Class.getSimpleName()}.<br>
	 * <br>
	 * This is heavily dependant from {@link #getEnclosingT()}, which can be {@code null} for
	 * obfuscate code! Hence this is not a reliable answer.
	 *
	 * @return simple name
	 * @see Class#getSimpleName()
	 * @see T#getInnerName()
	 */
	@Nonnull
	public String getSimpleName() {
		// The original Class-Function doesn't work for JVM < 5 because the naming rules changed,
		// different solution here with inner name info
		String innerName = getInnerName();
		if (innerName == null || innerName.isEmpty()) {
			final T enclosingT = getEnclosingT();
			if (enclosingT == null) {
				// top level class, strip the package name
				return getPName();
			}
			innerName = getName().substring(enclosingT.getName().length() + 1);
		}
		// Remove leading "[0-9]*" from the name
		final int length = innerName.length();
		int index = 0;
		while (index < length && isAsciiDigit(innerName.charAt(index))) {
			index++;
		}
		// Eventually, this is the empty string iff this is an anonymous class
		final String ret = innerName.substring(index);
		assert ret != null : this;
		return ret;
	}

	/**
	 * Get source file name (from source file attribute).
	 *
	 * @return source file name
	 */
	@Nullable
	public String getSourceFileName() {
		return null;
	}

	/**
	 * Get stack size.
	 *
	 * @return stack size
	 */
	public int getStackSize() {
		return 1; // overwrite in PrimitiveT for wides
	}

	/**
	 * Get super type.
	 *
	 * @return super type, can be {@code null} for {@code Object}, interfaces and primitives
	 *
	 * @see Class#getSuperclass()
	 */
	@Nullable
	public abstract T getSuperT();

	@Nonnull
	@Override
	public T getT() {
		return this;
	}

	/**
	 * Get type arguments for parameterized types.
	 *
	 * @return type arguments for parameterized types
	 */
	@Nonnull
	public T[] getTypeArgs() {
		return TYPE_ARGS_NONE;
	}

	/**
	 * Get type parameters.
	 *
	 * @return type parameters
	 * @see Class#getTypeParameters()
	 */
	@Nonnull
	public T[] getTypeParams() {
		return TYPE_PARAMS_NONE;
	}

	/**
	 * Get class file version.
	 *
	 * 1.0: 45.0, 1.1: 45.3, 1.2: 46, 1.3: 47, 1.4: 48, 5: 49, 6: 50, 7: 51, 8: 52
	 *
	 * JDK 1.2 and 1.3 creates versions 1.1 if no target option given. JDK 1.4 creates 1.2 if no
	 * target option given.
	 *
	 * @return class file version
	 */
	public int getVersion() {
		return -1;
	}

	@Override
	public int hashCode() {
		return getName().hashCode();
	}

	/**
	 * Is type of class?
	 *
	 * @param klass
	 *            class
	 * @return {@code true} - type is of class
	 */
	public boolean is(final Class<?> klass) {
		return getName().equals(klass.getName());
	}

	/**
	 * Is type of types?
	 *
	 * @param ts
	 *            types
	 * @return {@code true} - type is of types
	 */
	public boolean is(final T... ts) {
		if (isMulti()) {
			for (final T t : ts) {
				if ((getKind() & t.getKind()) == 0) {
					return false;
				}
			}
			return true;
		}
		return equals(ts[0]);
	}

	/**
	 * Is any int (JVM).
	 *
	 * @return {@code true} - is any int
	 */
	private boolean isAInt() {
		return (getKind() & 0x1F) != 0;
	}

	/**
	 * Is (or has) type annotation?
	 *
	 * @return {@code true} - is type annotation
	 */
	public boolean isAnnotated() {
		return false; // overwrite in AnnotT
	}

	/**
	 * Returns {@code true} if and only if the underlying class is an anonymous class.<br>
	 * <br>
	 * This is heavily dependant from {@link #getEnclosingT()}, which can be {@code null} for
	 * obfuscate code! Hence this is not a reliable answer.
	 *
	 * @return {@code true} if and only if this class is an anonymous class.
	 *
	 * @see Class#isAnonymousClass()
	 */
	public boolean isAnonymous() {
		return getSimpleName().isEmpty();
	}

	/**
	 * Is array type?
	 *
	 * @return {@code true} - is array type
	 */
	public boolean isArray() {
		return false; // overwrite in ArrayT
	}

	/**
	 * Is this type instance assignable from given class?
	 *
	 * @param klass
	 *            class
	 * @return {@code true} - is assignable
	 *
	 * @see Class#isAssignableFrom(Class)
	 */
	public boolean isAssignableFrom(@Nonnull final Class<?> klass) {
		return isAssignableFrom(getDu().getT(klass));
	}

	/**
	 * Is this type instance assignable from given type instance?
	 *
	 * Attention: Doesn't work for primitives implicit conversion (byte 2 short 2 int, char 2 int).
	 *
	 * @param t
	 *            type
	 * @return {@code true} - is assignable
	 *
	 * @see Class#isAssignableFrom(Class)
	 */
	public boolean isAssignableFrom(@Nullable final T t) {
		if (t == null) {
			return false;
		}
		if (equals(t.getRawT())) {
			return true;
		}
		final int kind = assignKindsToFrom(getKind(), t.getKind());
		if ((kind & Kind.REF.getKind()) == 0) {
			return kind != 0;
		}
		// assignableFrom(T.REF) is true, null is T.REF!
		// may be better to check for null const in R instead of this general answer
		if (getDu() == null || isObject() || t.getDu() == null) {
			return true;
		}
		// raise in t-hierarchy till equals to this type
		final LinkedList<T> ts = Lists.newLinkedList();
		ts.add(t);
		while (!ts.isEmpty()) {
			final T iT = ts.pollFirst();
			final T superT = iT.getSuperT();
			if (superT == null) {
				if (iT.isUnresolvable()) {
					return true;
				}
			} else {
				if (equals(superT.getRawT())) {
					return true;
				}
				ts.add(superT);
			}
			for (final T interfaceT : iT.getInterfaceTs()) {
				if (equals(interfaceT.getRawT())) {
					return true;
				}
				ts.add(interfaceT);
			}
		}
		return false;
	}

	/**
	 * Is this version at least of the given version?
	 *
	 * @param version
	 *            version
	 * @return {@code true} - at least of given version
	 */
	public abstract boolean isAtLeast(final Version version);

	/**
	 * Is this version less then given version?
	 *
	 * @param version
	 *            version
	 * @return {@code true} - less then given version
	 */
	public abstract boolean isBelow(final Version version);

	/**
	 * Is Dalvik?
	 *
	 * @return {@code true} - is Dalvik
	 */
	public abstract boolean isDalvik();

	@Override
	public boolean isDeclaration() {
		return false;
	}

	/**
	 * Is enum type?
	 *
	 * @return {@code true} - is enum type
	 */
	public boolean isEnum() {
		return false;
	}

	/**
	 * Is inner type?
	 *
	 * See JLS: 15.9.2 Determining Enclosing Instances
	 *
	 * @return {@code true}- is inner type
	 */
	public boolean isInner() {
		if (isStatic()) {
			return false;
		}
		final M enclosingM = getEnclosingM();
		if (enclosingM != null) {
			if (enclosingM.isStatic()) {
				return false;
			}
		} else {
			final T enclosingT = getEnclosingT();
			if (enclosingT == null || enclosingT.isStatic()) {
				return false;
			}
		}
		// isNested() is true here
		return true;
	}

	/**
	 * Is interface?
	 *
	 * @return {@code true} - is interface
	 */
	public boolean isInterface() {
		return false; // only class types can be interface types, overwrite in ClassT / ModT
	}

	/**
	 * Is intersection type?
	 *
	 * @return {@code true} - is intersection type
	 */
	public boolean isIntersection() {
		return false; // overwrite in IntersectionT
	}

	/**
	 * Is multi type?
	 *
	 * @return {@code true} - is multi type
	 */
	public boolean isMulti() {
		return false; // only primitive types can be multi types, overwrite in PrimitiveT
	}

	/**
	 * Is nested type? Includes none-static inner types.
	 *
	 * @return {@code true}- is nested type
	 *
	 * @see T#getEnclosingT()
	 */
	public boolean isNested() {
		return getEnclosingT() != null;
	}

	/**
	 * Is Object type?
	 *
	 * @return {@code true} - is Object type
	 */
	public boolean isObject() {
		return false; // only class types can be Object type, overwrite in ClassT
	}

	/**
	 * Is parameterized type?
	 *
	 * @return {@code true} - is parameterized type
	 */
	public boolean isParameterized() {
		return false; // overwrite in ParamT
	}

	/**
	 * Is primitive (not including REF, RET etc.)?
	 *
	 * @return {@code true} - is primitive (not including REF, RET etc.)
	 */
	public boolean isPrimitive() {
		return false; // overwrite in PrimitiveT
	}

	/**
	 * Is qualified type?
	 *
	 * @return {@code true} - is qualified type
	 */
	public boolean isQualified() {
		return false; // overwrite in QualifiedT
	}

	/**
	 * Is reference type (includes array type, parameterized type)?
	 *
	 * @return {@code true} - is reference type
	 */
	public boolean isRef() {
		return true; // only primitive types can not be references, overwrite in PrimitiveT
	}

	@Override
	public boolean isStatic() {
		return true;
	}

	/**
	 * Is subclass - for wildcard types?
	 *
	 * @return {@code true} - is subclass - for wildcard types
	 */
	public boolean isSubclassOf() {
		return false;
	}

	@Override
	public boolean isSynthetic() {
		return false; // overwrite in ClassT
	}

	/**
	 * Is unresolveable?
	 *
	 * @return {@code true} - is unresolveable
	 */
	public boolean isUnresolvable() {
		return false;
	}

	/**
	 * Is var type?
	 *
	 * @return {@code true} - is var type
	 */
	public boolean isVar() {
		return false; // overwrite in VarT
	}

	/**
	 * Is wide type?
	 *
	 * @return {@code true} - is wide type
	 */
	public boolean isWide() {
		return false; // only primitive types can be wide, overwrite in PrimitiveT
	}

	/**
	 * Is wildcard type?
	 *
	 * @return {@code true} - is wildcard type
	 */
	public boolean isWildcard() {
		return false; // overwrite in WildcardT
	}

	/**
	 * Resolve unfilled parameters.
	 */
	public abstract void resolve();

	@Override
	public void setAccessFlags(final int accessFlags) {
		assert false : this; // overwrite in ClassT
	}

	@Override
	public void setAf(@Nonnull final AF... af) {
		assert false : this;
		return;
	}

	/**
	 * Set bound type for wildcard type.
	 *
	 * For annotation application.
	 *
	 * @param boundT
	 *            bound type for wildcard type
	 */
	public void setBoundT(final T boundT) {
		assert false : this;
	}

	/**
	 * Set component type for array type.
	 *
	 * For annotation application.
	 *
	 * @param componentT
	 *            component type for array type
	 */
	public void setComponentT(final T componentT) {
		assert false : this;
	}

	@Override
	public void setDeclarationOwner(@Nonnull final Container declarationOwner) {
		assert false : this;
	}

	@Override
	public void setDeprecated() {
		assert false : this; // overwrite in ClassT
	}

	/**
	 * Set enclosing method (since JVM 5).
	 *
	 * @param enclosingM
	 *            enclosing method
	 *
	 * @see ClassT#setEnclosingT(T)
	 */
	public void setEnclosingM(final M enclosingM) {
		assert false : this;
	}

	/**
	 * Set enclosing class type (since JVM 5).
	 *
	 * There are five kinds of classes (or interfaces):<br>
	 *
	 * a) Top level classes<br>
	 * b) Nested classes (static member classes)<br>
	 * c) Inner classes (non-static member classes)<br>
	 * d) Local classes (named classes declared within a method)<br>
	 * e) Anonymous classes<br>
	 *
	 * JVM Spec 4.8.6: A class must have an EnclosingMethod attribute if and only if it is a local
	 * class or an anonymous class.<br>
	 *
	 * We mix declaring classes info and enclosing method / classes attribut info.<br>
	 *
	 * JVM 5 has enclosing method attribute for local/anonymous, outer info only for declaring outer
	 * <br>
	 * JVM < 5 has no enclosing method attribute and:<br>
	 * JVM 1.1 has normal outer info for anonymous/local, like declaring for JVM 5,<br>
	 * JVM 1.2 .. 1.4 has no outer info at all,<br>
	 * obfuscated code could also strip all these info!!!
	 *
	 * We can not ignore this information and rely on naming rules alone, because the separator '$'
	 * is a valid character in none-inner type names. If we don't have this info, we need to check
	 * the existence of the other types by other means.
	 *
	 * @param enclosingT
	 *            enclosing type
	 *
	 * @see Class#getEnclosingClass()
	 */
	public void setEnclosingT(@Nullable final T enclosingT) {
		assert false : this;
	}

	/**
	 * Set inner info.<br>
	 * Inner name: Can derive for JVM > 5 from type names (compatibility rules), but not before.<br>
	 * Inner access flags: Have _exclusively_ modifiers PROTECTED, PRIVATE, STATIC, but not SUPER
	 *
	 * @param name
	 *            inner name
	 * @param accessFlags
	 *            inner access flags
	 * @see T#getInnerName()
	 */
	public void setInnerInfo(final String name, final int accessFlags) {
		assert false : this;
	}

	/**
	 * Type must be an interface or a class (from usage, e.g. invoke/get/set).
	 *
	 * @param f
	 *            {@code true} - is interface
	 * @return {@code true} - success, can fail for wrong signatures etc.
	 */
	public boolean setInterface(final boolean f) {
		assert f == false : this;
		return false;
	}

	/**
	 * Set interface types.
	 *
	 * @param interfaceTs
	 *            interface types
	 */
	public void setInterfaceTs(@Nonnull final T[] interfaceTs) {
		assert false : this;
	}

	/**
	 * Set qualifier type for qualified type.
	 *
	 * For annotation application.
	 *
	 * @param qualifierT
	 *            qualifierd type for qualified type
	 */
	public void setQualifierT(@Nonnull final T qualifierT) {
		assert false : this; // overwrite in QualifiedT
	}

	/**
	 * Set raw type for modified type.
	 *
	 * For type annotation application.
	 *
	 * @param rawT
	 *            raw type for modified type
	 */
	public void setRawT(@Nonnull final T rawT) {
		assert false : this;
	}

	/**
	 * This should be scala code.
	 */
	public abstract void setScala();

	/**
	 * Set source file name (from source file attribute).
	 *
	 * @param sourceFileName
	 *            source file name
	 */
	public abstract void setSourceFileName(final String sourceFileName);

	/**
	 * Set super type.
	 *
	 * @param superT
	 *            super type
	 */
	public void setSuperT(final T superT) {
		assert false : this;
	}

	@Override
	public void setSynthetic() {
		assert false : this; // overwrite in ClassT
	}

	/**
	 * Set class file version.
	 *
	 * 1.0: 45.0, 1.1: 45.3, 1.2: 46, 1.3: 47, 1.4: 48, 5: 49, 6: 50, 7: 51, 8: 52
	 *
	 * JDK 1.2 and 1.3 creates versions 1.1 if no target option given. JDK 1.4 creates 1.2 if no
	 * target option given.
	 *
	 * @param version
	 *            class file version
	 */
	public abstract void setVersion(final int version);

	@Nonnull
	@Override
	public String toString() {
		// getSimpleName() not possible, potentially needs unresolved attributes, e.g. enclosing
		return getFullName();
	}

	/**
	 * Validate qualifier name for enclosings and qualifiers.
	 *
	 * @param qualifierName
	 *            qualifier name
	 * @return {@code true} - valid qualifier name
	 */
	public boolean validateQualifierName(final String qualifierName) {
		final String name = getName();
		return name.length() > qualifierName.length() + 1 && name.startsWith(qualifierName)
				&& name.charAt(qualifierName.length()) == '$';
	}

}