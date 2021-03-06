////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Common members of ast.Trees.Expr
// These are factored out into a separate file to keep ast.Trees readable
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.builtins._
import com.argondesign.alogic.core.Bindings
import com.argondesign.alogic.core.Symbol
import com.argondesign.alogic.core.TypeAssigner
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.lib.Math.clog2
import com.argondesign.alogic.transform.ReplaceTermRefs
import com.argondesign.alogic.transform.SimplifyExpr
import com.argondesign.alogic.util.PartialMatch._
import com.argondesign.alogic.util.unreachable

import scala.language.implicitConversions
import scala.math.BigInt.int2bigInt

trait ExprOps { this: Expr =>

  final private def fix(tree: Tree): tree.type = {
    if (hasLoc) {
      tree withLoc loc
    }
    if (hasTpe) {
      TypeAssigner(tree)
    }
    tree
  }

  final private def mkBinary(op: String, rhs: Expr) = {
    fix(ExprBinary(this, op, rhs))
  }

  final private def mkSized(v: Int) = {
    if (tpe.underlying.isNum) {
      fix(ExprNum(tpe.isSigned, v))
    } else {
      fix(ExprInt(tpe.isSigned, tpe.width.toInt, v))
    }
  }

  final private def mkIndex(idx: Long) = {
    fix(ExprInt(false, (clog2(tpe.shapeIter.next()) max 1).toInt, idx))
  }

  final private def mkSliceLength(idx: Long) = {
    fix(ExprInt(false, clog2(tpe.shapeIter.next() + 1), idx))
  }

  // Helpers to easily combine expression trees manually with other expressions
  final def *(rhs: Expr): ExprBinary = mkBinary("*", rhs)
  final def /(rhs: Expr): ExprBinary = mkBinary("/", rhs)
  final def %(rhs: Expr): ExprBinary = mkBinary("%", rhs)
  final def +(rhs: Expr): ExprBinary = mkBinary("+", rhs)
  final def -(rhs: Expr): ExprBinary = mkBinary("-", rhs)
  final def <<(rhs: Expr): ExprBinary = mkBinary("<<", rhs)
  final def >>(rhs: Expr): ExprBinary = mkBinary(">>", rhs)
  final def <<<(rhs: Expr): ExprBinary = mkBinary("<<<", rhs)
  final def >>>(rhs: Expr): ExprBinary = mkBinary(">>>", rhs)
  final def &(rhs: Expr): ExprBinary = mkBinary("&", rhs)
  final def ^(rhs: Expr): ExprBinary = mkBinary("^", rhs)
  final def |(rhs: Expr): ExprBinary = mkBinary("|", rhs)
  final def &&(rhs: Expr): ExprBinary = mkBinary("&&", rhs)
  final def ||(rhs: Expr): ExprBinary = mkBinary("||", rhs)
  final def <(rhs: Expr): ExprBinary = mkBinary("<", rhs)
  final def <=(rhs: Expr): ExprBinary = mkBinary("<=", rhs)
  final def >(rhs: Expr): ExprBinary = mkBinary(">", rhs)
  final def >=(rhs: Expr): ExprBinary = mkBinary(">=", rhs)

  final def *(rhs: Int): ExprBinary = this * mkSized(rhs)
  final def /(rhs: Int): ExprBinary = this / mkSized(rhs)
  final def %(rhs: Int): ExprBinary = this % mkSized(rhs)
  final def +(rhs: Int): ExprBinary = this + mkSized(rhs)
  final def -(rhs: Int): ExprBinary = this - mkSized(rhs)
  final def <<(rhs: Int): ExprBinary = this << fix(Expr(rhs))
  final def >>(rhs: Int): ExprBinary = this >> fix(Expr(rhs))
  final def <<<(rhs: Int): ExprBinary = this <<< fix(Expr(rhs))
  final def >>>(rhs: Int): ExprBinary = this >>> fix(Expr(rhs))
  final def &(rhs: Int): ExprBinary = this & mkSized(rhs)
  final def ^(rhs: Int): ExprBinary = this ^ mkSized(rhs)
  final def |(rhs: Int): ExprBinary = this | mkSized(rhs)
  final def &&(rhs: Int): ExprBinary = this && fix(Expr(rhs))
  final def ||(rhs: Int): ExprBinary = this || fix(Expr(rhs))
  final def <(rhs: Int): ExprBinary = this < mkSized(rhs)
  final def <=(rhs: Int): ExprBinary = this <= mkSized(rhs)
  final def >(rhs: Int): ExprBinary = this > mkSized(rhs)
  final def >=(rhs: Int): ExprBinary = this >= mkSized(rhs)

  final def index(idx: Expr): ExprIndex = fix(ExprIndex(this, idx))
  final def index[T: Numeric](idx: T): ExprIndex =
    this index mkIndex(implicitly[Numeric[T]].toInt(idx))

