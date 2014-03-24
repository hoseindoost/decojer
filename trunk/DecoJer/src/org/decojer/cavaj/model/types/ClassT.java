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

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.decojer.cavaj.model.A;
import org.decojer.cavaj.model.AF;
import org.decojer.cavaj.model.CU;
import org.decojer.cavaj.model.Container;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.Element;
import org.decojer.cavaj.model.methods.M;
import org.decojer.cavaj.utils.Cursor;
import org.eclipse.jdt.core.dom.ASTNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Class type.
 *
 * Class types are uniquely cached in the decompilation unit, they are used very often. Hence it's
 * very important that all type infos are only set as they declared and not as they are referenced
 * (no type arguments here, e.g. in enclosing info).
 *
 * @author André Pankraz
 */
@Slf4j
public class ClassT extends T {

	/**
	 * Type name - is like a unique descriptor without modifiers like annotations or
	 * parameterization.
	 *
	 * Names consist of '.'-separated package names (for full name) and '$'-separated type names
	 * (but '$' is also a valid Java name char!)
	 *
	 * Valid name chars contain also connecting characters and other, e.g.:
	 *
	 * $ _ ¢ £ ¤ ¥ ؋ ৲ ৳ ৻ ૱ ௹ ฿ ៛ ‿ ⁀ ⁔ ₠ ₡ ₢ ₣ ₤ ₥ ₦ ₧ ₨ ₩ ₪ ₫ € ₭ ₮ ₯ ₰ ₱ ₲ ₳ ₴ ₵ ₶ ₷ ₸ ₹ ꠸ ﷼ ︳ ︴
	 * ﹍ ﹎ ﹏ ﹩ ＄ ＿ ￠ ￡ ￥ ￦
	 */
	@Getter
	private final String name;

	/**
	 * Access flags.
	 */
	@Setter
	private int accessFlags;

	@Getter
	@Nonnull
	private final DU du;

	/**
	 * We mix here declaring classes info and enclosing method / classes info.
	 *
	 * @see ClassT#setEnclosingT(T)
	 */
	private Object enclosing;

	/**
	 * @see T#getInnerName()
	 */
	@Getter
	private String innerName;

	/**
	 * Interface types.
	 */
	@Nullable
	private T[] interfaceTs;

	/**
	 * Super type.
	 */
	@Nullable
	private T superT;

	@Getter(AccessLevel.PRIVATE)
	private TD td;

	@Nullable
	private Map<String, Object> member;

	/**
	 * Type parameters. (They define the useable type variables)
	 */
	@Setter
	@Nullable
	private T[] typeParams;

	/**
	 * Constructor.
	 *
	 * @param du
	 *            decompilation unit
	 * @param name
	 *            type name
	 */
	public ClassT(final DU du, final String name) {
		assert du != null;
		assert name != null;

		this.du = du;
		this.name = name;
	}

	@Override
	public boolean check(final AF af) {
		return (this.accessFlags & af.getValue()) != 0;
	}

	@Override
	public void clear() {
		getTd().clear();
	}

	@Override
	public boolean createTd() {
		if (isDeclaration()) {
			return false;
		}
		this.td = new TD();
		return true;
	}

	@Override
	public A[] getAs() {
		return getTd().getAs();
	}

	@Override
	public Object getAstNode() {
		return getTd().getAstNode();
	}

	@Override
	public CU getCu() {
		return getTd().getCu();
	}

	@Override
	public Element getDeclarationForNode(final ASTNode node) {
		return getTd().getDeclarationForNode(node);
	}

	@Override
	public Container getDeclarationOwner() {
		return getTd().getDeclarationOwner();
	}

	@Override
	public List<Element> getDeclarations() {
		return getTd().getDeclarations();
	}

	private Object getEnclosing() {
		if (this.enclosing == null && isUnresolvable()) {
			return null;
		}
		if (this.enclosing == NONE) {
			return null;
		}
		assert this.enclosing instanceof T || this.enclosing instanceof M : this
		+ ": enclosing must be T or M";

		return this.enclosing;
	}

	@Override
	public M getEnclosingM() {
		final Object enclosing = getEnclosing();
		if (enclosing instanceof M) {
			return (M) enclosing;
		}
		return null;
	}

