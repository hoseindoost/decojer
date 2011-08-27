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
package org.decojer.cavaj.reader.javassist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javassist.bytecode.AttributeInfo;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.ExceptionTable;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Opcode;
import javassist.bytecode.StackMap;
import javassist.bytecode.StackMapTable;

import org.decojer.cavaj.model.AF;
import org.decojer.cavaj.model.CFG;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.F;
import org.decojer.cavaj.model.M;
import org.decojer.cavaj.model.MD;
import org.decojer.cavaj.model.T;
import org.decojer.cavaj.model.vm.intermediate.CompareType;
import org.decojer.cavaj.model.vm.intermediate.DataType;
import org.decojer.cavaj.model.vm.intermediate.Exc;
import org.decojer.cavaj.model.vm.intermediate.Operation;
import org.decojer.cavaj.model.vm.intermediate.Var;
import org.decojer.cavaj.model.vm.intermediate.operations.ADD;
import org.decojer.cavaj.model.vm.intermediate.operations.ALOAD;
import org.decojer.cavaj.model.vm.intermediate.operations.AND;
import org.decojer.cavaj.model.vm.intermediate.operations.ARRAYLENGTH;
import org.decojer.cavaj.model.vm.intermediate.operations.ASTORE;
import org.decojer.cavaj.model.vm.intermediate.operations.CHECKCAST;
import org.decojer.cavaj.model.vm.intermediate.operations.CMP;
import org.decojer.cavaj.model.vm.intermediate.operations.CONVERT;
import org.decojer.cavaj.model.vm.intermediate.operations.DIV;
import org.decojer.cavaj.model.vm.intermediate.operations.DUP;
import org.decojer.cavaj.model.vm.intermediate.operations.GET;
import org.decojer.cavaj.model.vm.intermediate.operations.GOTO;
import org.decojer.cavaj.model.vm.intermediate.operations.INC;
import org.decojer.cavaj.model.vm.intermediate.operations.INSTANCEOF;
import org.decojer.cavaj.model.vm.intermediate.operations.INVOKE;
import org.decojer.cavaj.model.vm.intermediate.operations.JCMP;
import org.decojer.cavaj.model.vm.intermediate.operations.JCND;
import org.decojer.cavaj.model.vm.intermediate.operations.JSR;
import org.decojer.cavaj.model.vm.intermediate.operations.LOAD;
import org.decojer.cavaj.model.vm.intermediate.operations.MONITOR;
import org.decojer.cavaj.model.vm.intermediate.operations.MUL;
import org.decojer.cavaj.model.vm.intermediate.operations.NEG;
import org.decojer.cavaj.model.vm.intermediate.operations.NEW;
import org.decojer.cavaj.model.vm.intermediate.operations.NEWARRAY;
import org.decojer.cavaj.model.vm.intermediate.operations.OR;
import org.decojer.cavaj.model.vm.intermediate.operations.POP;
import org.decojer.cavaj.model.vm.intermediate.operations.PUSH;
import org.decojer.cavaj.model.vm.intermediate.operations.PUT;
import org.decojer.cavaj.model.vm.intermediate.operations.REM;
import org.decojer.cavaj.model.vm.intermediate.operations.RET;
import org.decojer.cavaj.model.vm.intermediate.operations.RETURN;
import org.decojer.cavaj.model.vm.intermediate.operations.SHL;
import org.decojer.cavaj.model.vm.intermediate.operations.SHR;
import org.decojer.cavaj.model.vm.intermediate.operations.STORE;
import org.decojer.cavaj.model.vm.intermediate.operations.SUB;
import org.decojer.cavaj.model.vm.intermediate.operations.SWAP;
import org.decojer.cavaj.model.vm.intermediate.operations.SWITCH;
import org.decojer.cavaj.model.vm.intermediate.operations.THROW;
import org.decojer.cavaj.model.vm.intermediate.operations.XOR;
import org.ow2.asm.Opcodes;

/**
 * Read code attribute.
 * 
 * @author Andr� Pankraz
 */
public class ReadCodeAttribute {

	private final static Logger LOGGER = Logger
			.getLogger(ReadCodeAttribute.class.getName());

	private final DU du;

	private MD md;

	final ArrayList<Operation> operations = new ArrayList<Operation>();

	private final HashMap<Integer, Integer> pc2index = new HashMap<Integer, Integer>();

	private final HashMap<Integer, ArrayList<Object>> pc2unresolved = new HashMap<Integer, ArrayList<Object>>();

	/**
	 * Constructor.
	 * 
	 * @param du
	 *            decompilation unit
	 */
	public ReadCodeAttribute(final DU du) {
		assert du != null;

		this.du = du;
	}

	private int getPcIndex(final int pc) {
		final Integer index = this.pc2index.get(pc);
		if (index != null) {
			return index;
		}
		final int unresolvedIndex = -1 - this.pc2unresolved.size();
		this.pc2index.put(pc, unresolvedIndex);
		return unresolvedIndex;
	}

	private ArrayList<Object> getPcUnresolved(final int pc) {
		ArrayList<Object> unresolved = this.pc2unresolved.get(pc);
		if (unresolved == null) {
			unresolved = new ArrayList<Object>();
			this.pc2unresolved.put(pc, unresolved);
		}
		return unresolved;
	}

