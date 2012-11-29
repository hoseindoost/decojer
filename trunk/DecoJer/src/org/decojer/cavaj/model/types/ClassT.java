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
import java.util.logging.Logger;

import lombok.Getter;
import lombok.Setter;

import org.decojer.cavaj.model.AF;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.M;
import org.decojer.cavaj.model.T;
import org.decojer.cavaj.model.TD;

/**
 * Class type.
 * 
 * @author André Pankraz
 */
public class ClassT extends T {

	private final static Logger LOGGER = Logger.getLogger(ClassT.class.getName());

	private static String toString(final T superT, final T[] interfaceTs) {
		final StringBuilder sb = new StringBuilder("{");
		if (superT != null) {
			sb.append(superT.getName()).append(',');
		}
		for (final T interfaceT : interfaceTs) {
			sb.append(interfaceT.getName()).append(",");
		}
		sb.setCharAt(sb.length() - 1, '}');
		return sb.toString();
	}

	/**
	 * Access flags.
	 */
	@Setter
	private int accessFlags;

	@Getter
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

	@Setter
	private T[] interfaceTs;

	/**
	 * Super type.
	 */
	private T superT;

	@Getter
	private TD td;

	/**
	 * Type parameters. (They define the useable type variables)
	 */
	@Setter
	private T[] typeParams;

	public static final T[] INTERFACES_NONE_UNRESOLVED = new T[0];

	/**
	 * Constructor.
	 * 
	 * @param name
	 *            type name
	 * @param du
	 *            decompilation unit
	 */
	public ClassT(final String name, final DU du) {
		super(name);

		assert du != null;

		this.du = du;
	}

	public ClassT(final T superT, final T... interfaceTs) {
		super(toString(superT, interfaceTs));

		this.du = superT.getDu();
		this.superT = superT;
		this.interfaceTs = interfaceTs;
	}

	/**
	 * Check access flag.
	 * 
	 * @param af
	 *            access flag
	 * @return {@code true} - is access flag
	 */
	public boolean check(final AF af) {
		if (this.accessFlags == 0) {
			resolve();
		}
		return (this.accessFlags & af.getValue()) != 0;
	}

	/**
	 * Create type declaration for this type.
	 * 
	 * @return type declaration
	 */
	public TD createTd() {
		assert this.td == null;

		this.td = new TD(this);
		return this.td;
	}

	private Object getEnclosing() {
		if (this.enclosing == null) {
			resolve();
		}
		return this.enclosing == NONE ? null : this.enclosing;
	}

	@Override
	public M getEnclosingM() {
		final Object enclosing = getEnclosing();
		return enclosing instanceof M ? (M) enclosing : null;
	}

	@Override
	public ClassT getEnclosingT() {
		final Object enclosing = getEnclosing();
		// like Class#getEnclosingClass()
		if (enclosing instanceof M) {
			return (ClassT) ((M) enclosing).getT();
		}
		return enclosing instanceof ClassT ? (ClassT) enclosing : null;
	}

	@Override
	public T[] getInterfaceTs() {
		if (this.interfaceTs == null) {
			resolve();
		}
		return this.interfaceTs;
	}

	@Override
	public int getKind() {
		return Kind.REF.getKind();
	}

	@Override
	public T getSuperT() {
		if (this.superT == null) {
			resolve();
		}
		// can be null, e.g. for Object.class or Interfaces
		return this.superT == NONE ? null : this.superT;
	}

	@Override
	public T[] getTypeParams() {
		if (this.typeParams == null) {
			resolve();
		}
		return this.typeParams;
	}

	@Override
	public boolean isInterface() {
		return check(AF.INTERFACE);
	}

