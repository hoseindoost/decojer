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
package org.decojer.cavaj.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import org.decojer.cavaj.model.fields.F;
import org.decojer.cavaj.model.fields.FD;
import org.decojer.cavaj.model.methods.M;
import org.decojer.cavaj.model.methods.MD;
import org.decojer.cavaj.model.types.T;
import org.decojer.cavaj.model.types.TD;
import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Container for Declarations.
 * 
 * @author André Pankraz
 */
public abstract class D {

	/**
	 * All body declarations: inner type / method / field declarations.
	 */
	@Getter
	private final List<Element> declarations = new ArrayList<Element>(0);

	/**
	 * AST node or {@code null}.
	 */
	@Getter
	@Setter
	private Object astNode;

	/**
	 * Clear all decompile infos, e.g. AST nodes.
	 */
	public void clear() {
		for (final Element e : getDeclarations()) {
			e.clear();
		}
	}

	/**
	 * Get declaration for AST node or {@code null}.
	 * 
	 * @param node
	 *            AST node or {@code null}
	 * @return declaration
	 */
	public Element getDeclarationForNode(final ASTNode node) {
		for (final Element bd : getDeclarations()) {
			// could also work with polymorphism here...but why pollute subclasses with helper
			if (bd instanceof F) {
				if (((F) bd).getAstNode() == node) {
					return bd;
				}
			} else if (bd instanceof M) {
				if (((M) bd).getAstNode() == node) {
					return bd;
				}
			} else if (bd instanceof T) {
				if (((T) bd).getAstNode() == node) {
					return bd;
				}
			}
			final Element retBd = bd.getDeclarationForNode(node);
			if (retBd != null) {
				return retBd;
			}
		}
		return null;
	}

	public Element getElement() {
		if (this instanceof TD) {
			return ((TD) this).getT();
		}
		if (this instanceof MD) {
			return ((MD) this).getM();
		}
		if (this instanceof FD) {
			return ((FD) this).getF();
		}
		return null;
	}

}