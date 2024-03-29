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
package org.decojer.cavaj.transformers;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

import org.decojer.DecoJerException;
import org.decojer.cavaj.model.CU;
import org.decojer.cavaj.model.Container;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.Element;
import org.decojer.cavaj.model.code.CFG;
import org.decojer.cavaj.model.code.ops.NEW;
import org.decojer.cavaj.model.code.ops.Op;
import org.decojer.cavaj.model.fields.F;
import org.decojer.cavaj.model.methods.M;
import org.decojer.cavaj.model.types.T;
import org.decojer.cavaj.model.types.Version;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Transformer: Analyze inner classes and create compilation units.
 *
 * @author André Pankraz
 */
@Slf4j
public class TrInnerClassesAnalysis {

	private static void checkBinaryCompatibilityNamingRules(final Collection<T> ts) {
		for (final T t : ts) {
			if (t == null || !t.isDeclaration()) {
				continue;
			}
			// Inner name is not necessary anymore since JVM 5, see T#getInnerName(), but we
			// validate the new "Binary Compatibility" rules here.
			if (t.isBelow(Version.JVM_5) || t.getEnclosingT() == null) {
				continue;
			}
			final String innerName = t.getInnerName();
			final String simpleName = getSimpleClassName(t);
			if (innerName != null && !innerName.isEmpty() && !innerName.equals(simpleName)) {
				log.warn("Inner name '" + innerName + "' for type '" + t
						+ "' is different from enclosing info '" + simpleName + "'!");
			}
		}
	}

	/**
	 * All JVMs < 5 have no enclosing method attribute and wrong (JVM 1) or missing (JVM 2...4)
	 * informations. We are looking for explicit new-ops, this must be the parent method.
	 *
	 * @param ts
	 *            all types
	 */
	private static void findEnclosingMethods(final Collection<T> ts) {
		for (final T t : ts) {
			if (t == null || !t.isDeclaration()) {
				continue;
			}
			for (final Element declaration : t.getDeclarations()) {
				if (!(declaration instanceof M)) {
					continue;
				}
				final M enclosingM = (M) declaration;
				final CFG cfg = enclosingM.getCfg();
				if (cfg == null) {
					continue;
				}
				final Op[] ops = cfg.getOps();
				if (ops == null) {
					continue;
				}
				for (final Op op : ops) {
					if (!(op instanceof NEW)) {
						continue;
					}
					final T newT = ((NEW) op).getT();
					if (t.equals(newT)) {
						// method cannot be enclosing method from it's owner, see
						// scala-library-2.9.1.jar
						continue;
					}
					// TODO the following function is dependant from enclosingT...if this is null,
					// we will never be anonymous! we should repair enclosing info here, overwrite
					// old read info?

					if (!newT.isAnonymous() || !newT.isDeclaration()) {
						continue;
					}
					final M newTenclosingM = newT.getEnclosingM();
					if (newTenclosingM == enclosingM) {
						continue;
					}
					final T newTenclosingT = newT.getEnclosingT();
					if (newTenclosingT == t) {
						continue;
					}
					final Container newTowner = newT.getDeclarationOwner();
					if (newTowner != null) {
						// TODO can happen for each constructor if this is a field value!!!
						if (newTowner instanceof M && ((M) newTowner).isConstructor()) {
							// TODO should link to Ms parent, but might not be linked yet???
							// parallel findTopTds necessary?
							continue;
						}
						if (!t.isScala()) {
							// happens often in scala, remote "...$anonfun$...$1" in other classes
							log.warn("New ananymous type declaration '" + newT
									+ "' already has parent '" + newTowner + "'!");
						}
						continue;
					}
					newT.setDeclarationOwner(enclosingM);
				}
			}
		}
	}

	private static List<T> findTopTs(final Collection<T> ts) {
		final List<T> topTs = Lists.newArrayList();
		// separate all read ts, not just selected ts
		for (final T t : ts) {
			if (t == null || !t.isDeclaration()) {
				continue;
			}
			if (t.isAnonymous()) {
				if (t.getDeclarationOwner() != null) {
					if (!(t.getDeclarationOwner() instanceof M)) {
						log.warn("Parent of inner local/anonymous type '" + t
								+ "' is no method but '" + t.getDeclarationOwner() + "'!");
					}
					continue;
				}
				if (isEnumSwitchMap(t)) {
					// use enclosingT info, should exist
					final T enclosingT = t.getEnclosingT();
					if (enclosingT != null) {
						if (enclosingT.isDeclaration()) {
							t.setDeclarationOwner(enclosingT);
							continue;
						}
					}
					log.warn("No enclosing type info for inner class with Enum Switch Map '" + t
							+ "'!");
				}
				// use existing enclosing info
			}
			// first check enclosing method, potentially deeper nested than in type
			final M enclosingM = t.getEnclosingM();
			if (enclosingM != null) {
				if (enclosingM.isDeclaration()) {
					t.setDeclarationOwner(enclosingM);
					continue;
				}
			}
			final T enclosingT = t.getEnclosingT();
			if (enclosingT != null) {
				if (enclosingT.isDeclaration()) {
					t.setDeclarationOwner(enclosingT);
					continue;
				}
			}
			topTs.add(t);
		}
		return topTs;
	}