	@Override
	public T getEnclosingT() {
		final Object enclosing = getEnclosing();
		if (enclosing instanceof T) {
			return (T) enclosing;
		}
		if (enclosing instanceof M) {
			return ((M) enclosing).getT();
		}
		return null;
	}

	@Override
	public T[] getInterfaceTs() {
		if (this.interfaceTs == null && isUnresolvable()) {
			return INTERFACES_NONE;
		}
		return this.interfaceTs;
	}

	@Nonnull
	@Override
	public Map<String, Object> getMember() {
		if (this.member == null) {
			this.member = Maps.newHashMap();
		}
		return this.member;
	}

	@Override
	public String getSourceFileName() {
		return getTd().getSourceFileName();
	}

	@Override
	public T getSuperT() {
		if (this.superT == null && isUnresolvable()) {
			return null;
		}
		// can be null, e.g. for Object or Interfaces
		return this.superT == NONE ? null : this.superT;
	}

	@Override
	public T[] getTypeParams() {
		T[] typeParams = this.typeParams;
		if (typeParams == null) {
			isUnresolvable();
			typeParams = this.typeParams;
			assert typeParams != null;
		}
		return typeParams;
	}

	@Override
	public int getVersion() {
		return getTd().getVersion();
	}

	@Override
	public boolean isAtLeast(final Version version) {
		return getVersion() >= version.getMajor();
	}

	@Override
	public boolean isBelow(final Version version) {
		return getVersion() < version.getMajor();
	}

	@Override
	public boolean isDalvik() {
		return getVersion() == 0;
	}

	@Override
	public boolean isDeclaration() {
		return getTd() != null;
	}

	/**
	 * Is deprecated type, marked via Javadoc @deprecated?
	 *
	 * @return {@code true} - is deprecated type
	 */
	public boolean isDeprecated() {
		return check(AF.DEPRECATED);
	}

	@Override
	public boolean isEnum() {
		return check(AF.ENUM);
	}

	@Override
	public boolean isInterface() {
		return check(AF.INTERFACE);
	}

	@Override
	public boolean isObject() {
		return is(Object.class);
	}

	@Override
	public boolean isScala() {
		final String sourceFileName = getSourceFileName();
		return sourceFileName != null && sourceFileName.endsWith(".scala");
	}

	@Override
	public boolean isStatic() {
		return check(AF.STATIC);
	}

	@Override
	public boolean isSynthetic() {
		return check(AF.SYNTHETIC);
	}

	@Override
	public boolean isUnresolvable() {
		if ((this.accessFlags & AF.UNRESOLVABLE.getValue()) != 0) {
			return true;
		}
		// try simple class loading, may be we are lucky ;)
		// TODO later ask DecoJer-online and local type cache with context info
		final Class<?> klass;
		try {
			klass = getClass().getClassLoader().loadClass(getName());
		} catch (final ClassNotFoundException e) {
			// log.warning("Couldn't load type '" + getName() + "'!");
			this.accessFlags |= AF.UNRESOLVABLE.getValue();
			return true;
		} catch (final SecurityException e) {
			log.warn("Couldn't load type class '" + getName()
					+ "' because of security issues!\nMessage: " + e.getMessage());
			this.accessFlags |= AF.UNRESOLVABLE.getValue();
			return true;
		}
		this.accessFlags = klass.getModifiers();

		final Class<?> superclass = klass.getSuperclass();
		if (superclass != null) {
			this.superT = getDu().getT(superclass);
		}
		final Class<?>[] interfaces = klass.getInterfaces();
		if (interfaces.length > 0) {
			final T[] interfaceTs = new T[interfaces.length];
			for (int i = interfaces.length; i-- > 0;) {
				interfaceTs[i] = getDu().getT(interfaces[i]);
			}
			this.interfaceTs = interfaceTs;
		}
		final TypeVariable<?>[] typeParameters = klass.getTypeParameters();
		if (typeParameters.length > 0) {
			final T[] typeParams = new T[typeParameters.length];
			for (int i = typeParameters.length; i-- > 0;) {
				typeParams[i] = getDu().getT(typeParameters[i].getName());
			}
			this.typeParams = typeParams;
		}
		final Class<?> enclosingClass = klass.getEnclosingClass();
		if (enclosingClass != null) {
			setEnclosingT(this.du.getT(enclosingClass));
		}
		final Method enclosingMethod = klass.getEnclosingMethod();
		if (enclosingMethod != null) {
			final Class<?> declaringClass = enclosingMethod.getDeclaringClass();
			final T methodT = this.du.getT(declaringClass);
			try {
				// backcalculating desc is a bit too much trouble, easier for now this way...
				final Method method = klass.getClass().getDeclaredMethod("getEnclosingMethod0",
						new Class[0]);
				method.setAccessible(true);
				final Object[] info = (Object[]) method.invoke(klass, new Object[0]);
				setEnclosingM(methodT.getM(enclosingMethod.getName() /* also info[1] */,
						(String) info[2]));
			} catch (final Exception e) {
				log.warn("Couldn't get descriptor for class loaded method!", e);
			}
		}
		resolve();
		return false;
	}