  final def slice(lIdx: Expr, op: String, rIdx: Expr): ExprSlice =
    fix(ExprSlice(this, lIdx, op, rIdx))
  final def slice(lIdx: Expr, op: String, rIdx: Int): ExprSlice =
    this.slice(lIdx, op, if (op == ":") mkIndex(rIdx) else mkSliceLength(rIdx))
  final def slice(lIdx: Int, op: String, rIdx: Expr): ExprSlice =
    this.slice(mkIndex(lIdx), op, rIdx)
  final def slice(lIdx: Int, op: String, rIdx: Int): ExprSlice =
    this.slice(mkIndex(lIdx), op, if (op == ":") mkIndex(rIdx) else mkSliceLength(rIdx))

  final def sel(name: String): ExprSel = fix(ExprSel(this, name))
  final def call(args: List[Arg]): ExprCall = fix(ExprCall(this, args))
  final def call(arg: Expr): ExprCall = this.call(fix(ArgP(arg)) :: Nil)

  final def cat(rhs: Expr): ExprCat = fix(ExprCat(List(this, rhs)))

  final def rep(cnt: Expr): ExprRep = fix(ExprRep(cnt, this))
  final def rep[T: Numeric](cnt: T): ExprRep =
    this rep fix(Expr(implicitly[Numeric[T]].toLong(cnt)))

  final def cast(kind: TypeFund): ExprCast = fix(ExprCast(kind, this))

  final def unary(op: String): ExprUnary = fix(ExprUnary(op, this))

  final def unary_+ : this.type = this
  final def unary_- : ExprUnary = unary("-")
  final def unary_~ : ExprUnary = unary("~")
  final def unary_! : ExprUnary = unary("!")

  final def zx(width: Expr): ExprBuiltin =
    fix(ExprBuiltin(AtZx, List(fix(ArgP(width)), fix(ArgP(this)))))
  final def zx(width: Int): ExprBuiltin = this zx fix(Expr(width))

  final def sx(width: Expr): ExprBuiltin =
    fix(ExprBuiltin(AtSx, List(fix(ArgP(width)), fix(ArgP(this)))))
  final def sx(width: Int): ExprBuiltin = this sx fix(Expr(width))

  final def castUnsigned: ExprBuiltin = fix(ExprBuiltin(DollarUnsigned, List(fix(ArgP(this)))))
  final def castSigned: ExprBuiltin = fix(ExprBuiltin(DollarSigned, List(fix(ArgP(this)))))

  final def asUnsigned: Expr = if (tpe.isSigned) castUnsigned else this
  final def asSigned: Expr = if (tpe.isSigned) this else castSigned
  final def withSignedness(signed: Boolean): Expr =
    if (tpe.isSigned && !signed) castUnsigned else if (!tpe.isSigned && signed) castSigned else this
  // format: on

  final def inc: Expr = if (tpe.isPacked && tpe.width == 1) ~this else (this + 1)
  final def dec: Expr = if (tpe.isPacked && tpe.width == 1) ~this else (this - 1)

  final def assign(expr: Expr): StmtAssign =
    fix(StmtAssign(this, expr))
  final def assign(value: Int): StmtAssign =
    this assign fix(ExprInt(tpe.isSigned, tpe.width.toInt, value))

  // Is this expression shaped as a valid lvalue expression
  lazy val isLValueExpr: Boolean = this forall {
    case _: ExprIdent             => true
    case _: ExprSym               => true
    case ExprIndex(expr, _)       => expr.isLValueExpr
    case ExprSlice(expr, _, _, _) => expr.isLValueExpr
    case ExprDot(expr, _, _)      => expr.isLValueExpr
    case ExprSel(expr, _)         => expr.isLValueExpr
    case ExprCat(parts)           => parts forall { _.isLValueExpr }
    case _                        => false
  }

  final def isPure: Boolean = {
    def p(expr: Expr): Boolean = expr match {
      // $COVERAGE-OFF$ Trivial to keep full
      case _: ExprCall             => false // Assume all calls are impure
      case ExprBuiltin(b, as)      => b.isPure && as.forall(_.expr.isPure)
      case ExprUnary(_, e)         => p(e)
      case ExprBinary(l, _, r)     => p(l) && p(r)
      case ExprCond(c, t, e)       => p(c) && p(t) && p(e)
      case ExprRep(_, e)           => p(e)
      case ExprCat(ps)             => ps forall p
      case ExprIndex(e, i)         => p(e) && p(i)
      case ExprSlice(e, _, ":", _) => p(e)
      case ExprSlice(e, l, _, _)   => p(e) && p(l)
      case ExprDot(e, _, _)        => p(e)
      case ExprSel(e, _)           => p(e)
      case ExprSymSel(e, _)        => p(e)
      case ExprCast(_, e)          => p(e)
      case _: ExprIdent | _: ExprSym | _: ExprOld | _: ExprThis | _: ExprType | _: ExprInt |
          _: ExprNum | _: ExprStr =>
        true
      // $COVERAGE-ON$
    }
    p(this)
  }

