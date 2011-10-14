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
package org.decojer.cavaj.model.code.op;

import org.decojer.cavaj.model.T;

/**
 * Operation 'NEWARRAY'.
 * 
 * @author Andr� Pankraz
 */
public class NEWARRAY extends Op {

	private final int dimensions;

	private final T t;

	/**
	 * Constructor.
	 * 
	 * @param pc
	 *            original pc
	 * @param opcode
	 *            original operation code
	 * @param line
	 *            line number
	 * @param t
	 *            type
	 * @param dimensions
	 *            dimensions
	 */
	public NEWARRAY(final int pc, final int opcode, final int line, final T t, final int dimensions) {
		super(pc, opcode, line);
		this.t = t;
		this.dimensions = dimensions;
	}

	/**
	 * Get dimensions.
	 * 
	 * @return dimensions
	 */
	public int getDimensions() {
		return this.dimensions;
	}

	@Override
	public int getInStackSize() {
		return this.dimensions;
	}

	@Override
	public Optype getOptype() {
		return Optype.NEWARRAY;
	}

	/**
	 * Get type.
	 * 
	 * @return type
	 */
	public T getT() {
		return this.t;
	}

}