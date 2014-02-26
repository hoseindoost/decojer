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
package org.decojer.cavaj.model.methods;

import java.util.List;
import java.util.logging.Logger;

import lombok.Getter;
import lombok.Setter;

import org.decojer.cavaj.model.A;
import org.decojer.cavaj.model.ED;
import org.decojer.cavaj.model.code.CFG;
import org.decojer.cavaj.model.types.T;
import org.decojer.cavaj.model.types.TD;
import org.decojer.cavaj.utils.Cursor;
import org.eclipse.jdt.core.dom.BodyDeclaration;

import com.google.common.collect.Lists;

/**
 * Method declaration.
 * 
 * @author André Pankraz
 */
public final class MD extends ED {

	private final static Logger LOGGER = Logger.getLogger(MD.class.getName());

	/**
	 * Annotation default value.
	 */
	@Getter
	@Setter
	private Object annotationDefaultValue;

	/**
	 * Control flow graph.
	 */
	@Getter
	@Setter
	private CFG cfg;

	@Getter
	private final ClassM m;

	/**
	 * AST method declaration.
	 * 
	 * Lambda expressions don't really match: Prevent using getBody(), use CFG.getBlock()!
	 */
	@Getter
	@Setter
	private BodyDeclaration methodDeclaration;

	/**
	 * Method parameter annotations.
	 */
	@Getter
	@Setter
	private A[][] paramAss;

	private String[] paramNames;

	/**
	 * Remember signature for Eclipse method finding.
	 */
	@Getter
	private String signature;

	/**
	 * Throws types or {@code null}.
	 */
	@Getter
	@Setter
	private T[] throwsTs;

	/**
	 * Type parameters.
	 */
	@Getter
	private T[] typeParams;

	/**
	 * Constructor.
	 * 
	 * @param m
	 *            method
	 */
	public MD(final ClassM m) {
		assert m != null;

		this.m = m;
	}

	@Override
	public void clear() {
		this.methodDeclaration = null;
		if (this.cfg != null) {
			this.cfg.clear();
		}
		super.clear();
	}

	/**
	 * Get unique method descriptor.
	 * 
	 * @return method descriptor
	 */
	public String getDescriptor() {
		return getM().getDescriptor();
	}

	@Override
	public String getName() {
		return getM().getName();
	}

	/**
	 * Get parameter name for index.
	 * 
	 * Dalvik provides this information directly, the JVM indirect via the local variable table.
	 * Could also be extracted from JavaDoc etc.
	 * 
	 * @param i
	 *            index (starts with 0, double/long params count as 1)
	 * @return parameter name
	 */
	public String getParamName(final int i) {
		if (this.paramNames == null || i >= this.paramNames.length || this.paramNames[i] == null) {
			return "arg" + i;
		}
		return this.paramNames[i];
	}

	/**
	 * Get parameter types.
	 * 
	 * @return parameter types
	 */
	public T[] getParamTs() {
		return getM().getParamTs();
	}

	/**
	 * Get receiver-type (this) for none-static methods.
	 * 
	 * @return receiver-type
	 */
	public T getReceiverT() {
		return getM().getReceiverT();
	}

	/**
	 * Get return type.
	 * 
	 * @return return type
	 */
	public T getReturnT() {
		return getM().getReturnT();
	}

	/**
	 * Get owner type.
	 * 
	 * @return owner type
	 */
	public T getT() {
		return getM().getT();
	}

	/**
	 * Get owner type declaration.
	 * 
	 * @return owner type declaration
	 */
	public TD getTd() {
		return getT().getTd();
	}

	/**
	 * Is constructor?
	 * 
	 * @return {@code true} - is constructor
	 * @see M#isConstructor()
	 */
	public boolean isConstructor() {
		return getM().isConstructor();
	}

	/**
	 * Is initializer?
	 * 
	 * @return {@code true} - is initializer
	 * @see ClassM#isInitializer()
	 */
	public boolean isInitializer() {
		return getM().isInitializer();
	}

	/**
	 * Is method with final varargs parameter?
	 * 
	 * @return {@code true} - is method with final varargs parameter
	 */
	public boolean isVarargs() {
		return getM().isVarargs();
	}