	/**
	 * Parse interface types from signature.
	 *
	 * @param s
	 *            signature
	 * @param c
	 *            cursor
	 * @return interface types
	 */
	@Nullable
	private T[] parseInterfaceTs(final String s, final Cursor c) {
		if (c.pos >= s.length() || s.charAt(c.pos) != 'L') {
			return null;
		}
		final List<T> ts = Lists.newArrayList();
		do {
			final T interfaceT = getDu().parseT(s, c, this);
			// not here...signature could be wrong (not bytecode checked), check erasure first
			// interfaceT.setInterface(true);
			ts.add(interfaceT);
		} while (c.pos < s.length() && s.charAt(c.pos) == 'L');
		return ts.toArray(new T[ts.size()]);
	}

	@Override
	public void resolve() {
		if (this.superT == null) {
			this.superT = NONE; // Object/Interfaces have no super!
		}
		if (this.interfaceTs == null) {
			this.interfaceTs = INTERFACES_NONE;
		}
		if (this.typeParams == null) {
			this.typeParams = TYPE_PARAMS_NONE;
		}
		if (this.enclosing == null) {
			this.enclosing = NONE;
		}
	}

	@Override
	public void setAs(final A[] as) {
		getTd().setAs(as);
	}

	@Override
	public void setAstNode(final Object astNode) {
		getTd().setAstNode(astNode);
	}

	@Override
	public void setDeclarationOwner(@Nonnull final Container declarationOwner) {
		final Container previousDeclarationOwner = getTd().getDeclarationOwner();
		if (previousDeclarationOwner != null) {
			previousDeclarationOwner.getDeclarations().remove(this);
		}
		declarationOwner.getDeclarations().add(this);
		getTd().setDeclarationOwner(declarationOwner);
	}

	@Override
	public void setDeprecated() {
		this.accessFlags |= AF.DEPRECATED.getValue();
	}

	@Override
	public void setEnclosingM(final M enclosingM) {
		if (this.enclosing != null && this.enclosing != NONE) {
			if (this.enclosing.equals(enclosingM)) {
				return;
			}
			if (!this.enclosing.equals(enclosingM.getT())) {
				log.warn("Enclosing method cannot be changed from '" + this.enclosing + "' to '"
						+ enclosingM + "'!");
				return;
			}
			// enclosing method is more specific, overwrite enclosing type...
		}
		if (!validateQualifierName(enclosingM.getT().getName())) {
			log.warn("Enclosing type for '" + this + "' cannot be set to not matching method '"
					+ enclosingM + "'!");
			return;
		}
		this.enclosing = enclosingM;
	}

