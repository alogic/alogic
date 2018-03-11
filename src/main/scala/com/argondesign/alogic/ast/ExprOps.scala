////////////////////////////////////////////////////////////////////////////////
// Argon Design Ltd. Project P8009 Alogic
// Copyright (c) 2017-2018 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// Module: Alogic Compiler
// Author: Geza Lore
//
// DESCRIPTION:
//
// Common members of ast.Trees.Expr
// These are factored out into a separate file to keep ast.Trees readable
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import scala.language.implicitConversions
import scala.math.BigInt.int2bigInt

import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.transform.ConstantFold

import com.argondesign.alogic.util.unreachable

import Trees._

trait ExprOps { this: Expr =>

  private final def makeExprBinary(op: String, rhs: Expr) = {
    val expr = ExprBinary(this, op, rhs)
    if (hasLoc) {
      if (!rhs.hasLoc) {
        rhs withLoc loc
      }
      expr withLoc loc
    } else {
      expr
    }
  }

  private final def makeExprAtCall(name: String, args: Expr*) = {
    val expr = ExprAtCall(name, args.toList)
    if (hasLoc) {
      for (arg <- args if !arg.hasLoc) {
        arg withLoc loc
      }
      expr withLoc loc
    } else {
      expr
    }
  }

  // Helpers to easily combine expression trees manually with other expressions
  final def *(rhs: Expr): Expr = makeExprBinary("*", rhs)
  final def /(rhs: Expr): Expr = makeExprBinary("/", rhs)
  final def %(rhs: Expr): Expr = makeExprBinary("%", rhs)
  final def +(rhs: Expr): Expr = makeExprBinary("+", rhs)
  final def -(rhs: Expr): Expr = makeExprBinary("-", rhs)
  final def <<(rhs: Expr): Expr = makeExprBinary("<<", rhs)
  final def >>(rhs: Expr): Expr = makeExprBinary(">>", rhs)
  final def <<<(rhs: Expr): Expr = makeExprBinary("<<<", rhs)
  final def >>>(rhs: Expr): Expr = makeExprBinary(">>>", rhs)
  final def &(rhs: Expr): Expr = makeExprBinary("&", rhs)
  final def ^(rhs: Expr): Expr = makeExprBinary("^", rhs)
  final def ~^(rhs: Expr): Expr = makeExprBinary("~^", rhs)
  final def |(rhs: Expr): Expr = makeExprBinary("|", rhs)
  final def &&(rhs: Expr): Expr = makeExprBinary("&&", rhs)
  final def ||(rhs: Expr): Expr = makeExprBinary("||", rhs)

  final def *(rhs: Int): Expr = makeExprBinary("*", Expr(rhs))
  final def /(rhs: Int): Expr = makeExprBinary("/", Expr(rhs))
  final def %(rhs: Int): Expr = makeExprBinary("%", Expr(rhs))
  final def +(rhs: Int): Expr = makeExprBinary("+", Expr(rhs))
  final def -(rhs: Int): Expr = makeExprBinary("-", Expr(rhs))
  final def <<(rhs: Int): Expr = makeExprBinary("<<", Expr(rhs))
  final def >>(rhs: Int): Expr = makeExprBinary(">>", Expr(rhs))
  final def <<<(rhs: Int): Expr = makeExprBinary("<<<", Expr(rhs))
  final def >>>(rhs: Int): Expr = makeExprBinary(">>>", Expr(rhs))
  final def &(rhs: Int): Expr = makeExprBinary("&", Expr(rhs))
  final def ^(rhs: Int): Expr = makeExprBinary("^", Expr(rhs))
  final def ~^(rhs: Int): Expr = makeExprBinary("~^", Expr(rhs))
  final def |(rhs: Int): Expr = makeExprBinary("|", Expr(rhs))
  final def &&(rhs: Int): Expr = makeExprBinary("&&", Expr(rhs))
  final def ||(rhs: Int): Expr = makeExprBinary("||", Expr(rhs))

  final def max(rhs: Expr): Expr = makeExprAtCall("max", this, rhs)
  final def max(rhs: Int): Expr = makeExprAtCall("max", this, Expr(rhs))

  // Is this expression shaped as a valid type expression
  lazy val isTypeExpr: Boolean = this forall {
    case _: ExprType         => true
    case _: ExprRef          => true
    case ExprSelect(expr, _) => expr.isTypeExpr
    case _                   => false
  }

