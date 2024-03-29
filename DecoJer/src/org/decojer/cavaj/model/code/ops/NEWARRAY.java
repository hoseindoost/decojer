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
package org.decojer.cavaj.model.code.ops;

import javax.annotation.Nonnull;

import lombok.Getter;

import org.decojer.cavaj.model.types.T;

/**
 * Operation 'NEWARRAY'.
 *
 * This operation initializes array dimensions, which can be less then the given array type. The
 * operation uses the array type, not the component type + dimension!
 *
 * @author André Pankraz
 */
public class NEWARRAY extends TypedOp {

	@Getter
	private final int dimensions;

	/**
	 * Constructor.
	 *
	 * @param pc
	 *            pc
	 * @param opcode
	 *            operation code
	 * @param line
	 *            line number
	 * @param t
	 *            array type, not the component type reduced by dimensions!
	 * @param dimensions
	 *            dimensions for initialization, smaller than array type dimension!
	 */
	public NEWARRAY(final int pc, final int opcode, final int line, @Nonnull final T t,
			final int dimensions) {
		super(pc, opcode, line, t);

		assert dimensions > 0 : dimensions;

		this.dimensions = dimensions;
	}

	@Override
	public int getInStackSize() {
		return this.dimensions;
	}

	@Override
	public Optype getOptype() {
		return Optype.NEWARRAY;
	}

}