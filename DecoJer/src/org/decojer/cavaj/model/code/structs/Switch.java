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
package org.decojer.cavaj.model.code.structs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Getter;
import lombok.Setter;

import org.decojer.cavaj.model.code.BB;
import org.decojer.cavaj.model.types.T;

/**
 * Switch struct.
 *
 * @author André Pankraz
 */
public class Switch extends Struct {

	/**
	 * Switch kind.
	 *
	 * @author André Pankraz
	 */
	public enum Kind {

		/**
		 * No-default switch.
		 */
		NO_DEFAULT,
		/**
		 * With-defaulte switch.
		 */
		WITH_DEFAULT

	}

	@Getter
	@Setter
	private Kind kind;

	/**
	 * Constructor.
	 *
	 * @param head
	 *            switch head BB
	 */
	public Switch(@Nonnull final BB head) {
		super(head);
	}

	/**
	 * Get case values if this switch struct has the given BB as case node.
	 *
	 * @param bb
	 *            BB
	 * @return case values if this switch struct has the given BB as case node or {@code null}
	 */
	@Nullable
	public Object[] getCaseValues(@Nullable final BB bb) {
		final Object value = findValueWhereFirstMemberIs(bb);
		return value instanceof Object[] && !(value instanceof T[]) ? (Object[]) findValueWhereFirstMemberIs(bb)
				: null;
	}

	@Override
	public String getDefaultLabelName() {
		return "switchStruct";
	}

	@Override
	public boolean isDefaultBreakable() {
		return true;
	}

	@Override
	public String toStringSpecial(final String prefix) {
		return prefix + "Kind: " + getKind();
	}

}