  // Is this expression shaped as a valid lvalue expression
  lazy val isLValueExpr: Boolean = this forall {
    case _: ExprRef               => true
    case ExprIndex(expr, _)       => expr.isLValueExpr
    case ExprSlice(expr, _, _, _) => expr.isLValueExpr
    case ExprSelect(expr, _)      => expr.isLValueExpr
    case ExprCat(parts)           => parts forall { _.isLValueExpr }
    case _                        => false
  }

  // Is this expression shaped as a valid port reference expression
  lazy val isPortRefExpr: Boolean = this match {
    case _: ExprRef                => true
    case ExprSelect(_: ExprRef, _) => true
    case _                         => false
  }

  // Is this expression a known constant
  def isKnownConst: Boolean = this match {
    case _: ExprNum  => true
    case _: ExprInt  => true
    case _: ExprStr  => true
    case _: ExprType => true
    case ExprRef(Sym(symbol)) =>
      symbol.denot.kind match {
        case _: TypeConst => true
        case _            => false
      }
    case ExprUnary(_, expr)      => expr.isKnownConst
    case ExprBinary(lhs, _, rhs) => lhs.isKnownConst && rhs.isKnownConst
    case ExprTernary(cond, thenExpr, elseExpr) =>
      cond.isKnownConst && thenExpr.isKnownConst && elseExpr.isKnownConst
    case ExprRep(count, expr)   => count.isKnownConst && expr.isKnownConst
    case ExprCat(parts)         => parts forall { _.isKnownConst }
    case ExprIndex(expr, index) => expr.isKnownConst && index.isKnownConst
    case ExprSlice(expr, lidx, op, ridx) =>
      expr.isKnownConst && lidx.isKnownConst && ridx.isKnownConst
    case ExprSelect(expr, _) => expr.isKnownConst
    case _                   => false
  }

  // Simplify this expression
  def simplify(implicit cc: CompilerContext): Expr = {
    this rewrite { new ConstantFold } match {
      case expr: Expr => expr
      case _          => unreachable
    }
  }

  // Value of this expression if it can be determined right now, otherwise None
  def value(implicit cc: CompilerContext): Option[BigInt] = simplify match {
    // TODO: follow constants
    case ExprNum(_, value)    => Some(value)
    case ExprInt(_, _, value) => Some(value)
    case _                    => None
  }

}

trait ObjectExprOps {
  // Helpers to easily create ExprNum from integers
  final def apply(n: Int): ExprNum = ExprNum(true, n)
  final def apply(n: BigInt): ExprNum = ExprNum(true, n)

  object ImplicitConversions {
    // Implicit conversion for Int to ExprNum
    implicit final def int2ExprNum(n: Int): ExprNum = apply(n)
  }

  // And extractor so we can match against the the same as above
  final def unapply(num: ExprNum): Option[Int] = if (num.signed) Some(num.value.toInt) else None

  // Extractors to match operators naturally
  final object * {
    def unapply(expr: ExprBinary) = if (expr.op == "*") Some((expr.lhs, expr.rhs)) else None
  }
  final object / {
    def unapply(expr: ExprBinary) = if (expr.op == "/") Some((expr.lhs, expr.rhs)) else None
  }
  final object % {
    def unapply(expr: ExprBinary) = if (expr.op == "%") Some((expr.lhs, expr.rhs)) else None
  }
  final object + {
    def unapply(expr: ExprBinary) = if (expr.op == "+") Some((expr.lhs, expr.rhs)) else None
  }
  final object - {
    def unapply(expr: ExprBinary) = if (expr.op == "-") Some((expr.lhs, expr.rhs)) else None
  }
  final object << {
    def unapply(expr: ExprBinary) = if (expr.op == "<<") Some((expr.lhs, expr.rhs)) else None
  }
  final object >> {
    def unapply(expr: ExprBinary) = if (expr.op == ">>") Some((expr.lhs, expr.rhs)) else None
  }
  final object >>> {
    def unapply(expr: ExprBinary) = if (expr.op == ">>>") Some((expr.lhs, expr.rhs)) else None
  }
  final object & {
    def unapply(expr: ExprBinary) = if (expr.op == "&") Some((expr.lhs, expr.rhs)) else None
  }
  final object ^ {
    def unapply(expr: ExprBinary) = if (expr.op == "^") Some((expr.lhs, expr.rhs)) else None
  }
  final object | {
    def unapply(expr: ExprBinary) = if (expr.op == "|") Some((expr.lhs, expr.rhs)) else None
  }
  final object && {
    def unapply(expr: ExprBinary) = if (expr.op == "&&") Some((expr.lhs, expr.rhs)) else None
  }
  final object || {
    def unapply(expr: ExprBinary) = if (expr.op == "||") Some((expr.lhs, expr.rhs)) else None
  }
}