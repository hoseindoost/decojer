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
package org.decojer.cavaj.transformer;

import static org.decojer.cavaj.util.Expressions.newInfixExpression;
import static org.decojer.cavaj.util.Expressions.newPrefixExpression;
import static org.decojer.cavaj.util.Expressions.wrap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.decojer.cavaj.model.AF;
import org.decojer.cavaj.model.BB;
import org.decojer.cavaj.model.BD;
import org.decojer.cavaj.model.CFG;
import org.decojer.cavaj.model.CU;
import org.decojer.cavaj.model.DU;
import org.decojer.cavaj.model.F;
import org.decojer.cavaj.model.FD;
import org.decojer.cavaj.model.M;
import org.decojer.cavaj.model.MD;
import org.decojer.cavaj.model.T;
import org.decojer.cavaj.model.TD;
import org.decojer.cavaj.model.code.Var;
import org.decojer.cavaj.model.code.op.ADD;
import org.decojer.cavaj.model.code.op.ALOAD;
import org.decojer.cavaj.model.code.op.AND;
import org.decojer.cavaj.model.code.op.ARRAYLENGTH;
import org.decojer.cavaj.model.code.op.ASTORE;
import org.decojer.cavaj.model.code.op.CAST;
import org.decojer.cavaj.model.code.op.CMP;
import org.decojer.cavaj.model.code.op.DIV;
import org.decojer.cavaj.model.code.op.DUP;
import org.decojer.cavaj.model.code.op.FILLARRAY;
import org.decojer.cavaj.model.code.op.GET;
import org.decojer.cavaj.model.code.op.INC;
import org.decojer.cavaj.model.code.op.INSTANCEOF;
import org.decojer.cavaj.model.code.op.INVOKE;
import org.decojer.cavaj.model.code.op.JCMP;
import org.decojer.cavaj.model.code.op.JCND;
import org.decojer.cavaj.model.code.op.JSR;
import org.decojer.cavaj.model.code.op.LOAD;
import org.decojer.cavaj.model.code.op.MONITOR;
import org.decojer.cavaj.model.code.op.MUL;
import org.decojer.cavaj.model.code.op.NEG;
import org.decojer.cavaj.model.code.op.NEW;
import org.decojer.cavaj.model.code.op.NEWARRAY;
import org.decojer.cavaj.model.code.op.OR;
import org.decojer.cavaj.model.code.op.Op;
import org.decojer.cavaj.model.code.op.POP;
import org.decojer.cavaj.model.code.op.PUSH;
import org.decojer.cavaj.model.code.op.PUT;
import org.decojer.cavaj.model.code.op.REM;
import org.decojer.cavaj.model.code.op.RETURN;
import org.decojer.cavaj.model.code.op.SHL;
import org.decojer.cavaj.model.code.op.SHR;
import org.decojer.cavaj.model.code.op.STORE;
import org.decojer.cavaj.model.code.op.SUB;
import org.decojer.cavaj.model.code.op.SWAP;
import org.decojer.cavaj.model.code.op.SWITCH;
import org.decojer.cavaj.model.code.op.THROW;
import org.decojer.cavaj.model.code.op.XOR;
import org.decojer.cavaj.util.Priority;
import org.decojer.cavaj.util.Types;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Transform CFG IVM to HLL Expression Statements.
 * 
 * @author Andr� Pankraz
 */
public class TrIvmCfg2JavaExprStmts {

	private final static Logger LOGGER = Logger.getLogger(TrIvmCfg2JavaExprStmts.class.getName());

	public static void transform(final CFG cfg) {
		new TrIvmCfg2JavaExprStmts(cfg).transform();
		cfg.calculatePostorder(); // blocks deleted...
	}

	public static void transform(final TD td) {
		// no parallelism! 2 shared instance variables: code and nextPc
		final List<BD> bds = td.getBds();
		for (int i = 0; i < bds.size(); ++i) {
			final BD bd = bds.get(i);
			if (!(bd instanceof MD)) {
				continue;
			}
			final CFG cfg = ((MD) bd).getCfg();
			if (cfg == null || cfg.isIgnore()) {
				continue;
			}
			try {
				transform(cfg);
			} catch (final Exception e) {
				LOGGER.log(Level.WARNING, "Cannot transform '" + cfg.getMd() + "'!", e);
				cfg.setError(true);
			}
		}
	}

	private final CFG cfg;

	private TrIvmCfg2JavaExprStmts(final CFG cfg) {
		this.cfg = cfg;
	}