  final protected var _simplified: Expr = null

  // Simplify this expression
  final def simplify: Expr = {
    if (_simplified == null) {
      // Compute the simplified expression
      _simplified = this rewrite SimplifyExpr
      // The simplified expression cannot be simplified further
      _simplified._simplified = _simplified
    }
    _simplified
  }

  final protected var _simplifiedLValue: Expr = null

  final def simplifyLValue: Expr = {
    if (_simplifiedLValue == null) {
      // Compute the simplified expression
      _simplifiedLValue = this match {
        case _: ExprSym        => this
        case ExprCat(p :: Nil) => p
        case ExprCat(ps) =>
          TypeAssigner {
            ExprCat {
              ps flatMap {
                _.simplifyLValue match {
                  case ExprCat(es) => es
                  case e           => List(e)
                }
              }
            } withLoc loc
          }
        case _: ExprIndex => simplify
        case _: ExprSlice => simplify
        case _: ExprSel   => this
        case _            => unreachable
      }
      // The simplified expression cannot be simplified further
      _simplifiedLValue._simplifiedLValue = _simplifiedLValue
    }
    _simplifiedLValue
  }

  // Rewrite expression using bindings provided
  def substitute(bindings: Symbol => Option[Expr]): Expr =
    this rewrite new ReplaceTermRefs(bindings)

  def substitute(bindings: Bindings): Expr =
    this.substitute(bindings.get _)

  // Value of this expression if it can be determined right now, otherwise None
  def valueOption: Option[BigInt] = simplify match {
    case ExprNum(_, value)                 => Some(value)
    case ExprInt(_, _, value)              => Some(value)
    case ExprCast(_, ExprNum(_, value))    => Some(value)
    case ExprCast(_, ExprInt(_, _, value)) => Some(value)
    case _                                 => None
  }

  def value: BigInt = valueOption.get

  // True if this we know the value of this expression (same as value.isDefined)
  def isKnown: Boolean = valueOption.isDefined

  def cpy(): Expr = this match {
    // $COVERAGE-OFF$ Trivial to keep full, but not necessarily used
    case expr: ExprCall    => expr.copy()
    case expr: ExprBuiltin => expr.copy()
    case expr: ExprUnary   => expr.copy()
    case expr: ExprBinary  => expr.copy()
    case expr: ExprCond    => expr.copy()
    case expr: ExprRep     => expr.copy()
    case expr: ExprCat     => expr.copy()
    case expr: ExprIndex   => expr.copy()
    case expr: ExprSlice   => expr.copy()
    case expr: ExprDot     => expr.copy()
    case expr: ExprSel     => expr.copy()
    case expr: ExprSymSel  => expr.copy()
    case expr: ExprIdent   => expr.copy()
    case expr: ExprSym     => expr.copy()
    case expr: ExprOld     => expr.copy()
    case expr: ExprThis    => expr.copy()
    case expr: ExprType    => expr.copy()
    case expr: ExprCast    => expr.copy()
    case expr: ExprInt     => expr.copy()
    case expr: ExprNum     => expr.copy()
    case expr: ExprStr     => expr.copy()
    // $COVERAGE-ON$
  }

}

trait ExprObjOps { self: Expr.type =>
  // Helpers to easily create ExprNum from integers
  final def apply[T: Numeric](n: T): ExprNum = ExprNum(false, implicitly[Numeric[T]].toLong(n))

  object ImplicitConversions {
    // Implicit conversion for Int to ExprNum
    implicit final def int2ExprNum(n: Int): ExprNum = apply(n)
  }

  // And extractor so we can match against the the same as above
  final def unapply(num: ExprNum): Option[Int] = if (!num.signed) Some(num.value.toInt) else None

  // Extractor for instance port selects
  object InstancePortSel {

    def unapply(expr: ExprSel): Option[(Symbol, Symbol)] = expr.expr match {
      case ExprSym(symbol) =>
        symbol.kind match {
          case kind: TypeEntity => kind(expr.selector).map((symbol, _))
          case _                => None
        }
      case _ => None
    }

  }

  // Extractor for integral values (ExprInt or ExprNum)
  object Integral {

    def unapply(expr: Expr): Option[(Boolean, Option[Int], BigInt)] = expr partialMatch {
      case ExprNum(signed, value)                     => (signed, None, value)
      case ExprInt(signed, width, value)              => (signed, Some(width), value)
      case ExprCast(_, ExprNum(signed, value))        => (signed, None, value)
      case ExprCast(_, ExprInt(signed, width, value)) => (signed, Some(width), value)
    }

  }

}
