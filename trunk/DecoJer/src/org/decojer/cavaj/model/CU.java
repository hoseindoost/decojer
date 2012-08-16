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

import java.util.EnumSet;

import lombok.Getter;
import lombok.Setter;

import org.decojer.DecoJerException;
import org.decojer.cavaj.model.code.DFlag;
import org.decojer.cavaj.transformers.TrMergeAll;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/**
 * Compilation unit. Can contain multiple type declarations, but only one with type name equal to
 * source file name can be public.
 * 
 * @author André Pankraz
 */
public final class CU extends D {

	/**
	 * AST compilation unit.
	 */
	@Getter
	@Setter
	private CompilationUnit compilationUnit;

	private final EnumSet<DFlag> dFlags = EnumSet.noneOf(DFlag.class);

	/**
	 * Source file name (calculated).
	 */
	@Getter
	private final String sourceFileName;

	/**
	 * Constructor.
	 * 
	 * @param td
	 *            main type declaration
	 * @param sourceFileName
	 *            source file name
	 */
	public CU(final TD td, final String sourceFileName) {
		assert td != null;
		assert sourceFileName != null;

		addTd(td);
		this.sourceFileName = sourceFileName;
	}

	/**
	 * Check decompile flag.
	 * 
	 * @param dFlag
	 *            decompile flag
	 * @return true - decompile flag is active
	 */
	public boolean check(final DFlag dFlag) {
		return this.dFlags.contains(dFlag);
	}

	/**
	 * Clear all generated data after read.
	 */
	@Override
	public void clear() {
		this.compilationUnit = null;
		super.clear();
	}

	/**
	 * Create source code.
	 * 
	 * @return source code
	 */
	public String createSourceCode() {
		final Document document = new Document();
		final TextEdit edits = this.compilationUnit.rewrite(document, null);
		try {
			edits.apply(document);
		} catch (final MalformedTreeException e) {
			throw new DecoJerException("Couldn't create source code!", e);
		} catch (final BadLocationException e) {
			throw new DecoJerException("Couldn't create source code!", e);
		}
		String sourceCode = document.get();

		packageAnnotationBug: if (this.compilationUnit.getPackage() != null
				&& this.compilationUnit.getPackage().annotations().size() > 0) {
			// bugfix for: https://bugs.eclipse.org/bugs/show_bug.cgi?id=361071
			// for Eclipse 4.2 still necessary
			// see TrJvmStruct2JavaAst.transform(TD)
			final int pos = sourceCode.indexOf("package ");
			if (pos < 2) {
				break packageAnnotationBug;
			}
			final char ch = sourceCode.charAt(pos - 1);
			if (Character.isWhitespace(ch)) {
				break packageAnnotationBug;
			}
			sourceCode = sourceCode.substring(0, pos - 1) + "\n" + sourceCode.substring(pos);
		}
		// build class decompilation comment
		final StringBuilder sb = new StringBuilder(sourceCode);
		sb.append("\n\n/*\n").append(" * Generated by DecoJer ").append(0.9)
				.append(", a Java-bytecode decompiler.\n")
				.append(" * DecoJer Copyright (C) 2009-2011 André Pankraz. All Rights Reserved.\n")
				.append(" *\n");
		final int version = getTd().getVersion();
		if (version == 0) {
			sb.append(" * Dalvik File");
		} else {
			sb.append(" * Class File Version: ").append(version).append(" (Java ");
			if (version <= 48) {
				sb.append("1.");
			}
			sb.append(version - 44).append(')');
		}
		sb.append('\n');
		if (getTd().getSourceFileName() != null) {
			sb.append(" * Source File Name: ").append(getTd().getSourceFileName()).append('\n');
		}
		sb.append(" */");
		return sb.toString();
	}

	/**
	 * Decompile compilation unit.
	 * 
	 * @return source code
	 */
	public String decompile() {
		for (final BD bd : getAllTds()) {
			((TD) bd).decompile();
		}
		TrMergeAll.transform(this);
		return createSourceCode();
	}

	/**
	 * Get abstract syntax tree.
	 * 
	 * @return abstract syntax tree
	 */
	public AST getAst() {
		final AST ast = getCompilationUnit().getAST();
		assert ast != null;

		return ast;
	}

	/**
	 * Get name.
	 * 
	 * @return name
	 */
	@Override
	public String getName() {
		return getPackageName() + "." + this.sourceFileName;
	}

	/**
	 * Get package name.
	 * 
	 * @return package name
	 */
	public String getPackageName() {
		return getTd().getPackageName();
	}

	/**
	 * Get first type declaration.
	 * 
	 * @return first type declaration
	 */
	public TD getTd() {
		return (TD) getBds().get(0);
	}

}