	@Override
	public boolean isObject() {
		return Object.class.getName().equals(getName());
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isRef() {
		return true;
	}

	@Override
	public boolean isResolvable() {
		return !check(AF.UNRESOLVABLE);
	}

	/**
	 * Mark access flag.
	 * 
	 * @param af
	 *            access flag
	 */
	public void markAf(final AF af) {
		this.accessFlags |= af.getValue();
	}

	private boolean resolve() {
		// try simple class loading, may be we are lucky ;)
		// TODO later ask DecoJer-online and local type cache with context info
		try {
			final Class<?> clazz = getClass().getClassLoader().loadClass(getName());
			this.accessFlags = clazz.getModifiers();

			final Class<?> superclass = clazz.getSuperclass();
			if (superclass != null) {
				this.superT = getDu().getT(superclass);
			}
			final Class<?>[] interfaces = clazz.getInterfaces();
			if (interfaces.length > 0) {
				final T[] interfaceTs = new T[interfaces.length];
				for (int i = interfaces.length; i-- > 0;) {
					interfaceTs[i] = getDu().getT(interfaces[i]);
				}
				this.interfaceTs = interfaceTs;
			}
			final TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
			if (typeParameters.length > 0) {
				final T[] typeParams = new T[typeParameters.length];
				for (int i = typeParameters.length; i-- > 0;) {
					typeParams[i] = getDu().getT(typeParameters[i].getName());
				}
				this.typeParams = typeParams;
			}
			final Method enclosingMethod = clazz.getEnclosingMethod();
			if (enclosingMethod != null) {
				final Class<?> declaringClass = enclosingMethod.getDeclaringClass();
				final T methodT = this.du.getT(declaringClass);
				// TODO difficult...have only generic types here, not original descriptor
				this.enclosing = methodT.getM(enclosingMethod.getName(), "<TODO>");
			}
			final Class<?> enclosingClass = clazz.getEnclosingClass();
			if (enclosingClass != null) {
				this.enclosing = this.du.getT(enclosingClass);
			}
			return true;
		} catch (final ClassNotFoundException e) {
			LOGGER.warning("Couldn't load type '" + getName() + "'!");
			markAf(AF.UNRESOLVABLE);
			return false;
		} finally {
			resolved();
		}
	}

	public void resolved() {
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

	/**
	 * Set enclosing method (since JVM 5).
	 * 
	 * @param m
	 *            method
	 * 
	 * @see ClassT#setEnclosingT(T)
	 */
	public void setEnclosingM(final M m) {
		if (this.enclosing != null) {
			if (this.enclosing != m) {
				LOGGER.warning("Enclosing method cannot be changed from '" + this.enclosing
						+ "' to '" + m + "'!");
			}
			return;
		}
		this.enclosing = m;
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
	 * JVM 5 has enclosing method attribute for local/anonymous, outer info only for declaring outer<br>
	 * JVM < 5 has no enclosing method attribute and:<br>
	 * JVM 1.1 has normal outer info for anonymous/local, like declaring for JVM 5,<br>
	 * JVM 1.2 .. 1.4 has no outer info at all!!!
	 * 
	 * We cannot ignore this information and rely on naming rules, because the separator '$' is a
	 * valid character in none-inner type names.
	 * 
	 * @param t
	 *            class type
	 * 
	 * @see Class#getEnclosingClass()
	 */
	public void setEnclosingT(final T t) {
		if (this.enclosing != null) {
			if (this.enclosing != t) {
				LOGGER.warning("Enclosing type cannot be changed from '" + this.enclosing
						+ "' to '" + t + "'!");
			}
			return;
		}
		this.enclosing = t;
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
		// inner access flags have _exclusively_ following modifiers: PROTECTED, PRIVATE, STATIC,
		// but not: SUPER
		this.accessFlags = accessFlags | this.accessFlags & AF.SUPER.getValue();
		// don't really need this info (@see T#getInnerName()) for JVM >= 5
		this.innerName = name != null ? name : "";
	}

	/**
	 * Set super type.
	 * 
	 * @param superT
	 *            super type
	 */
	public void setSuperT(final T superT) {
		this.superT = superT != null ? superT : NONE;
	}

}