	/**
	 * Parse throw types from signature.
	 * 
	 * @param s
	 *            signature
	 * @param c
	 *            cursor
	 * @return throw types or {@code null}
	 */
	private T[] parseThrowsTs(final String s, final Cursor c) {
		if (c.pos >= s.length() || s.charAt(c.pos) != '^') {
			return null;
		}
		final List<T> ts = Lists.newArrayList();
		do {
			++c.pos;
			final T throwT = getTd().getDu().parseT(s, c, getM());
			throwT.setInterface(false); // TODO we know even more, must be from Throwable
			ts.add(throwT);
		} while (c.pos < s.length() && s.charAt(c.pos) == '^');
		return ts.toArray(new T[ts.size()]);
	}

	/**
	 * Set parameter name.
	 * 
	 * Dalvik provides this information directly, the JVM indirect via the local variable table.
	 * Could also be extracted from JavaDoc etc.
	 * 
	 * @param i
	 *            index
	 * @param name
	 *            parameter name
	 */
	public void setParamName(final int i, final String name) {
		if (this.paramNames == null) {
			this.paramNames = new String[getParamTs().length];
		}
		this.paramNames[i] = name;
	}

	/**
	 * Set receiver type (this) for none-static methods.
	 * 
	 * @param receiverT
	 *            receiver type
	 * @return {@code true} - success
	 */
	public boolean setReceiverT(final T receiverT) {
		return getM().setReceiverT(receiverT);
	}

	/**
	 * Set return type.
	 * 
	 * @param returnT
	 *            return type
	 */
	public void setReturnT(final T returnT) {
		getM().setReturnT(returnT);
	}

	@Override
	public void setSignature(final String signature) {
		if (signature == null) {
			return;
		}
		// remember signature for Eclipse method finding...
		this.signature = signature;

		final Cursor c = new Cursor();
		// typeParams better in M, maybe later if necessary for static invokes
		this.typeParams = getTd().getDu().parseTypeParams(signature, c, getM());

		final T[] paramTs = getParamTs();
		final T[] signParamTs = getTd().getDu().parseMethodParamTs(signature, c, getM());
		if (signParamTs.length != 0) {
			if (paramTs.length != signParamTs.length) {
				// can happen with Sun JVM for constructor:
				// see org.decojer.cavaj.test.jdk2.DecTestInnerS.Inner1.Inner11.1.InnerMethod
				// or org.decojer.cavaj.test.jdk5.DecTestEnumStatus
				// Signature since JVM 5 exists but doesn't contain synthetic parameters,
				// e.g. outer context for methods in inner classes: (I)V instead of (Lthis;_I_II)V
				// or enum constructor parameters arg0: String, arg1: int
				if (!isConstructor()) {
					LOGGER.info("Cannot reduce signature '" + signature
							+ "' to types for method params: " + this);
				}
			} else {
				for (int i = 0; i < paramTs.length; ++i) {
					final T paramT = signParamTs[i];
					if (!paramT.eraseTo(paramTs[i])) {
						LOGGER.info("Cannot reduce signature '" + signature + "' to type '"
								+ paramTs[i] + "' for method param: " + this);
						break;
					}
					paramTs[i] = paramT;
				}
			}
		}
		final T returnT = getTd().getDu().parseT(signature, c, getM());
		if (!returnT.eraseTo(getReturnT())) {
			LOGGER.info("Cannot reduce signature '" + signature + "' to type '" + getReturnT()
					+ "' for method return: " + this);
		} else {
			getM().setReturnT(returnT);
		}
		final T[] signThrowTs = parseThrowsTs(signature, c);
		if (signThrowTs != null) {
			final T[] throwsTs = getThrowsTs();
			if (throwsTs.length != signThrowTs.length) {
				LOGGER.info("Cannot reduce signature '" + signature
						+ "' to types for method throws: " + this);
			}
			for (int i = 0; i < throwsTs.length; ++i) {
				final T throwT = signThrowTs[i];
				if (!throwT.eraseTo(throwsTs[i])) {
					LOGGER.info("Cannot reduce signature '" + signature + "' to type '"
							+ throwsTs[i] + "' for method throw: " + this);
					break;
				}
				throwsTs[i] = throwT;
			}
		}
	}

	@Override
	public String toString() {
		return getM().toString();
	}

}