	@SuppressWarnings("unchecked")
	private boolean convertToHLLIntermediate(final BB bb) {
		final List<Op> ops = bb.getOps();
		while (ops.size() != 0) {
			final Op op = ops.get(0);
			if (op.getInStackSize() > bb.getExpressionsSize()) {
				return false;
			}
			ops.remove(0);
			Statement statement = null;
			switch (op.getOptype()) {
			case ADD: {
				assert op instanceof ADD;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.PLUS,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case ALOAD: {
				assert op instanceof ALOAD;

				final ArrayAccess arrayAccess = getAst().newArrayAccess();
				arrayAccess.setIndex(wrap(bb.popExpression()));
				arrayAccess.setArray(wrap(bb.popExpression(), Priority.ARRAY_INDEX));
				bb.pushExpression(arrayAccess);
				break;
			}
			case AND: {
				assert op instanceof AND;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.AND,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case ARRAYLENGTH: {
				assert op instanceof ARRAYLENGTH;

				final Expression expression = bb.popExpression();
				if (expression instanceof Name) {
					// annotationsVisible.length
					bb.pushExpression(getAst().newQualifiedName((Name) wrap(expression),
							getAst().newSimpleName("length")));
				} else {
					// FieldAccess or MethodInvocation:
					// this.code.length, getInterfaces().length
					final FieldAccess fieldAccess = getAst().newFieldAccess();
					fieldAccess.setExpression(wrap(expression, Priority.MEMBER_ACCESS));
					fieldAccess.setName(getAst().newSimpleName("length"));
					bb.pushExpression(fieldAccess);
				}
				break;
			}
			case ASTORE: {
				assert op instanceof ASTORE;

				final Expression rightExpression = bb.popExpression();
				final Expression indexExpression = bb.popExpression();
				final Expression arrayRefExpression = bb.popExpression();
				if (arrayRefExpression instanceof ArrayCreation) {
					final ArrayCreation arrayCreation = (ArrayCreation) arrayRefExpression;
					ArrayInitializer arrayInitializer = arrayCreation.getInitializer();
					if (arrayInitializer == null) {
						arrayInitializer = getAst().newArrayInitializer();
						// TODO arrayCreation => switch to pure arrayInitializer
						arrayCreation.setInitializer(arrayInitializer);
						arrayCreation.dimensions().clear();
					}
					arrayInitializer.expressions().add(wrap(rightExpression));
				} else {
					final ArrayAccess arrayAccess = getAst().newArrayAccess();
					arrayAccess.setArray(wrap(arrayRefExpression, Priority.ARRAY_INDEX));
					arrayAccess.setIndex(wrap(indexExpression));
					final Assignment assignment = getAst().newAssignment();
					assignment.setLeftHandSide(arrayAccess);
					// TODO a = a +/- 1 => a++ / a--
					// TODO a = a <op> expr => a <op>= expr
					assignment.setRightHandSide(wrap(rightExpression, Priority.ASSIGNMENT));
					// inline assignment, DUP(_X1) -> PUT
					if (bb.getExpressionsSize() > 0 && bb.peekExpression() == rightExpression) {
						bb.popExpression();
						bb.pushExpression(assignment);
					} else {
						statement = getAst().newExpressionStatement(assignment);
					}
				}
				break;
			}
			case CAST: {
				final CAST cop = (CAST) op;
				final CastExpression castExpression = getAst().newCastExpression();
				castExpression.setType(Types.convertType(cop.getToT(), getTd(), getAst()));
				castExpression.setExpression(wrap(bb.popExpression(), Priority.TYPE_CAST));
				bb.pushExpression(castExpression);
				break;
			}
			case CMP: {
				assert op instanceof CMP;

				// pseudo expression for following JCND, not really the correct
				// answer for -1, 0, 1
				bb.pushExpression(newInfixExpression(InfixExpression.Operator.LESS_EQUALS,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case DIV: {
				assert op instanceof DIV;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.DIVIDE,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case DUP: {
				final DUP cop = (DUP) op;
				switch (cop.getDupType()) {
				case DUP.T_DUP:
					bb.pushExpression(bb.peekExpression());
					break;
				case DUP.T_DUP_X1: {
					final Expression e1 = bb.popExpression();
					final Expression e2 = bb.popExpression();
					bb.pushExpression(e1);
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					break;
				}
				case DUP.T_DUP_X2: {
					final Expression e1 = bb.popExpression();
					final Expression e2 = bb.popExpression();
					final Expression e3 = bb.popExpression();
					bb.pushExpression(e1);
					bb.pushExpression(e3);
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					break;
				}
				case DUP.T_DUP2: {
					final Expression e1 = bb.popExpression();
					final Expression e2 = bb.popExpression();
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					break;
				}
				case DUP.T_DUP2_X1: {
					final Expression e1 = bb.popExpression();
					final Expression e2 = bb.popExpression();
					final Expression e3 = bb.popExpression();
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					bb.pushExpression(e3);
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					break;
				}
				case DUP.T_DUP2_X2: {
					final Expression e1 = bb.popExpression();
					final Expression e2 = bb.popExpression();
					final Expression e3 = bb.popExpression();
					final Expression e4 = bb.popExpression();
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					bb.pushExpression(e4);
					bb.pushExpression(e3);
					bb.pushExpression(e2);
					bb.pushExpression(e1);
					break;
				}
				default:
					LOGGER.warning("Unknown dup type '" + cop.getDupType() + "'!");
				}
				break;
			}
			case FILLARRAY: {
				final FILLARRAY cop = (FILLARRAY) op;

				final Expression arrayRefExpression = bb.popExpression();
				// TODO bug...assignment happened already...pure type only here?

				final T t = this.cfg.getOutFrame(op).peek().getT();
				final T baseT = t.getBaseT();

				final ArrayCreation arrayCreation = getAst().newArrayCreation();
				arrayCreation.setType((ArrayType) Types.convertType(t, getTd(), getAst()));

				final ArrayInitializer arrayInitializer = getAst().newArrayInitializer();
				for (final Object value : cop.getValues()) {
					arrayInitializer.expressions().add(
							Types.convertLiteral(baseT, value, getTd(), getAst()));
				}
				arrayCreation.setInitializer(arrayInitializer);

				bb.pushExpression(arrayCreation);
				break;
			}
			case GET: {
				final GET cop = (GET) op;
				final F f = cop.getF();
				if (f.checkAf(AF.STATIC)) {
					final Name name = getAst().newQualifiedName(
							getTd().newTypeName(f.getT().getName()),
							getAst().newSimpleName(f.getName()));
					bb.pushExpression(name);
				} else {
					final FieldAccess fieldAccess = getAst().newFieldAccess();
					fieldAccess.setExpression(wrap(bb.popExpression(), Priority.MEMBER_ACCESS));
					fieldAccess.setName(getAst().newSimpleName(f.getName()));
					bb.pushExpression(fieldAccess);
				}
				break;
			}
			case GOTO: {
				// not really necessary, but important for
				// 1) correct opPc blocks
				// 2) line numbers

				// TODO put line number anywhere?
				// remember as pseudo statement? but problem with boolean ops
				break;
			}
			case INC: {
				final INC cop = (INC) op;
				final int value = cop.getValue();

				if (bb.getExpressionsSize() == 0) {
					if (value == 1 || value == -1) {
						final PrefixExpression prefixExpression = getAst().newPrefixExpression();
						prefixExpression
								.setOperator(value == 1 ? PrefixExpression.Operator.INCREMENT
										: PrefixExpression.Operator.DECREMENT);
						final String name = getVarName(cop.getReg(), cop.getPc());
						prefixExpression.setOperand(getAst().newSimpleName(name));
						statement = getAst().newExpressionStatement(prefixExpression);
					} else {
						LOGGER.warning("INC with value '" + value + "'!");
						// TODO
					}
				} else {
					LOGGER.warning("Inline INC with value '" + value + "'!");
					// TODO ... may be inline
				}

				break;
			}
			case INSTANCEOF: {
				final INSTANCEOF cop = (INSTANCEOF) op;
				final InstanceofExpression instanceofExpression = getAst()
						.newInstanceofExpression();
				instanceofExpression.setLeftOperand(wrap(bb.popExpression(), Priority.INSTANCEOF));
				instanceofExpression.setRightOperand(Types.convertType(cop.getT(), getTd(),
						getAst()));
				bb.pushExpression(instanceofExpression);
				break;
			}
			case INVOKE: {
				final INVOKE cop = (INVOKE) op;
				final M m = cop.getM();

				// read method invokation arguments
				final List<Expression> arguments = new ArrayList<Expression>();
				for (int i = 0; i < m.getParamTs().length; ++i) {
					arguments.add(wrap(bb.popExpression()));
				}
				Collections.reverse(arguments);

				final String mName = m.getName();

				final Expression methodExpression;
				if (cop.isDirect()) {
					final Expression expression = bb.popExpression();
					if ("<init>".equals(mName)) {
						methodExpression = null;
						if (expression instanceof ThisExpression) {
							final SuperConstructorInvocation superConstructorInvocation = getAst()
									.newSuperConstructorInvocation();
							if (arguments.size() == 0) {
								// implicit super callout, more checks possible but not necessary
								break;
							}
							superConstructorInvocation.arguments().addAll(arguments);
							bb.addStatement(superConstructorInvocation);
							break;
						}
						if (expression instanceof ClassInstanceCreation) {
							final ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;
							// ignore synthetic constructor parameter for inner classes:
							// none-static inner classes get extra constructor argument,
							// anonymous inner classes are static if context is static
							// (see SignatureDecompiler.decompileMethodTypes)
							// TODO

							classInstanceCreation.arguments().addAll(arguments);
							// normally there was a DUP in advance, don't use:
							// basicBlock.pushExpression(classInstanceCreation);
							break;
						}
						LOGGER.warning("Constructor method '<init> expects expression class 'ThisExpression' or 'ClassInstanceCreation' but is '"
								+ expression.getClass() + "' with value: " + expression);
						break;
					}
					if (expression instanceof ThisExpression) {
						final SuperMethodInvocation superMethodInvocation = getAst()
								.newSuperMethodInvocation();
						superMethodInvocation.setName(getAst().newSimpleName(mName));
						superMethodInvocation.arguments().addAll(arguments);
						methodExpression = superMethodInvocation;
					} else {
						methodExpression = null;
					}
				} else if (m.checkAf(AF.STATIC)) {
					final MethodInvocation methodInvocation = getAst().newMethodInvocation();
					methodInvocation.setExpression(getTd().newTypeName(m.getT().getName()));
					methodInvocation.setName(getAst().newSimpleName(mName));
					methodInvocation.arguments().addAll(arguments);
					methodExpression = methodInvocation;
				} else {
					stringAdd: if ("toString".equals(mName)
							&& ("java.lang.StringBuilder".equals(m.getT().getName()) || "java.lang.StringBuffer"
									.equals(m.getT().getName()))) {
						// jdk1.1.6:
						// new
						// StringBuffer(String.valueOf(super.toString())).append(" TEST").toString()
						// jdk1.3:
						// new StringBuffer().append(super.toString()).append(" TEST").toString();
						// jdk1.5.0:
						// new StringBuilder().append(super.toString()).append(" TEST").toString()
						// Eclipse (constructor argument fail?):
						// new
						// StringBuilder(String.valueOf(super.toString())).append(" TEST").toString()
						try {
							Expression stringExpression = null;
							Expression appendExpression = bb.peekExpression();
							do {
								final MethodInvocation methodInvocation = (MethodInvocation) appendExpression;
								if (!"append".equals(methodInvocation.getName().getIdentifier())
										|| methodInvocation.arguments().size() != 1) {
									break stringAdd;
								}
								appendExpression = methodInvocation.getExpression();
								if (stringExpression == null) {
									stringExpression = (Expression) methodInvocation.arguments()
											.get(0);
									continue;
								}
								stringExpression = newInfixExpression(
										InfixExpression.Operator.PLUS, stringExpression,
										(Expression) methodInvocation.arguments().get(0));
							} while (appendExpression instanceof MethodInvocation);
							final ClassInstanceCreation builder = (ClassInstanceCreation) appendExpression;
							// additional type check for pure append-chain not necessary
							if (builder.arguments().size() > 1) {
								break stringAdd;
							}
							if (builder.arguments().size() == 1) {
								stringExpression = newInfixExpression(
										InfixExpression.Operator.PLUS, stringExpression,
										(Expression) builder.arguments().get(0));
							}
							bb.popExpression();
							bb.pushExpression(stringExpression);
							break;
						} catch (final Exception e) {
							// rewrite to string-add didn't work
						}
					}
					final MethodInvocation methodInvocation = getAst().newMethodInvocation();
					methodInvocation.setExpression(wrap(bb.popExpression(), Priority.METHOD_CALL));
					methodInvocation.setName(getAst().newSimpleName(mName));
					methodInvocation.arguments().addAll(arguments);
					methodExpression = methodInvocation;
				}
				if (methodExpression != null) {
					final T returnType = m.getReturnT();
					if (void.class.getName().equals(returnType.getName())) {
						statement = getAst().newExpressionStatement(methodExpression);
					} else {
						bb.pushExpression(methodExpression);
					}
				}
				break;
			}
			case JCMP: {
				final JCMP cop = (JCMP) op;
				// invert all operators and switch out edge predicates
				final InfixExpression.Operator operator;
				switch (cop.getCmpType()) {
				case T_EQ:
					operator = InfixExpression.Operator.EQUALS;
					break;
				case T_GE:
					operator = InfixExpression.Operator.GREATER_EQUALS;
					break;
				case T_GT:
					operator = InfixExpression.Operator.GREATER;
					break;
				case T_LE:
					operator = InfixExpression.Operator.LESS_EQUALS;
					break;
				case T_LT:
					operator = InfixExpression.Operator.LESS;
					break;
				case T_NE:
					operator = InfixExpression.Operator.NOT_EQUALS;
					break;
				default:
					LOGGER.warning("Unknown cmp type '" + cop.getCmpType() + "'!");
					operator = null;
				}
				statement = getAst().newIfStatement();
				((IfStatement) statement).setExpression(newInfixExpression(operator,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case JCND: {
				final JCND cop = (JCND) op;
				Expression expression = bb.popExpression();
				// check preceding CMP
				if (expression instanceof InfixExpression
						&& ((InfixExpression) expression).getOperator() == InfixExpression.Operator.LESS_EQUALS) {
					// preceding compare expression (CMP result: -1 / 0 / 1)
					final InfixExpression.Operator operator;
					switch (cop.getCmpType()) {
					case T_EQ:
						operator = InfixExpression.Operator.EQUALS;
						break;
					case T_GE:
						operator = InfixExpression.Operator.GREATER_EQUALS;
						break;
					case T_GT:
						operator = InfixExpression.Operator.GREATER;
						break;
					case T_LE:
						operator = InfixExpression.Operator.LESS_EQUALS;
						break;
					case T_LT:
						operator = InfixExpression.Operator.LESS;
						break;
					case T_NE:
						operator = InfixExpression.Operator.NOT_EQUALS;
						break;
					default:
						LOGGER.warning("Unknown cmp type '" + cop.getCmpType() + "'!");
						operator = null;
					}
					((InfixExpression) expression).setOperator(operator);
				} else if (this.cfg.getInFrame(op).peek().getT().isReference()) {
					final InfixExpression.Operator operator;
					switch (cop.getCmpType()) {
					case T_EQ:
						operator = InfixExpression.Operator.EQUALS;
						break;
					case T_NE:
						operator = InfixExpression.Operator.NOT_EQUALS;
						break;
					default:
						LOGGER.warning("Unknown cmp type '" + cop.getCmpType()
								+ "' for null-expression!");
						operator = null;
					}
					final InfixExpression infixExpression = getAst().newInfixExpression();
					infixExpression.setLeftOperand(expression);
					infixExpression.setRightOperand(getAst().newNullLiteral());
					infixExpression.setOperator(operator);
					expression = infixExpression;
				} else if (this.cfg.getInFrame(op).peek().getT() == T.BOOLEAN) {
					// "!a" or "a == 0"?
					switch (cop.getCmpType()) {
					case T_EQ:
						// "== 0" means "is false"
						expression = newPrefixExpression(PrefixExpression.Operator.NOT, expression);
						break;
					case T_NE:
						// "!= 0" means "is true"
						break;
					default:
						LOGGER.warning("Unknown cmp type '" + cop.getCmpType()
								+ "' for boolean expression '" + expression + "'!");
					}
				} else {
					final InfixExpression.Operator operator;
					switch (cop.getCmpType()) {
					case T_EQ:
						operator = InfixExpression.Operator.EQUALS;
						break;
					case T_GE:
						operator = InfixExpression.Operator.GREATER_EQUALS;
						break;
					case T_GT:
						operator = InfixExpression.Operator.GREATER;
						break;
					case T_LE:
						operator = InfixExpression.Operator.LESS_EQUALS;
						break;
					case T_LT:
						operator = InfixExpression.Operator.LESS;
						break;
					case T_NE:
						operator = InfixExpression.Operator.NOT_EQUALS;
						break;
					default:
						LOGGER.warning("Unknown cmp type '" + cop.getCmpType()
								+ "' for 0-expression!");
						operator = null;
					}
					final InfixExpression infixExpression = getAst().newInfixExpression();
					infixExpression.setLeftOperand(expression);
					infixExpression.setRightOperand(getAst().newNumberLiteral("0"));
					infixExpression.setOperator(operator);
					expression = infixExpression;
				}
				statement = getAst().newIfStatement();
				((IfStatement) statement).setExpression(expression);
				break;
			}
			case JSR: {
				assert op instanceof JSR;
				// TODO
				break;
			}
			case LOAD: {
				final LOAD cop = (LOAD) op;

				final String name = getVarName(cop.getReg(), cop.getPc());
				if ("this".equals(name)) {
					bb.pushExpression(getAst().newThisExpression());
				} else {
					bb.pushExpression(getAst().newSimpleName(name));
				}
				break;
			}
			case MONITOR: {
				assert op instanceof MONITOR;

				bb.popExpression();
				break;
			}
			case MUL: {
				assert op instanceof MUL;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.TIMES,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case NEG: {
				assert op instanceof NEG;

				bb.pushExpression(newPrefixExpression(PrefixExpression.Operator.MINUS,
						bb.popExpression()));
				break;
			}
			case NEW: {
				final NEW cop = (NEW) op;

				final ClassInstanceCreation classInstanceCreation = getAst()
						.newClassInstanceCreation();

				final String thisName = getTd().getT().getName();
				final T t = cop.getT();
				final String newName = t.getName();
				if (newName.startsWith(thisName) && newName.length() >= thisName.length() + 2
						&& newName.charAt(thisName.length()) == '$') {
					inner: try {
						Integer.parseInt(newName.substring(thisName.length() + 1));

						final DU du = t.getDu();
						final TD td = du.getTd(newName);
						if (td != null) {
							// anonymous inner can only have a single interface
							// (with generic super "Object") or a super class
							final T[] interfaceTs = t.getInterfaceTs();
							switch (interfaceTs.length) {
							case 0:
								classInstanceCreation.setType(Types.convertType(t.getSuperT(),
										getTd(), getAst()));
								break;
							case 1:
								classInstanceCreation.setType(Types.convertType(interfaceTs[0],
										getTd(), getAst()));
								break;
							default:
								break inner;
							}
							if (td.getPd() == null) {
								getCu().addTd(td);
							}
							td.setPd(this.cfg.getMd());

							final AnonymousClassDeclaration anonymousClassDeclaration = getAst()
									.newAnonymousClassDeclaration();
							td.setTypeDeclaration(anonymousClassDeclaration);

							classInstanceCreation
									.setAnonymousClassDeclaration(anonymousClassDeclaration);

							bb.pushExpression(classInstanceCreation);
							break;
						}
					} catch (final NumberFormatException e) {
						// no int
					}
				}
				classInstanceCreation.setType(Types.convertType(cop.getT(), getTd(), getAst()));
				bb.pushExpression(classInstanceCreation);
				break;
			}
			case NEWARRAY: {
				final NEWARRAY cop = (NEWARRAY) op;
				final ArrayCreation arrayCreation = getAst().newArrayCreation();
				arrayCreation.setType(getAst().newArrayType(
						Types.convertType(cop.getT(), getTd(), getAst())));
				arrayCreation.dimensions().add(bb.popExpression());
				bb.pushExpression(arrayCreation);
				break;
			}
			case OR: {
				assert op instanceof OR;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.OR,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case POP: {
				final POP cop = (POP) op;
				switch (cop.getPopType()) {
				case POP.T_POP: {
					final Expression expression = bb.popExpression();
					statement = getAst().newExpressionStatement(expression);
					break;
				}
				case POP.T_POP2: {
					final Expression expression = bb.popExpression();
					statement = getAst().newExpressionStatement(expression);
					// CHECK pop another??? happens when? another statement?
					bb.popExpression();
					break;
				}
				default:
					LOGGER.warning("Unknown pop type '" + cop.getPopType() + "'!");
				}
				break;
			}
			case PUSH: {
				final PUSH cop = (PUSH) op;
				final Expression expr = Types.convertLiteral(
						this.cfg.getOutFrame(op).peek().getT(), cop.getValue(), getTd(), getAst());
				if (expr != null) {
					bb.pushExpression(expr);
				}
				break;
			}
			case PUT: {
				final PUT cop = (PUT) op;
				final Expression rightExpression = bb.popExpression();
				final F f = cop.getF();
				final M m = this.cfg.getMd().getM();
				fieldInit: if (m.getT() == f.getT()) {
					// set local field, could be initializer
					if (f.checkAf(AF.STATIC)) {
						if (!"<clinit>".equals(m.getName())) {
							break fieldInit;
						}
					} else {
						if (!"<init>".equals(m.getName())) {
							break fieldInit;
						}
						if (!(bb.peekExpression() instanceof ThisExpression)) {
							break fieldInit;
						}
						// multiple constructors with different signatures possible, all of them
						// contain the same field initializer code after super() - simply overwrite
					}
					if (this.cfg.getStartBb() != bb) {
						break fieldInit;
					}
					if (bb.getStatements().size() != 0) {
						break fieldInit;
					}
					if (f.checkAf(AF.SYNTHETIC)) {
						if (getCu().isIgnoreSynthetic()) {
							break; // ignore such assignments completely
						} else {
							break fieldInit; // not as field initializer
						}
					}
					try {
						final FD fd = getTd().getFd(f.getName());
						((VariableDeclarationFragment) ((FieldDeclaration) fd.getFieldDeclaration())
								.fragments().get(0)).setInitializer(wrap(rightExpression,
								Priority.ASSIGNMENT));
						if (!f.checkAf(AF.STATIC)) {
							bb.popExpression();
						}
						break;
					} catch (final Exception e) {
						// rewrite to field-initializer didn't work
					}
				}
				final Assignment assignment = getAst().newAssignment();
				// TODO a = a +/- 1 => a++ / a--
				// TODO a = a <op> expr => a <op>= expr
				assignment.setRightHandSide(wrap(rightExpression, Priority.ASSIGNMENT));

				if (f.checkAf(AF.STATIC)) {
					final Name name = getAst().newQualifiedName(
							getTd().newTypeName(f.getT().getName()),
							getAst().newSimpleName(f.getName()));
					assignment.setLeftHandSide(name);
				} else {
					final FieldAccess fieldAccess = getAst().newFieldAccess();
					fieldAccess.setExpression(wrap(bb.popExpression(), Priority.MEMBER_ACCESS));
					fieldAccess.setName(getAst().newSimpleName(f.getName()));
					assignment.setLeftHandSide(fieldAccess);
				}
				// inline assignment, DUP(_X1) -> PUT
				if (bb.getExpressionsSize() > 0 && bb.peekExpression() == rightExpression) {
					bb.popExpression();
					bb.pushExpression(assignment);
				} else {
					statement = getAst().newExpressionStatement(assignment);
				}
				break;
			}
			case REM: {
				assert op instanceof REM;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.REMAINDER,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case RETURN: {
				final RETURN cop = (RETURN) op;
				final ReturnStatement returnStatement = getAst().newReturnStatement();
				if (cop.getT() != T.VOID) {
					returnStatement.setExpression(wrap(bb.popExpression()));
				}
				statement = returnStatement;
				break;
			}
			case SHL: {
				assert op instanceof SHL;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.LEFT_SHIFT,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case SHR: {
				final SHR cop = (SHR) op;
				bb.pushExpression(newInfixExpression(
						cop.isUnsigned() ? InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED
								: InfixExpression.Operator.RIGHT_SHIFT_SIGNED, bb.popExpression(),
						bb.popExpression()));
				break;
			}
			case STORE: {
				final STORE cop = (STORE) op;

				final Expression rightExpression = bb.popExpression();
				final Assignment assignment = getAst().newAssignment();
				// TODO a = a +/- 1 => a++ / a--
				// TODO a = a <op> expr => a <op>= expr
				assignment.setRightHandSide(wrap(rightExpression, Priority.ASSIGNMENT));

				// TODO STORE.pc in DALVIK sucks now...multiple ops share pc
				String name = getVarName(cop.getReg(), ops.isEmpty() ? cop.getPc() + 1 : ops.get(0)
						.getPc());
				if ("this".equals(name)) {
					name = "_this"; // TODO can happen before synchronized(this)
				}
				assignment.setLeftHandSide(getAst().newSimpleName(name));
				// inline assignment, DUP -> STORE
				if (bb.getExpressionsSize() > 0 && bb.peekExpression() == rightExpression) {
					bb.popExpression();
					bb.pushExpression(assignment);
				} else {
					statement = getAst().newExpressionStatement(assignment);
				}
				break;
			}
			case SUB: {
				assert op instanceof SUB;

				bb.pushExpression(newInfixExpression(InfixExpression.Operator.MINUS,
						bb.popExpression(), bb.popExpression()));
				break;
			}
			case SWAP: {
				assert op instanceof SWAP;

				final Expression e1 = bb.popExpression();
				final Expression e2 = bb.popExpression();
				bb.pushExpression(e1);
				bb.pushExpression(e2);
				break;
			}
			case SWITCH: {
				assert op instanceof SWITCH;

				final SwitchStatement switchStatement = getAst().newSwitchStatement();
				switchStatement.setExpression(wrap(bb.popExpression()));
				statement = switchStatement;
				break;
			}
			case THROW: {
				assert op instanceof THROW;

				final ThrowStatement throwStatement = getAst().newThrowStatement();
				throwStatement.setExpression(wrap(bb.popExpression()));
				statement = throwStatement;
				break;
			}
			case XOR: {
				assert op instanceof XOR;

				final Expression expression = bb.popExpression();
				// "a ^ -1" => "~a"
				if (expression instanceof NumberLiteral
						&& ((NumberLiteral) expression).getToken().equals("-1")) {
					bb.pushExpression(newPrefixExpression(PrefixExpression.Operator.COMPLEMENT,
							bb.popExpression()));
				} else {
					bb.pushExpression(newInfixExpression(InfixExpression.Operator.XOR, expression,
							bb.popExpression()));
				}
				break;
			}
			default:
				throw new RuntimeException("Unknown intermediate vm operation '" + op + "'!");
			}
			if (statement != null) {
				bb.addStatement(statement);
			}
		}
		return true;
	}

	private AST getAst() {
		return getCu().getAst();
	}

	private CU getCu() {
		return getTd().getCu();
	}

	private TD getTd() {
		return this.cfg.getMd().getTd();
	}

	private String getVarName(final int reg, final int pc) {
		final Var var = this.cfg.getFrameVar(reg, pc);
		final String name = var == null ? null : var.getName();
		return name == null ? "r" + reg : name;
	}

	private void transform() {
		final List<BB> postorderedBbs = this.cfg.getPostorderedBbs();
		boolean changed = false;
		// for all nodes in postorder
		for (int i = 0; i < postorderedBbs.size(); ++i) {
			final BB bb = postorderedBbs.get(i);
			if (bb == null) {
				continue;
			}
			// initially convert all operations to statements and
			// expressions till possible stack underflow
			convertToHLLIntermediate(bb);
			if (bb.getSuccBbs().size() != 2) {
				// must be 2-way
				continue;
			}
			final List<Statement> stmts = bb.getStatements();
			if (stmts.size() == 0) {
				// no statements converted yet, try later
				continue;
			}
			final Object statement = stmts.get(stmts.size() - 1);
			if (!(statement instanceof IfStatement)) {
				continue;
			}
			// this can only be the last statement -> all operations converted
			BB trueBb = bb.getSuccBb(Boolean.TRUE);
			BB falseBb = bb.getSuccBb(Boolean.FALSE);
			// check for short-circuit compound boolean expression structure
			if (trueBb.getSuccBbs().size() == 2 && trueBb.getStatements().size() == 1
					&& trueBb.getPredBbs().size() == 1) {
				// another IfStatement in true direction
				final Object statement2 = trueBb.getStatements().get(0);
				if (!(statement2 instanceof IfStatement)) {
					continue;
				}
				final BB trueBb2 = trueBb.getSuccBb(Boolean.TRUE);
				final BB falseBb2 = trueBb.getSuccBb(Boolean.FALSE);
				if (falseBb == trueBb2) {
					// !A || B

					// ....|..
					// ....A..
					// ..t/.\f
					// ...B..|
					// .f/.\t|
					// ./...\|
					// F.....T

					// rewrite AST
					final Expression leftExpression = ((IfStatement) statement).getExpression();
					final Expression rightExpression = ((IfStatement) statement2).getExpression();
					((IfStatement) statement).setExpression(newInfixExpression(
							InfixExpression.Operator.CONDITIONAL_OR, rightExpression,
							newPrefixExpression(PrefixExpression.Operator.NOT, leftExpression)));
					// rewrite CFG
					trueBb.remove();
					bb.setSuccValue(trueBb2, Boolean.TRUE);
					bb.addSucc(falseBb2, Boolean.FALSE);
					// for conditional expression stage
					trueBb = trueBb2;
					falseBb = falseBb2;
					changed = true;
				} else if (falseBb == falseBb2) {
					// A && B

					// ..|....
					// ..A....
					// f/.\t..
					// |..B...
					// |f/.\t.
					// |/...\.
					// F.....T

					// rewrite AST
					final Expression leftExpression = ((IfStatement) statement).getExpression();
					final Expression rightExpression = ((IfStatement) statement2).getExpression();
					((IfStatement) statement).setExpression(newInfixExpression(
							InfixExpression.Operator.CONDITIONAL_AND, rightExpression,
							leftExpression));
					// rewrite CFG
					trueBb.remove();
					bb.addSucc(trueBb2, Boolean.TRUE);
					// for conditional expression stage
					trueBb = trueBb2;
					changed = true;
				}
			} else if (falseBb.getSuccBbs().size() == 2 && falseBb.getStatements().size() == 1
					&& falseBb.getPredBbs().size() == 1) {
				// another IfStatement in false direction
				final Object statement2 = falseBb.getStatements().get(0);
				if (!(statement2 instanceof IfStatement)) {
					continue;
				}
				final BB trueBb2 = falseBb.getSuccBb(Boolean.TRUE);
				final BB falseBb2 = falseBb.getSuccBb(Boolean.FALSE);
				if (trueBb == trueBb2) {
					// A || B

					// ....|..
					// ....A..
					// ..f/.\t
					// ...B..|
					// .f/.\t|
					// ./...\|
					// F.....T

					// rewrite AST
					final Expression leftExpression = ((IfStatement) statement).getExpression();
					final Expression rightExpression = ((IfStatement) statement2).getExpression();
					((IfStatement) statement).setExpression(newInfixExpression(
							InfixExpression.Operator.CONDITIONAL_OR, rightExpression,
							leftExpression));
					// rewrite CFG
					falseBb.remove();
					bb.addSucc(falseBb2, Boolean.FALSE);
					// for conditional expression stage
					falseBb = falseBb2;
					changed = true;
				} else if (trueBb == falseBb2) {
					// !A && B

					// ..|....
					// ..A....
					// t/.\f..
					// |..B...
					// |f/.\t.
					// |/...\.
					// F.....T

					// rewrite AST
					final Expression leftExpression = ((IfStatement) statement).getExpression();
					final Expression rightExpression = ((IfStatement) statement2).getExpression();
					((IfStatement) statement).setExpression(newInfixExpression(
							InfixExpression.Operator.CONDITIONAL_AND, rightExpression,
							newPrefixExpression(PrefixExpression.Operator.NOT, leftExpression)));
					// rewrite CFG
					falseBb.remove();
					bb.setSuccValue(falseBb2, Boolean.FALSE);
					bb.addSucc(trueBb2, Boolean.TRUE);
					// for conditional expression stage
					trueBb = trueBb2;
					falseBb = falseBb2;
					changed = true;
				}
			}
			// check for conditional expression structure
			if (trueBb.getPredBbs().size() == 1 && falseBb.getPredBbs().size() == 1
					&& trueBb.isExpression() && falseBb.isExpression()) {
				final Collection<BB> trueSuccessors = trueBb.getSuccBbs();
				if (trueSuccessors.size() != 1) {
					continue;
				}
				final Collection<BB> falseSuccessors = falseBb.getSuccBbs();
				if (falseSuccessors.size() != 1) {
					continue;
				}
				final BB trueBb2 = trueSuccessors.iterator().next();
				final BB falseBb2 = falseSuccessors.iterator().next();
				if (trueBb2 != falseBb2) {
					continue;
				}
				if (trueBb2.getPredBbs().size() != 2) {
					continue;
				}
				final Expression trueExpression = trueBb.peekExpression();
				final Expression falseExpression = falseBb.peekExpression();

				Expression expression = ((IfStatement) statement).getExpression();
				rewrite: if ((trueExpression instanceof BooleanLiteral || trueExpression instanceof NumberLiteral)
						&& (falseExpression instanceof BooleanLiteral || falseExpression instanceof NumberLiteral)) {
					// expressions: expression ? true : false => a
					// TODO NumberLiteral necessary until Data Flow Analysis works
					if (trueExpression instanceof BooleanLiteral
							&& !((BooleanLiteral) trueExpression).booleanValue()
							|| trueExpression instanceof NumberLiteral
							&& ((NumberLiteral) trueExpression).getToken().equals("0")) {
						expression = newPrefixExpression(PrefixExpression.Operator.NOT, expression);
					}
				} else {
					classLiteral: if (expression instanceof InfixExpression) {
						// Class-literals unknown in pre JDK 1.5 bytecode
						// (only primitive wrappers have constants like
						// getstatic java.lang.Void.TYPE : java.lang.Class)
						// ...construct Class-literals with synthetic local method:
						// static synthetic java.lang.Class class$(java.lang.String x0);
						// ...and cache this Class-literals in synthetic local fields:
						// static synthetic java.lang.Class class$java$lang$String;
						// static synthetic java.lang.Class array$$I;
						// resulting conditional code:
						// DecTestFields.array$$I != null ? DecTestFields.array$$I :
						// (DecTestFields.array$$I = DecTestFields.class$("[[I"))
						// ...convert too: int[][].class
						final InfixExpression equalsExpression = (InfixExpression) expression;
						if (!(equalsExpression.getRightOperand() instanceof NullLiteral)) {
							break classLiteral;
						}
						try {
							// JDK 1.1.6 and 1.4 differ in branch order
							final Assignment assignment = (Assignment) (trueExpression instanceof Assignment ? trueExpression
									: falseExpression);
							final MethodInvocation methodInvocation = (MethodInvocation) assignment
									.getRightHandSide();
							if (!"class$".equals(methodInvocation.getName().getIdentifier())) {
								break classLiteral;
							}
							if (getTd().getVersion() >= 49) {
								LOGGER.warning("Unexpected class literal code with class$() in >= JDK 5 code!");
							}
							final String classInfo = ((StringLiteral) methodInvocation.arguments()
									.get(0)).getLiteralValue();
							// strange behaviour for classinfo:
							// arrays: normal descriptor (but with '.')
							// no arrays - class name
							final DU du = getTd().getT().getDu();
							final T literalT = classInfo.charAt(0) == '[' ? du.getDescT(classInfo
									.replace('.', '/')) : du.getT(classInfo);
							expression = Types.convertLiteral(du.getT(Class.class), literalT,
									getTd(), getAst());
							break rewrite;
						} catch (final Exception e) {
							// rewrite to class literal didn't work
						}
					}
					// expressions: expression ? trueExpression : falseExpression
					final ConditionalExpression conditionalExpression = getAst()
							.newConditionalExpression();
					conditionalExpression.setExpression(wrap(expression, Priority.CONDITIONAL));
					conditionalExpression.setThenExpression(wrap(trueExpression,
							Priority.CONDITIONAL));
					conditionalExpression.setElseExpression(wrap(falseExpression,
							Priority.CONDITIONAL));
					expression = conditionalExpression;
				}
				// is conditional expression, modify graph
				// remove IfStatement
				stmts.remove(stmts.size() - 1);
				// push new conditional expression, here only "a ? true : false" as "a"
				bb.pushExpression(expression);
				// copy successor values to bbNode
				bb.copyContent(trueBb2);
				// again convert operations, stack underflow might be solved
				changed = true;
				// first preserve successors...
				trueBb2.moveSuccBbs(bb);
				// then remove basic blocks
				trueBb.remove();
				falseBb.remove();
				trueBb2.remove();
			}
			if (changed) {
				changed = false;
				--i;
			}
		}
	}

}