	/**
	 * Returns the "simple binary name" of the underlying class, i.e., the binary name without the
	 * leading enclosing class name. Returns {@code null} if the underlying class is a top level
	 * class.
	 *
	 * Works just for JVM >= 5.
	 *
	 * @param t
	 *            type
	 * @return simple binary name
	 * @since 1.5
	 * @see Class#getSimpleName()
	 */
	@Nullable
	private static String getSimpleBinaryName(final T t) {
		final T enclosingT = t.getEnclosingT();
		if (enclosingT == null) {
			return null;
		}
		if (!t.getName().startsWith(enclosingT.getName())) {
			return null;
		}
		return t.getName().substring(enclosingT.getName().length());
	}

	/**
	 * Get simple name, like appearing in Java source code.
	 *
	 * Works just for JVM >= 5.
	 *
	 * @param t
	 *            type
	 * @return simple name
	 * @since 1.5
	 * @see Class#getSimpleName()
	 */
	@Nonnull
	private static String getSimpleClassName(final T t) {
		final String simpleName = getSimpleBinaryName(t);
		if (simpleName == null) { // is top level class
			return t.getPName();
		}
		// According to JLS3 "Binary Compatibility" (13.1) the binary
		// name of non-package classes (not top level) is the binary
		// name of the immediately enclosing class followed by a '$' followed by:
		// (for nested and inner classes): the simple name.
		// (for local classes): 1 or more digits followed by the simple name.
		// (for anonymous classes): 1 or more digits.

		// Since getSimpleBinaryName() will strip the binary name of
		// the immediatly enclosing class, we are now looking at a
		// string that matches the regular expression "\$[0-9]*"
		// followed by a simple name (considering the simple of an
		// anonymous class to be the empty string).

		// Remove leading "\$[0-9]*" from the name
		final int length = simpleName.length();
		if (length < 1 || simpleName.charAt(0) != '$') {
			throw new DecoJerException("Malformed class name");
		}
		int index = 1;
		while (index < length && isAsciiDigit(simpleName.charAt(index))) {
			index++;
		}
		// Eventually, this is the empty string iff this is an anonymous class
		final String ret = simpleName.substring(index);
		assert ret != null;
		return ret;
	}

	/**
	 * Character.isDigit answers {@code true} to some non-ascii digits. This one does not.
	 *
	 * @param c
	 *            character
	 * @return {@code true} - is ascii digit
	 */
	private static boolean isAsciiDigit(final char c) {
		return '0' <= c && c <= '9';
	}

	/**
	 * Enum switches use static inner with static cached map, use enclosingT info.
	 *
	 * @param t
	 *            type declaration
	 * @return {@code true} - is enum switch mal inner
	 */
	private static boolean isEnumSwitchMap(final T t) {
		for (final Element declaration : t.getDeclarations()) {
			if (!(declaration instanceof F)) {
				continue;
			}
			final F f = (F) declaration;
			if (!f.isStatic()) {
				continue;
			}
			if (!f.getName().startsWith("$SwitchMap$")) {
				continue;
			}
			return true;
		}
		return false;
	}

	/**
	 * Transform decompilation unit.
	 *
	 * @param du
	 *            decompilation unit
	 */
	public static void transform(@Nonnull final DU du) {
		final Collection<T> ts = du.getTs();

		checkBinaryCompatibilityNamingRules(ts);
		findEnclosingMethods(ts);
		final List<T> topTs = findTopTs(ts);

		final Map<String, CU> sourceId2cu = Maps.newHashMap();
		for (final T topT : topTs) {
			assert topT != null : du;
			// source file name set? then this top T could belong to a multi-top-CU, check
			final String sourceFileName = topT.getSourceFileName();
			if (sourceFileName == null) {
				// no multi-top CU, we have our own
				final String sourceId = topT.getPName() + ".java";
				sourceId2cu.put(sourceId, new CU(topT, sourceId));
				continue;
			}
			// source file name is set! check if multi-top-CU
			final String packageName = topT.getPackageName();
			final String sourceId = packageName == null ? sourceFileName : packageName + "."
					+ sourceFileName;
			final CU cu = sourceId2cu.get(sourceId);
			if (cu != null) {
				topT.setDeclarationOwner(cu);
				continue;
			}
			sourceId2cu.put(sourceId, new CU(topT, sourceFileName));
		}
		// find selected CUs
		final Set<CU> selectedCus = Sets.newHashSet();
		for (final T selectedT : du.getSelectedTs()) {
			final CU cu = selectedT.getCu();
			assert cu != null;
			selectedCus.add(cu);
		}
		// sort selected CUs by name
		final List<CU> sortedSelectedCus = Lists.newArrayList(selectedCus);
		Collections.sort(sortedSelectedCus, new Comparator<CU>() {

			@Override
			public int compare(final CU cu1, final CU cu2) {
				return cu1.getName().compareTo(cu2.getName());
			}

		});
		du.setCus(sortedSelectedCus);
	}

}