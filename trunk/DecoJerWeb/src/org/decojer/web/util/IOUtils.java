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
package org.decojer.web.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Andr� Pankraz
 */
public class IOUtils {

	private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	public static String bytes2hex(final byte[] bytes) {
		int i = bytes.length;
		int t = i << 1;
		final char[] hex = new char[t];
		byte v;
		while (i > 0) {
			v = bytes[--i];
			hex[--t] = HEX_CHARS[v & 0x0F];
			hex[--t] = HEX_CHARS[v >> 4 & 0x0F];
		}
		return new String(hex);
	}

	public static int copy(final InputStream is, final OutputStream os)
			throws IOException {
		final byte[] buffer = new byte[4096];
		long count = 0;
		int n = 0;
		while (-1 != (n = is.read(buffer))) {
			os.write(buffer, 0, n);
			count += n;
		}
		return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
	}

	public static byte[] hex2bytes(final String hex) {
		int j = hex.length();
		int i = j >> 1;
		final byte[] bytes = new byte[i];
		char v;
		while (i > 0) {
			v = hex.charAt(--j);
			bytes[--i] = (byte) (v - (v >= 'a' ? 'a' : '0'));
			v = hex.charAt(--j);
			bytes[i] |= (byte) (v - (v >= 'a' ? 'a' : '0')) << 4;
		}
		return bytes;
	}

	public static byte[] toBytes(final InputStream is) throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream(32486);
		copy(is, os);
		return os.toByteArray();
	}
}