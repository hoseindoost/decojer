/*
 * $Id$
 *
 * This file is part of the DecoJer project.
 * Copyright (C) 2010-2011  Andr� Pankraz
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
package org.decojer.cavaj.reader.dex2jar;

import org.decojer.cavaj.model.FD;
import org.decojer.cavaj.model.MD;
import org.decojer.cavaj.model.TD;
import org.objectweb.asm.AnnotationVisitor;

import com.googlecode.dex2jar.Field;
import com.googlecode.dex2jar.Method;
import com.googlecode.dex2jar.visitors.DexClassVisitor;
import com.googlecode.dex2jar.visitors.DexFieldVisitor;
import com.googlecode.dex2jar.visitors.DexMethodVisitor;

/**
 * Read DEX class visitor.
 * 
 * @author Andr� Pankraz
 */
public class ReadDexClassVisitor implements DexClassVisitor {

	private final ReadDexFieldVisitor readDexFieldVisitor = new ReadDexFieldVisitor();

	private final ReadDexMethodVisitor readDexMethodVisitor = new ReadDexMethodVisitor();

	private TD td;

	/**
	 * Get type declaration.
	 * 
	 * @return type declaration
	 */
	public TD getTd() {
		return this.td;
	}

	/**
	 * Set type declaration.
	 * 
	 * @param td
	 *            type declaration
	 */
	public void setTd(final TD td) {
		this.td = td;
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String name,
			final boolean visitable) {
		return new ReadDexAnnotationVisitor();
	}

	@Override
	public void visitEnd() {
		// nothing
	}

	@Override
	public DexFieldVisitor visitField(final Field field, final Object value) {
		// MAX_CHANGES_BEFORE_PURGE : I : 100
		// queue : Ljava/lang/ref/ReferenceQueue; : null
		final FD fd = new FD(this.td, field.getAccessFlags(), field.getName(),
				field.getType(), null, value);
		this.td.getBds().add(fd);

		this.readDexFieldVisitor.setFd(fd);
		return this.readDexFieldVisitor;
	}

	@Override
	public DexMethodVisitor visitMethod(final Method method) {
		// put : (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

		// Exceptions are in method annotations!

		final MD md = new MD(this.td, method.getAccessFlags(),
				method.getName(), method.getType().getDesc().replace('/', '.'),
				null, null);
		this.td.getBds().add(md);

		this.readDexMethodVisitor.setMd(md);
		return this.readDexMethodVisitor;
	}

	@Override
	public void visitSource(final String file) {
		this.td.setSourceFileName(file);
	}

}