	@Override
	public void setEnclosingT(final T enclosingT) {
		if (!(enclosingT instanceof ClassT)) {
			log.warn("Enclosing type for '" + this + "' cannot be set to modified type '"
					+ enclosingT + "'!");
			return;
		}
		if (this.enclosing != null && this.enclosing != NONE) {
			if (this.enclosing.equals(enclosingT)) {
				return;
			}
			if (this.enclosing instanceof M && ((M) this.enclosing).getT().equals(enclosingT)) {
				// enclosing method is more specific, don't change it
				return;
			}
			log.warn("Enclosing type cannot be changed from '" + this.enclosing + "' to '"
					+ enclosingT + "'!");
			return;
		}
		if (!validateQualifierName(enclosingT.getName())) {
			log.warn("Enclosing type for '" + this + "' cannot be set to not matching type '"
					+ enclosingT + "'!");
			return;
		}
		this.enclosing = enclosingT;
	}

	@Override
	public void setInnerInfo(final String name, final int accessFlags) {
		// inner access flags have _exclusively_ following modifiers: PROTECTED, PRIVATE, STATIC,
		// but not: SUPER
		this.accessFlags = accessFlags | this.accessFlags & AF.SUPER.getValue();
		// don't really need this info (@see T#getInnerName()) for JVM >= 5
		this.innerName = name != null ? name : "";
	}

	/**
	 * Type must be an interface or class.
	 *
	 * @param f
	 *            {@code true} - is interface
	 */
	@Override
	public void setInterface(final boolean f) {
		if (f) {
			if ((this.accessFlags & AF.INTERFACE.getValue()) != 0) {
				return;
			}
			assert (this.accessFlags & AF.INTERFACE_ASSERTED.getValue()) == 0;

			this.accessFlags |= AF.INTERFACE.getValue() | AF.INTERFACE_ASSERTED.getValue();
			return;
		}
		assert (this.accessFlags & AF.INTERFACE.getValue()) == 0;

		this.accessFlags |= AF.INTERFACE_ASSERTED.getValue();
		return;
	}

	@Override
	public void setInterfaceTs(final T[] interfaceTs) {
		for (final T t : interfaceTs) {
			t.setInterface(true);
		}
		this.interfaceTs = interfaceTs;
	}

	@Override
	public void setScala() {
		if (getSourceFileName() != null) {
			if (!isScala()) {
				log.warn("This should be a Scala source code!");
			}
			return;
		}
	}

	@Override
	public void setSignature(@Nullable final String signature) {
		if (signature == null) {
			return;
		}
		final Cursor c = new Cursor();
		setTypeParams(getDu().parseTypeParams(signature, c, this));

		final T superT = getDu().parseT(signature, c, this);
		if (superT != null) {
			if (!superT.eraseTo(getSuperT())) {
				log.info("Cannot reduce type '" + superT + "' to super type '" + getSuperT()
						+ "' for type declaration '" + this + "' with signature: " + signature);
				return;
			}
			setSuperT(superT);
		}
		final T[] signInterfaceTs = parseInterfaceTs(signature, c);
		if (signInterfaceTs != null) {
			final T[] interfaceTs = getInterfaceTs();
			if (signInterfaceTs.length > interfaceTs.length) {
				// < can happen, e.g. scala-lift misses the final java.io.Serializable in signatures
				log.info("Cannot reduce interface types for type declaration '" + this
						+ "' with signature: " + signature);
				return;
			}
			for (int i = 0; i < signInterfaceTs.length; ++i) {
				final T interfaceT = signInterfaceTs[i];
				if (!interfaceT.eraseTo(interfaceTs[i])) {
					log.info("Cannot reduce type '" + interfaceT + "' to interface type '"
							+ interfaceTs[i] + "' for type declaration '" + this
							+ "' with signature: " + signature);
					return;
				}
				// erasure works...now we are safe to assert interface...but should be anyway
				// because erasure leads to right type, not necessary:
				// interfaceT.setInterface(true);
				assert interfaceT.isInterface();

				interfaceTs[i] = interfaceT;
			}
		}
	}

	@Override
	public void setSourceFileName(final String sourceFileName) {
		getTd().setSourceFileName(sourceFileName);
	}

	@Override
	public void setSuperT(final T superT) {
		if (superT == null) {
			this.superT = NONE;
			return;
		}
		superT.setInterface(false);
		this.superT = superT;
	}

	@Override
	public void setSynthetic() {
		this.accessFlags |= AF.SYNTHETIC.getValue();
	}

	@Override
	public void setVersion(final int version) {
		getTd().setVersion(version);
	}

}