	/**
	 * Init and set method declaration.
	 * 
	 * @param md
	 *            method declaration
	 * @param codeAttribute
	 *            Javassist code attribute
	 */
	@SuppressWarnings("unchecked")
	public void initAndVisit(final MD md, final CodeAttribute codeAttribute) {
		this.md = md;

		this.operations.clear();
		this.pc2index.clear();
		this.pc2unresolved.clear();

		LineNumberAttribute lineNumberAttribute = null;
		// contains names and descriptors
		LocalVariableAttribute localVariableAttribute = null;
		// contains signatures, names are same
		LocalVariableAttribute localVariableTypeAttribute = null;
		StackMap stackMap;
		StackMapTable stackMapTable;
		for (final AttributeInfo attributeInfo : (List<AttributeInfo>) codeAttribute
				.getAttributes()) {
			final String attributeTag = attributeInfo.getName();
			if (LineNumberAttribute.tag.equals(attributeTag)) {
				lineNumberAttribute = (LineNumberAttribute) attributeInfo;
			} else if (LocalVariableAttribute.tag.equals(attributeTag)) {
				localVariableAttribute = (LocalVariableAttribute) attributeInfo;
			} else if (LocalVariableAttribute.typeTag.equals(attributeTag)) {
				localVariableTypeAttribute = (LocalVariableAttribute) attributeInfo;
			} else if (StackMap.tag.equals(attributeTag)) {
				stackMap = (StackMap) attributeInfo;
			} else if (StackMapTable.tag.equals(attributeTag)) {
				stackMapTable = (StackMapTable) attributeInfo;
			} else {
				LOGGER.warning("Unknown code attribute tag '" + attributeTag
						+ "' in '" + md + "'!");
			}
		}

		// read code
		final CodeReader codeReader = new CodeReader(codeAttribute.getCode());

		// init CFG with start BB
		final CFG cfg = new CFG(md, codeAttribute.getMaxLocals(),
				codeAttribute.getMaxStack());
		md.setCFG(cfg);

		final DU du = md.getTd().getT().getDu();
		final ConstPool constPool = codeAttribute.getConstPool();

		// wide operation following?
		// one of: iload, fload, aload, lload, dload, istore, fstore, astore,
		// lstore, dstore, or ret
		boolean wide = false;

		while (codeReader.isNext()) {
			final int opPc = codeReader.pc;

			visitPc(opPc);

			final int opcode = codeReader.readUnsignedByte();
			final int line = lineNumberAttribute == null ? -1
					: lineNumberAttribute.toLineNumber(opPc);

			int type = -1;
			int iValue = Integer.MIN_VALUE;
			Object oValue = null;

			switch (opcode) {
			/*******
			 * ADD *
			 *******/
			case Opcode.DADD:
				type = DataType.T_DOUBLE;
				// fall through
			case Opcode.FADD:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IADD:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LADD:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new ADD(opPc, opcode, line, type));
				break;
			/*********
			 * ALOAD *
			 *********/
			case Opcode.AALOAD:
				type = DataType.T_AREF;
				// fall through
			case Opcode.BALOAD:
				if (type < 0) {
					type = DataType.T_BOOLEAN;
				}
				// fall through
			case Opcode.CALOAD:
				if (type < 0) {
					type = DataType.T_CHAR;
				}
				// fall through
			case Opcode.DALOAD:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FALOAD:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IALOAD:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LALOAD:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				// fall through
			case Opcode.SALOAD:
				if (type < 0) {
					type = DataType.T_SHORT;
				}
				this.operations.add(new ALOAD(opPc, opcode, line, type));
				break;
			/*******
			 * AND *
			 *******/
			case Opcode.IAND:
				type = DataType.T_INT;
				// fall through
			case Opcode.LAND:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new AND(opPc, opcode, line, type));
				break;
			/***************
			 * ARRAYLENGTH *
			 ***************/
			case Opcode.ARRAYLENGTH:
				this.operations.add(new ARRAYLENGTH(opPc, opcode, line));
				break;
			/**********
			 * ASTORE *
			 **********/
			case Opcode.AASTORE:
				type = DataType.T_AREF;
				// fall through
			case Opcode.BASTORE:
				if (type < 0) {
					type = DataType.T_BOOLEAN;
				}
				// fall through
			case Opcode.CASTORE:
				if (type < 0) {
					type = DataType.T_CHAR;
				}
				// fall through
			case Opcode.DASTORE:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FASTORE:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IASTORE:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LASTORE:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				// fall through
			case Opcode.SASTORE:
				if (type < 0) {
					type = DataType.T_SHORT;
				}
				this.operations.add(new ASTORE(opPc, opcode, line, type));
				break;
			/**************
			 * CHECKCAST *
			 **************/
			case Opcode.CHECKCAST: {
				final int cpClassIndex = codeReader.readUnsignedShort();
				// cp arrays: "[L<classname>;" instead of "<classname>"!!!
				this.operations.add(new CHECKCAST(opPc, opcode, line,
						readType(constPool.getClassInfo(cpClassIndex))));
				break;
			}
			/*******
			 * CMP *
			 *******/
			case Opcode.DCMPG:
				type = DataType.T_DOUBLE;
				iValue = CMP.T_G;
				// fall through
			case Opcode.DCMPL:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = CMP.T_L;
				}
				// fall through
			case Opcode.FCMPG:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = CMP.T_G;
				}
				// fall through
			case Opcode.FCMPL:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = CMP.T_L;
				}
				// fall through
			case Opcode.LCMP:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = CMP.T_0;
				}
				this.operations.add(new CMP(opPc, opcode, line, type, iValue));
				break;
			/***********
			 * CONVERT *
			 ***********/
			case Opcode.D2F:
				type = DataType.T_DOUBLE;
				iValue = DataType.T_FLOAT;
				// fall through
			case Opcode.D2I:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = DataType.T_INT;
				}
				// fall through
			case Opcode.D2L:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = DataType.T_LONG;
				}
				// fall through
			case Opcode.F2D:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.F2I:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = DataType.T_INT;
				}
				// fall through
			case Opcode.F2L:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = DataType.T_LONG;
				}
				// fall through
			case Opcode.I2B:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_BYTE;
				}
				// fall through
			case Opcode.I2C:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_CHAR;
				}
				// fall through
			case Opcode.I2D:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.I2F:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.I2L:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_LONG;
				}
				// fall through
			case Opcode.I2S:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = DataType.T_SHORT;
				}
				// fall through
			case Opcode.L2D:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.L2F:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.L2I:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = DataType.T_INT;
				}
				this.operations.add(new CONVERT(opPc, opcode, line, type,
						iValue));
				break;
			/*******
			 * DIV *
			 *******/
			case Opcode.DDIV:
				type = DataType.T_DOUBLE;
				// fall through
			case Opcode.FDIV:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IDIV:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LDIV:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new DIV(opPc, opcode, line, type));
				break;
			/*******
			 * DUP *
			 *******/
			case Opcode.DUP:
				type = DUP.T_DUP;
				// fall through
			case Opcode.DUP_X1:
				if (type < 0) {
					type = DUP.T_DUP_X1;
				}
				// fall through
			case Opcode.DUP_X2:
				if (type < 0) {
					type = DUP.T_DUP_X2;
				}
				// fall through
			case Opcode.DUP2:
				if (type < 0) {
					type = DUP.T_DUP2;
				}
				// fall through
			case Opcode.DUP2_X1:
				if (type < 0) {
					type = DUP.T_DUP2_X1;
				}
				// fall through
			case Opcode.DUP2_X2:
				if (type < 0) {
					type = DUP.T_DUP2_X2;
				}
				this.operations.add(new DUP(opPc, opcode, line, type));
				break;
			/*******
			 * GET *
			 *******/
			case Opcode.GETFIELD:
			case Opcode.GETSTATIC: {
				final int cpFieldIndex = codeReader.readUnsignedShort();

				final T ownerT = readType(constPool
						.getFieldrefClassName(cpFieldIndex));
				final T t = du
						.getDescT(constPool.getFieldrefType(cpFieldIndex));
				final F f = ownerT.getF(
						constPool.getFieldrefName(cpFieldIndex), t);
				if (opcode == Opcode.GETSTATIC) {
					f.markAf(AF.STATIC);
				}
				this.operations.add(new GET(opPc, opcode, line, f));
				break;
			}
			/********
			 * GOTO *
			 ********/
			case Opcode.GOTO:
				type = 0;
				iValue = codeReader.readSignedShort();
				// fall through
			case Opcode.GOTO_W:
				if (type < 0) {
					iValue = codeReader.readSignedInt();
				}
				{
					final GOTO op = new GOTO(opPc, opcode, line);
					final int targetPc = opPc + iValue;
					final int pcIndex = getPcIndex(targetPc);
					op.setTargetPc(pcIndex);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
					this.operations.add(op);
				}
				break;
			/*******
			 * INC *
			 *******/
			case Opcode.IINC: {
				final int varIndex = codeReader.readUnsignedByte();
				final int constValue = codeReader.readUnsignedByte();
				this.operations.add(new INC(opPc, opcode, line, DataType.T_INT,
						varIndex, constValue));
				break;
			}
			/**************
			 * INSTANCEOF *
			 **************/
			case Opcode.INSTANCEOF: {
				final int cpClassIndex = codeReader.readUnsignedShort();
				this.operations.add(new INSTANCEOF(opPc, opcode, line,
						readType(constPool.getClassInfo(cpClassIndex))));
				break;
			}
			/**********
			 * INVOKE *
			 **********/
			case Opcode.INVOKEINTERFACE: {
				// interface method callout
				final int cpMethodIndex = codeReader.readUnsignedShort();
				codeReader.readUnsignedByte(); // count, unused
				codeReader.readUnsignedByte(); // reserved, unused

				final T invokeT = readType(constPool
						.getInterfaceMethodrefClassName(cpMethodIndex));
				invokeT.markAf(AF.INTERFACE);
				final M invokeM = invokeT.getM(
						constPool.getInterfaceMethodrefName(cpMethodIndex),
						constPool.getInterfaceMethodrefType(cpMethodIndex));

				this.operations.add(new INVOKE(opPc, opcode, line, invokeM,
						false));
				break;
			}
			case Opcode.INVOKESPECIAL:
				// constructor or supermethod callout
			case Opcode.INVOKEVIRTUAL:
			case Opcode.INVOKESTATIC: {
				final int cpMethodIndex = codeReader.readUnsignedShort();

				final T invokeT = readType(constPool
						.getMethodrefClassName(cpMethodIndex));
				final M invokeM = invokeT.getM(
						constPool.getMethodrefName(cpMethodIndex),
						constPool.getMethodrefType(cpMethodIndex));
				if (opcode == Opcode.INVOKESTATIC) {
					invokeM.markAf(AF.STATIC);
				}
				this.operations.add(new INVOKE(opPc, opcode, line, invokeM,
						opcode == Opcode.INVOKESPECIAL));
				break;
			}
			/********
			 * JCMP *
			 ********/
			case Opcode.IF_ACMPEQ:
				type = DataType.T_AREF;
				iValue = CompareType.T_EQ;
				// fall through
			case Opcode.IF_ACMPNE:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = CompareType.T_NE;
				}
				// fall through
			case Opcode.IF_ICMPEQ:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_EQ;
				}
				// fall through
			case Opcode.IF_ICMPGE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_GE;
				}
				// fall through
			case Opcode.IF_ICMPGT:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_GT;
				}
				// fall through
			case Opcode.IF_ICMPLE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_LE;
				}
				// fall through
			case Opcode.IF_ICMPLT:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_LT;
				}
				// fall through
			case Opcode.IF_ICMPNE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_NE;
				}
				{
					final JCMP op = new JCMP(opPc, opcode, line, type, iValue);
					final int targetPc = opPc + codeReader.readSignedShort();
					final int pcIndex = getPcIndex(targetPc);
					op.setTargetPc(pcIndex);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
					this.operations.add(op);
				}
				break;
			/********
			 * JCND *
			 ********/
			case Opcode.IFNULL:
				type = DataType.T_AREF;
				iValue = CompareType.T_EQ;
				// fall through
			case Opcode.IFNONNULL:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = CompareType.T_NE;
				}
				// fall through
			case Opcode.IFEQ:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_EQ;
				}
				// fall through
			case Opcode.IFGE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_GE;
				}
				// fall through
			case Opcode.IFGT:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_GT;
				}
				// fall through
			case Opcode.IFLE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_LE;
				}
				// fall through
			case Opcode.IFLT:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_LT;
				}
				// fall through
			case Opcode.IFNE:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = CompareType.T_NE;
				}
				{
					final JCND op = new JCND(opPc, opcode, line, type, iValue);
					final int targetPc = opPc + codeReader.readSignedShort();
					final int pcIndex = getPcIndex(targetPc);
					op.setTargetPc(pcIndex);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
					this.operations.add(op);
				}
				break;
			/*******
			 * JSR *
			 *******/
			case Opcode.JSR:
				type = 0;
				iValue = codeReader.readUnsignedShort();
				// fall through
			case Opcode.JSR_W:
				if (type < 0) {
					iValue = codeReader.readUnsignedInt();
				}
				{
					final JSR op = new JSR(opPc, opcode, line);
					final int targetPc = opPc + iValue;
					final int pcIndex = getPcIndex(targetPc);
					op.setTargetPc(pcIndex);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
					this.operations.add(op);
				}
				break;
			/********
			 * LOAD *
			 ********/
			case Opcode.ALOAD:
				type = DataType.T_AREF;
				// fall through
			case Opcode.DLOAD:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FLOAD:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.ILOAD:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LLOAD:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				iValue = wide ? codeReader.readUnsignedShort() : codeReader
						.readUnsignedByte();
				// fall through
			case Opcode.ALOAD_0:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 0;
				}
				// fall through
			case Opcode.DLOAD_0:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 0;
				}
				// fall through
			case Opcode.FLOAD_0:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 0;
				}
				// fall through
			case Opcode.ILOAD_0:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 0;
				}
				// fall through
			case Opcode.LLOAD_0:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 0;
				}
				// fall through
			case Opcode.ALOAD_1:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 1;
				}
				// fall through
			case Opcode.DLOAD_1:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 1;
				}
				// fall through
			case Opcode.FLOAD_1:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 1;
				}
				// fall through
			case Opcode.ILOAD_1:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 1;
				}
				// fall through
			case Opcode.LLOAD_1:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 1;
				}
				// fall through
			case Opcode.ALOAD_2:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 2;
				}
				// fall through
			case Opcode.DLOAD_2:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 2;
				}
				// fall through
			case Opcode.FLOAD_2:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 2;
				}
				// fall through
			case Opcode.ILOAD_2:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 2;
				}
				// fall through
			case Opcode.LLOAD_2:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 2;
				}
				// fall through
			case Opcode.ALOAD_3:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 3;
				}
				// fall through
			case Opcode.DLOAD_3:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 3;
				}
				// fall through
			case Opcode.FLOAD_3:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 3;
				}
				// fall through
			case Opcode.ILOAD_3:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 3;
				}
				// fall through
			case Opcode.LLOAD_3: {
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 3;
				}
				this.operations.add(new LOAD(opPc, opcode, line, type, iValue));
				break;
			}
			/***********
			 * MONITOR *
			 ***********/
			case Opcode.MONITORENTER:
				type = MONITOR.T_ENTER;
				// fall through
			case Opcode.MONITOREXIT:
				if (type < 0) {
					type = MONITOR.T_EXIT;
				}
				this.operations.add(new MONITOR(opPc, opcode, line, type));
				break;
			/*******
			 * MUL *
			 *******/
			case Opcode.DMUL:
				type = DataType.T_DOUBLE;
				// fall through
			case Opcode.FMUL:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IMUL:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LMUL:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new MUL(opPc, opcode, line, type));
				break;
			/*******
			 * NEG *
			 *******/
			case Opcode.DNEG:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FNEG:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.INEG:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LNEG:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new NEG(opPc, opcode, line, type));
				break;
			/*******
			 * NEW *
			 *******/
			case Opcode.NEW: {
				final int cpClassIndex = codeReader.readUnsignedShort();
				this.operations.add(new NEW(opPc, opcode, line,
						readType(constPool.getClassInfo(cpClassIndex))));
				break;
			}
			/************
			 * NEWARRAY *
			 ************/
			case Opcode.ANEWARRAY: {
				final int cpClassIndex = codeReader.readUnsignedShort();
				this.operations.add(new NEWARRAY(opPc, opcode, line,
						readType(constPool.getClassInfo(cpClassIndex)), 1));
				break;
			}
			case Opcode.NEWARRAY: {
				type = codeReader.readUnsignedByte();
				final String typeName = new String[] { null, null, null, null,
						boolean.class.getName(), char.class.getName(),
						float.class.getName(), double.class.getName(),
						byte.class.getName(), short.class.getName(),
						int.class.getName(), long.class.getName() }[type];
				this.operations.add(new NEWARRAY(opPc, opcode, line, du
						.getT(typeName), 1));
				break;
			}
			case Opcode.MULTIANEWARRAY: {
				final int cpClassIndex = codeReader.readUnsignedShort();
				final int dimensions = codeReader.readUnsignedByte();
				this.operations.add(new NEWARRAY(opPc, opcode, line,
						readType(constPool.getClassInfo(cpClassIndex)),
						dimensions));
				break;
			}
			/*******
			 * NOP *
			 *******/
			case Opcode.NOP:
				// ignore
				break;
			/******
			 * OR *
			 ******/
			case Opcode.IOR:
				type = DataType.T_INT;
				// fall through
			case Opcode.LOR:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new OR(opPc, opcode, line, type));
				break;
			/*******
			 * POP *
			 *******/
			case Opcode.POP:
				type = POP.T_POP;
				// fall through
			case Opcode.POP2:
				if (type < 0) {
					type = POP.T_POP2;
				}
				this.operations.add(new POP(opPc, opcode, line, type));
				break;
			/********
			 * PUSH *
			 ********/
			case Opcode.ACONST_NULL:
				type = DataType.T_AREF;
				oValue = null;
				// fall through
			case Opcode.BIPUSH:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = codeReader.readSignedByte();
				}
				// fall through
			case Opcode.SIPUSH:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = codeReader.readSignedShort();
				}
				// fall through
			case Opcode.LDC:
				if (type < 0) {
					final int ldcValueIndex = codeReader.readUnsignedByte();
					final int tag = constPool.getTag(ldcValueIndex);
					switch (constPool.getTag(ldcValueIndex)) {
					case ConstPool.CONST_Class:
						type = DataType.T_CLASS;
						oValue = readType(constPool.getClassInfo(ldcValueIndex));
						break;
					case ConstPool.CONST_Double:
						// Double / Long only with LDC2_W, but is OK here too
						type = DataType.T_DOUBLE;
						// fall through
					case ConstPool.CONST_Float:
						if (type < 0) {
							type = DataType.T_FLOAT;
						}
						// fall through
					case ConstPool.CONST_Integer:
						if (type < 0) {
							type = DataType.T_INT;
						}
						// fall through
					case ConstPool.CONST_Long:
						// Double / Long only with LDC2_W, but is OK here too
						if (type < 0) {
							type = DataType.T_LONG;
						}
						// fall through
					case ConstPool.CONST_String:
						if (type < 0) {
							type = DataType.T_STRING;
						}
						oValue = constPool.getLdcValue(ldcValueIndex);
						break;
					default:
						throw new RuntimeException("Unknown Const Pool Tag "
								+ tag + " for LDC!");
					}
				}
				// fall through
			case Opcode.LDC_W:
				// fall through
			case Opcode.LDC2_W:
				if (type < 0) {
					final int ldcValueIndex = codeReader.readUnsignedShort();
					final int tag = constPool.getTag(ldcValueIndex);
					switch (constPool.getTag(ldcValueIndex)) {
					case ConstPool.CONST_Class:
						type = DataType.T_CLASS;
						oValue = readType(constPool.getClassInfo(ldcValueIndex));
						break;
					case ConstPool.CONST_Double:
						type = DataType.T_DOUBLE;
						// fall through
					case ConstPool.CONST_Float:
						if (type < 0) {
							type = DataType.T_FLOAT;
						}
						// fall through
					case ConstPool.CONST_Integer:
						if (type < 0) {
							type = DataType.T_INT;
						}
						// fall through
					case ConstPool.CONST_Long:
						if (type < 0) {
							type = DataType.T_LONG;
						}
						// fall through
					case ConstPool.CONST_String:
						if (type < 0) {
							type = DataType.T_STRING;
						}
						oValue = constPool.getLdcValue(ldcValueIndex);
						break;
					default:
						throw new RuntimeException("Unknown Const Pool Tag "
								+ tag + " for LDC!");
					}
				}
				// fall through
			case Opcode.DCONST_0:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					oValue = 0D;
				}
				// fall through
			case Opcode.FCONST_0:
				if (type < 0) {
					type = DataType.T_FLOAT;
					oValue = 0;
				}
				// fall through
			case Opcode.ICONST_0:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 0;
				}
				// fall through
			case Opcode.LCONST_0:
				if (type < 0) {
					type = DataType.T_LONG;
					oValue = 0L;
				}
				// fall through
			case Opcode.DCONST_1:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					oValue = 1D;
				}
				// fall through
			case Opcode.FCONST_1:
				if (type < 0) {
					type = DataType.T_FLOAT;
					oValue = 1;
				}
				// fall through
			case Opcode.ICONST_1:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 1;
				}
				// fall through
			case Opcode.LCONST_1:
				if (type < 0) {
					type = DataType.T_LONG;
					oValue = 1L;
				}
				// fall through
			case Opcode.FCONST_2:
				if (type < 0) {
					type = DataType.T_FLOAT;
					oValue = 2;
				}
				// fall through
			case Opcode.ICONST_2:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 2;
				}
				// fall through
			case Opcode.ICONST_3:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 3;
				}
				// fall through
			case Opcode.ICONST_4:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 4;
				}
				// fall through
			case Opcode.ICONST_5:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = 5;
				}
				// fall through
			case Opcode.ICONST_M1:
				if (type < 0) {
					type = DataType.T_INT;
					oValue = -1;
				}
				this.operations.add(new PUSH(opPc, opcode, line, type, oValue));
				break;
			/*******
			 * PUT *
			 *******/
			case Opcode.PUTFIELD:
			case Opcode.PUTSTATIC: {
				final int cpFieldIndex = codeReader.readUnsignedShort();

				final T ownerT = readType(constPool
						.getFieldrefClassName(cpFieldIndex));
				final T t = du
						.getDescT(constPool.getFieldrefType(cpFieldIndex));
				final F f = ownerT.getF(
						constPool.getFieldrefName(cpFieldIndex), t);
				if (opcode == Opcode.PUTSTATIC) {
					f.markAf(AF.STATIC);
				}
				this.operations.add(new PUT(opPc, opcode, line, f));
				break;
			}
			/*******
			 * REM *
			 *******/
			case Opcode.DREM:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FREM:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IREM:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LREM:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new REM(opPc, opcode, line, type));
				break;
			/*******
			 * RET *
			 *******/
			case Opcode.RET: {
				final int varIndex = wide ? codeReader.readUnsignedShort()
						: codeReader.readUnsignedByte();
				this.operations.add(new RET(opPc, opcode, line, varIndex));
				break;
			}
			/**********
			 * RETURN *
			 **********/
			case Opcode.ARETURN:
				type = DataType.T_AREF;
				// fall through
			case Opcode.DRETURN:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FRETURN:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.IRETURN:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LRETURN:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				// fall through
			case Opcode.RETURN:
				if (type < 0) {
					type = DataType.T_VOID;
				}
				this.operations.add(new RETURN(opPc, opcode, line, type));
				break;
			/*********
			 * STORE *
			 *********/
			case Opcode.ASTORE:
				type = DataType.T_AREF;
				// fall through
			case Opcode.DSTORE:
				if (type < 0) {
					type = DataType.T_DOUBLE;
				}
				// fall through
			case Opcode.FSTORE:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.ISTORE:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LSTORE:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				iValue = wide ? codeReader.readUnsignedShort() : codeReader
						.readUnsignedByte();
				// fall through
			case Opcode.ASTORE_0:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 0;
				}
				// fall through
			case Opcode.DSTORE_0:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 0;
				}
				// fall through
			case Opcode.FSTORE_0:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 0;
				}
				// fall through
			case Opcode.ISTORE_0:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 0;
				}
				// fall through
			case Opcode.LSTORE_0:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 0;
				}
				// fall through
			case Opcode.ASTORE_1:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 1;
				}
				// fall through
			case Opcode.DSTORE_1:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 1;
				}
				// fall through
			case Opcode.FSTORE_1:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 1;
				}
				// fall through
			case Opcode.ISTORE_1:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 1;
				}
				// fall through
			case Opcode.LSTORE_1:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 1;
				}
				// fall through
			case Opcode.ASTORE_2:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 2;
				}
				// fall through
			case Opcode.DSTORE_2:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 2;
				}
				// fall through
			case Opcode.FSTORE_2:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 2;
				}
				// fall through
			case Opcode.ISTORE_2:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 2;
				}
				// fall through
			case Opcode.LSTORE_2:
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 2;
				}
				// fall through
			case Opcode.ASTORE_3:
				if (type < 0) {
					type = DataType.T_AREF;
					iValue = 3;
				}
				// fall through
			case Opcode.DSTORE_3:
				if (type < 0) {
					type = DataType.T_DOUBLE;
					iValue = 3;
				}
				// fall through
			case Opcode.FSTORE_3:
				if (type < 0) {
					type = DataType.T_FLOAT;
					iValue = 3;
				}
				// fall through
			case Opcode.ISTORE_3:
				if (type < 0) {
					type = DataType.T_INT;
					iValue = 3;
				}
				// fall through
			case Opcode.LSTORE_3: {
				if (type < 0) {
					type = DataType.T_LONG;
					iValue = 3;
				}
				this.operations
						.add(new STORE(opPc, opcode, line, type, iValue));
				break;
			}
			/*******
			 * SHL *
			 *******/
			case Opcode.ISHL:
				type = DataType.T_INT;
				// fall through
			case Opcode.LSHL:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new SHL(opPc, opcode, line, type));
				break;
			/*******
			 * SHR *
			 *******/
			case Opcode.ISHR:
			case Opcode.IUSHR:
				type = DataType.T_INT;
				// fall through
			case Opcode.LSHR:
			case Opcode.LUSHR:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new SHR(opPc, opcode, line, type,
						opcode == Opcode.IUSHR || opcode == Opcode.LUSHR));
				break;
			/*******
			 * SUB *
			 *******/
			case Opcode.DSUB:
				type = DataType.T_DOUBLE;
				// fall through
			case Opcode.FSUB:
				if (type < 0) {
					type = DataType.T_FLOAT;
				}
				// fall through
			case Opcode.ISUB:
				if (type < 0) {
					type = DataType.T_INT;
				}
				// fall through
			case Opcode.LSUB:
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new SUB(opPc, opcode, line, type));
				break;
			/********
			 * SWAP *
			 ********/
			case Opcode.SWAP:
				this.operations.add(new SWAP(opPc, opcode, line));
				break;
			/**********
			 * SWITCH *
			 **********/
			case Opcode.LOOKUPSWITCH: {
				// align
				if (codeReader.pc % 4 > 0) {
					codeReader.pc += 4 - codeReader.pc % 4;
				}
				final SWITCH op = new SWITCH(opPc, Opcodes.LOOKUPSWITCH, line);
				// default
				int targetPc = opPc + codeReader.readUnsignedInt();
				int pcIndex = getPcIndex(targetPc);
				op.setDefaultPc(pcIndex);
				if (pcIndex < 0) {
					getPcUnresolved(targetPc).add(op);
				}

				// map entries number
				final int npairs = codeReader.readUnsignedInt();

				final int[] caseKeys = new int[npairs];
				final int[] casePcs = new int[npairs];

				for (int i = 0; i < npairs; ++i) {
					caseKeys[i] = codeReader.readUnsignedInt();
					targetPc = opPc + codeReader.readUnsignedInt();
					casePcs[i] = pcIndex = getPcIndex(targetPc);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
				}
				op.setCaseKeys(caseKeys);
				op.setCasePcs(casePcs);
				this.operations.add(op);
				break;
			}
			case Opcode.TABLESWITCH: {
				// align
				if (codeReader.pc % 4 > 0) {
					codeReader.pc += 4 - codeReader.pc % 4;
				}
				final SWITCH op = new SWITCH(opPc, Opcodes.LOOKUPSWITCH, line);
				// default
				int targetPc = opPc + codeReader.readUnsignedInt();
				int pcIndex = getPcIndex(targetPc);
				op.setDefaultPc(pcIndex);
				if (pcIndex < 0) {
					getPcUnresolved(targetPc).add(op);
				}

				// map key boundaries
				final int caseLow = codeReader.readUnsignedInt();
				final int caseHigh = codeReader.readUnsignedInt();

				final int[] caseKeys = new int[caseHigh - caseLow + 1];
				final int[] casePcs = new int[caseHigh - caseLow + 1];

				for (int i = 0, caseValue = caseLow; caseValue <= caseHigh; ++caseValue, ++i) {
					caseKeys[i] = caseValue;
					targetPc = opPc + codeReader.readUnsignedInt();
					casePcs[i] = pcIndex = getPcIndex(targetPc);
					if (pcIndex < 0) {
						getPcUnresolved(targetPc).add(op);
					}
				}
				op.setCaseKeys(caseKeys);
				op.setCasePcs(casePcs);
				this.operations.add(op);
				break;
			}
			/*********
			 * THROW *
			 *********/
			case Opcode.ATHROW:
				this.operations.add(new THROW(opPc, opcode, line));
				break;
			/*******
			 * XOR *
			 *******/
			case Opcode.IXOR:
				type = DataType.T_INT;
				// fall through
			case Opcode.LXOR: {
				if (type < 0) {
					type = DataType.T_LONG;
				}
				this.operations.add(new XOR(opPc, opcode, line, type));
				break;
			}
			/*******
			 * WIDE *
			 *******/
			case Opcode.WIDE:
				wide = true;
				// just for once! reset wide after switch
				continue;
			default:
				throw new RuntimeException("Unknown jvm operation code '"
						+ opcode + "'!");
			}
			// reset wide
			wide = false;
		}
		visitPc(codeReader.pc);
		cfg.setOperations(this.operations.toArray(new Operation[this.operations
				.size()]));

		final ExceptionTable exceptionTable = codeAttribute.getExceptionTable();
		if (exceptionTable != null) {
			final ArrayList<Exc> excs = new ArrayList<Exc>();
			// preserve order
			final int exceptionTableSize = exceptionTable.size();
			for (int i = 0; i < exceptionTableSize; ++i) {
				final String catchName = constPool.getClassInfo(exceptionTable
						.catchType(i));
				// no array possible, name is OK here
				final T catchT = catchName == null ? null : du.getT(catchName);
				final Exc exc = new Exc(catchT);

				exc.setStartPc(this.pc2index.get(exceptionTable.startPc(i)));
				exc.setEndPc(this.pc2index.get(exceptionTable.endPc(i)));
				exc.setHandlerPc(this.pc2index.get(exceptionTable.handlerPc(i)));

				excs.add(exc);
			}
			if (excs.size() > 0) {
				cfg.setExcs(excs.toArray(new Exc[excs.size()]));
			}
		}
		readLocalVariables(localVariableAttribute, localVariableTypeAttribute);
		md.postProcessVars();
	}

	private void readLocalVariables(
			final LocalVariableAttribute localVariableAttribute,
			final LocalVariableAttribute localVariableTypeAttribute) {
		final HashMap<Integer, ArrayList<Var>> reg2vars = new HashMap<Integer, ArrayList<Var>>();
		if (localVariableAttribute != null) {
			final DU du = this.md.getTd().getT().getDu();
			// preserve order
			final int tableLength = localVariableAttribute.tableLength();
			for (int i = 0; i < tableLength; ++i) {
				final T varT = du
						.getDescT(localVariableAttribute.descriptor(i));
				final Var var = new Var(varT);

				var.setName(localVariableAttribute.variableName(i));
				var.setStartPc(this.pc2index.get(localVariableAttribute
						.startPc(i)));
				var.setEndPc(this.pc2index.get(localVariableAttribute
						.startPc(i) + localVariableAttribute.codeLength(i)));

				final int index = localVariableAttribute.index(i);

				ArrayList<Var> vars = reg2vars.get(index);
				if (vars == null) {
					vars = new ArrayList<Var>();
					reg2vars.put(index, vars);
				}
				vars.add(var);
			}
			if (reg2vars.size() > 0) {
				for (final Entry<Integer, ArrayList<Var>> entry : reg2vars
						.entrySet()) {
					final int reg = entry.getKey();
					for (final Var var : entry.getValue()) {
						this.md.addVar(reg, var);
					}
				}
			}
		}
		if (localVariableTypeAttribute != null) {
			// preserve order
			final int tableLength = localVariableTypeAttribute.tableLength();
			for (int i = 0; i < tableLength; ++i) {
				final Var var = this.md.getVar(
						localVariableTypeAttribute.index(i),
						localVariableTypeAttribute.startPc(i));
				if (var == null) {
					LOGGER.warning("Local variable type attribute '"
							+ localVariableTypeAttribute.index(i)
							+ "' without local variable attribute!");
					continue;
				}
				var.getTs().iterator().next()
						.setSignature(localVariableTypeAttribute.signature(i));
			}
		}
	}

	private T readType(final String classInfo) {
		if (classInfo == null) {
			return null;
		}
		// strange behaviour for classinfo:
		// arrays: normal descriptor (but with '.'):
		// [[I, [Ljava/lang/String;
		if (classInfo.charAt(0) == '[') {
			return this.du.getDescT(classInfo.replace('.', '/'));
		}
		// no arrays - class name:
		// org.decojer.cavaj.test.DecTestInner$1$1$1
		return this.du.getT(classInfo);
	}

	private void visitPc(final int pc) {
		final Integer pcIndex = this.pc2index.put(pc, this.operations.size());
		if (pcIndex == null) {
			// fresh new label, never referenced before
			return;
		}
		if (pcIndex > 0) {
			// visited before but is known?!
			LOGGER.warning("Pc '" + pc + "' is not unique, has old opPc '"
					+ this.operations.size() + "'!");
			return;
		}
		final int labelUnknownIndex = pcIndex;
		// unknown and has forward reference
		for (final Object o : this.pc2unresolved.get(pc)) {
			if (o instanceof GOTO) {
				((GOTO) o).setTargetPc(this.operations.size());
				continue;
			}
			if (o instanceof JCMP) {
				((JCMP) o).setTargetPc(this.operations.size());
				continue;
			}
			if (o instanceof JCND) {
				((JCND) o).setTargetPc(this.operations.size());
				continue;
			}
			if (o instanceof JSR) {
				((JSR) o).setTargetPc(this.operations.size());
				continue;
			}
			if (o instanceof SWITCH) {
				final SWITCH op = (SWITCH) o;
				if (labelUnknownIndex == op.getDefaultPc()) {
					op.setDefaultPc(this.operations.size());
				}
				final int[] keyTargets = op.getCasePcs();
				for (int i = keyTargets.length; i-- > 0;) {
					if (labelUnknownIndex == keyTargets[i]) {
						keyTargets[i] = this.operations.size();
					}
				}
				continue;
			}
			// cannot happen for Exc / Var here
		}
	}

}