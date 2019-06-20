////////////////////////////////////////////////////////////////////////////////
// Argon Design Ltd. Project P8009 Alogic
// Copyright (c) 2018 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// Module: Alogic Compiler
// Author: Geza Lore
//
// DESCRIPTION:
//
// Namer tests
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.passes

import java.util.regex.Pattern

import com.argondesign.alogic.AlogicTest
import com.argondesign.alogic.SourceTextConverters._
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Error
import com.argondesign.alogic.core.Loc
import com.argondesign.alogic.core.Types.TypeNum
import com.argondesign.alogic.core.Types.TypeSInt
import com.argondesign.alogic.core.Types.TypeUInt
import com.argondesign.alogic.typer.AddImplicitCasts
import com.argondesign.alogic.typer.Typer
import org.scalatest.FreeSpec

final class FoldExprSpec extends FreeSpec with AlogicTest {

  implicit val cc = new CompilerContext
  val namer = new Namer
  val typer = new Typer
  val aics = new AddImplicitCasts
  val fold = new FoldExpr(foldRefs = false)

  def xform(tree: Tree): Tree = {
    tree match {
      case Root(_, entity: EntityIdent) => cc.addGlobalEntity(entity)
      case entity: EntityIdent          => cc.addGlobalEntity(entity)
      case _                            =>
    }
    val node = tree rewrite namer match {
      case Root(_, entity) => entity
      case other           => other
    }
    node rewrite typer rewrite aics rewrite fold
  }

  "FoldExpr should fold" - {
    "unary operators applied to unsized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed positive operand
          ("+(2s)", ExprNum(true, 2), ""),
          ("-(2s)", ExprNum(true, -2), ""),
          ("~(2s)", ExprNum(true, -3), ""),
          ("!(2s)", ExprInt(false, 1, 0), ""),
          ("&(2s)", ExprInt(false, 1, 0), ""),
          ("|(2s)", ExprInt(false, 1, 1), ""),
          ("^(2s)", ExprInt(false, 1, 1), ""),
          // signed negative operand
          ("+(-2s)", ExprNum(true, -2), ""),
          ("-(-2s)", ExprNum(true, 2), ""),
          ("~(-2s)", ExprNum(true, 1), ""),
          ("!(-2s)", ExprInt(false, 1, 0), ""),
          ("&(-2s)", ExprInt(false, 1, 0), ""),
          ("|(-2s)", ExprInt(false, 1, 1), ""),
          ("^(-2s)", ExprError(), "Unary '^' is not well defined for unsized negative values"),
          // signed 0 operand
          ("+(0s)", ExprNum(true, 0), ""),
          ("-(0s)", ExprNum(true, 0), ""),
          ("~(0s)", ExprNum(true, -1), ""),
          ("!(0s)", ExprInt(false, 1, 1), ""),
          ("&(0s)", ExprInt(false, 1, 0), ""),
          ("|(0s)", ExprInt(false, 1, 0), ""),
          ("^(0s)", ExprInt(false, 1, 0), ""),
          // signed -1 operand
          ("+(-1s)", ExprNum(true, -1), ""),
          ("-(-1s)", ExprNum(true, 1), ""),
          ("~(-1s)", ExprNum(true, 0), ""),
          ("!(-1s)", ExprInt(false, 1, 0), ""),
          ("&(-1s)", ExprInt(false, 1, 1), ""),
          ("|(-1s)", ExprInt(false, 1, 1), ""),
          ("^(-1s)", ExprError(), "Unary '^' is not well defined for unsized negative values"),
          // unsigned non-0 operand
          ("+(2)", ExprNum(false, 2), ""),
          ("-(2)", ExprError(), "Unary '-' is not well defined for unsigned values"),
          ("~(2)", ExprError(), "Unary '~' is not well defined for unsized unsigned values"),
          ("!(2)", ExprInt(false, 1, 0), ""),
          ("&(2)", ExprInt(false, 1, 0), ""),
          ("|(2)", ExprInt(false, 1, 1), ""),
          ("^(2)", ExprInt(false, 1, 1), ""),
          // unsigned 0 operand
          ("+(0)", ExprNum(false, 0), ""),
          ("-(0)", ExprNum(false, 0), ""),
          ("~(0)", ExprError(), "Unary '~' is not well defined for unsized unsigned values"),
          ("!(0)", ExprInt(false, 1, 1), ""),
          ("&(0)", ExprInt(false, 1, 0), ""),
          ("|(0)", ExprInt(false, 1, 0), ""),
          ("^(0)", ExprInt(false, 1, 0), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](Pattern.quote(msg))
          }
        }
      }
    }

    "binary operators applied to unsized integer literals" - {
      for {
        (expr, result, msg) <- List(
          // format: off
          //////////////////////////////////////////////
          // signed signed
          //////////////////////////////////////////////
          // Always valid
          ("3s >  2s", ExprNum(false, 1), ""),
          ("3s >= 2s", ExprNum(false, 1), ""),
          ("3s <  2s", ExprNum(false, 0), ""),
          ("3s <= 2s", ExprNum(false, 0), ""),
          ("3s == 2s", ExprNum(false, 0), ""),
          ("3s != 2s", ExprNum(false, 1), ""),
          ("3s && 2s", ExprNum(false, 1), ""),
          ("3s || 2s", ExprNum(false, 1), ""),
          // Arith
          ("3s * 2s", ExprNum(true, 6), ""),
          ("3s / 2s", ExprNum(true, 1), ""),
          ("3s % 2s", ExprNum(true, 1), ""),
          ("3s + 2s", ExprNum(true, 5), ""),
          ("3s - 2s", ExprNum(true, 1), ""),
          // Shifts
          (" 3s <<   2s", ExprNum(true, 12), ""),
          (" 3s >>   2s", ExprNum(true, 0), ""),
          (" 3s <<<  2s", ExprNum(true, 12), ""),
          (" 3s >>>  2s", ExprNum(true, 0), ""),
          (" 3s <<  -2s", ExprError(), "Negative shift amount"),
          (" 3s >>  -2s", ExprError(), "Negative shift amount"),
          (" 3s <<< -2s", ExprError(), "Negative shift amount"),
          (" 3s >>> -2s", ExprError(), "Negative shift amount"),
          ("-3s <<   2s", ExprNum(true, -12), ""),
          ("-3s >>   2s", ExprError(), "'>>' is not well defined for negative unsized values"),
          ("-3s <<<  2s", ExprNum(true, -12), ""),
          ("-3s >>>  2s", ExprNum(true, -1), ""),
          ("-3s <<  -2s", ExprError(), "Negative shift amount"),
          ("-3s >>  -2s", ExprError(), "Negative shift amount"), // ***
          ("-3s <<< -2s", ExprError(), "Negative shift amount"),
          ("-3s >>> -2s", ExprError(), "Negative shift amount"),
          // Bitwise
          (" 3s &  2s", ExprNum(true, 2), ""),
          (" 3s ^  2s", ExprNum(true, 1), ""),
          (" 3s |  2s", ExprNum(true, 3), ""),
          (" 3s & -2s", ExprError(), "Bitwise '&' operator is not well defined for negative unsized values"),
          (" 3s ^ -2s", ExprError(), "Bitwise '\\^' operator is not well defined for negative unsized values"),
          (" 3s | -2s", ExprError(), "Bitwise '\\|' operator is not well defined for negative unsized values"),
          ("-3s &  2s", ExprError(), "Bitwise '&' operator is not well defined for negative unsized values"),
          ("-3s ^  2s", ExprError(), "Bitwise '\\^' operator is not well defined for negative unsized values"),
          ("-3s |  2s", ExprError(), "Bitwise '\\|' operator is not well defined for negative unsized values"),
          ("-3s & -2s", ExprError(), "Bitwise '&' operator is not well defined for negative unsized values"),
          ("-3s ^ -2s", ExprError(), "Bitwise '\\^' operator is not well defined for negative unsized values"),
          ("-3s | -2s", ExprError(), "Bitwise '\\|' operator is not well defined for negative unsized values"),
          //////////////////////////////////////////////
          // signed unsigned
          //////////////////////////////////////////////
          // Always valid
          ("3s >  2", ExprNum(false, 1), ""),
          ("3s >= 2", ExprNum(false, 1), ""),
          ("3s <  2", ExprNum(false, 0), ""),
          ("3s <= 2", ExprNum(false, 0), ""),
          ("3s == 2", ExprNum(false, 0), ""),
          ("3s != 2", ExprNum(false, 1), ""),
          ("3s && 2", ExprNum(false, 1), ""),
          ("3s || 2", ExprNum(false, 1), ""),
          // Arith
          (" 3s * 2", ExprNum(false, 6), ""),
          (" 3s / 2", ExprNum(false, 1), ""),
          (" 3s % 2", ExprNum(false, 1), ""),
          (" 3s + 2", ExprNum(false, 5), ""),
          (" 3s - 2", ExprNum(false, 1), ""),
          (" 3s - 4", ExprError(), "Result of operator '-' is unsigned, but value is negative"),
          ("-3s * 2", ExprError(), "Result of operator '\\*' is unsigned, but value is negative"),
          ("-3s / 2", ExprError(), "Result of operator '/' is unsigned, but value is negative"),
          ("-3s % 2", ExprError(), "Result of operator '%' is unsigned, but value is negative"),
          ("-3s + 2", ExprError(), "Result of operator '\\+' is unsigned, but value is negative"),
          ("-3s - 2", ExprError(), "Result of operator '-' is unsigned, but value is negative"),
          ("-3s + 4", ExprNum(false, 1), ""),
          // Shifts
          (" 3s <<  2", ExprNum(true, 12), ""),
          (" 3s >>  2", ExprNum(true, 0), ""),
          (" 3s <<< 2", ExprNum(true, 12), ""),
          (" 3s >>> 2", ExprNum(true, 0), ""),
          ("-3s <<  2", ExprNum(true, -12), ""),
          ("-3s >>  2", ExprError(), "'>>' is not well defined for negative unsized values"),
          ("-3s <<< 2", ExprNum(true, -12), ""),
          ("-3s >>> 2", ExprNum(true, -1), ""),
          // Bitwise
          (" 3s & 2", ExprNum(false, 2), ""),
          (" 3s ^ 2", ExprNum(false, 1), ""),
          (" 3s | 2", ExprNum(false, 3), ""),
          ("-3s & 2", ExprError(), "Bitwise '&' operator is not well defined for negative unsized values"),
          ("-3s ^ 2", ExprError(), "Bitwise '\\^' operator is not well defined for negative unsized values"),
          ("-3s | 2", ExprError(), "Bitwise '\\|' operator is not well defined for negative unsized values"),
          //////////////////////////////////////////////
          // unsigned signed
          //////////////////////////////////////////////
          // Always valid
          ("3 >  2s", ExprNum(false, 1), ""),
          ("3 >= 2s", ExprNum(false, 1), ""),
          ("3 <  2s", ExprNum(false, 0), ""),
          ("3 <= 2s", ExprNum(false, 0), ""),
          ("3 == 2s", ExprNum(false, 0), ""),
          ("3 != 2s", ExprNum(false, 1), ""),
          ("3 && 2s", ExprNum(false, 1), ""),
          ("3 || 2s", ExprNum(false, 1), ""),
          // Arith
          ("3 *  2s", ExprNum(false, 6), ""),
          ("3 /  2s", ExprNum(false, 1), ""),
          ("3 %  2s", ExprNum(false, 1), ""),
          ("3 +  2s", ExprNum(false, 5), ""),
          ("3 -  2s", ExprNum(false, 1), ""),
          ("3 -  4s", ExprError(), "Result of operator '-' is unsigned, but value is negative"),
          ("3 * -2s", ExprError(), "Result of operator '\\*' is unsigned, but value is negative"),
          ("3 / -2s", ExprError(), "Result of operator '/' is unsigned, but value is negative"),
          ("3 % -2s", ExprNum(false, 1), ""),
          ("3 + -2s", ExprNum(false, 1), ""),
          ("3 - -2s", ExprNum(false, 5), ""),
          ("3 + -4s", ExprError(), "Result of operator '\\+' is unsigned, but value is negative"),
          // Shifts
          ("3 <<   2s", ExprNum(false, 12), ""),
          ("3 >>   2s", ExprNum(false, 0), ""),
          ("3 <<<  2s", ExprNum(false, 12), ""),
          ("3 >>>  2s", ExprNum(false, 0), ""),
          ("3 <<  -2s", ExprError(), "Negative shift amount"),
          ("3 >>  -2s", ExprError(), "Negative shift amount"),
          ("3 <<< -2s", ExprError(), "Negative shift amount"),
          ("3 >>> -2s", ExprError(), "Negative shift amount"),
          // Bitwise
          ("3 &  2s", ExprNum(false, 2), ""),
          ("3 ^  2s", ExprNum(false, 1), ""),
          ("3 |  2s", ExprNum(false, 3), ""),
          ("3 & -2s", ExprError(), "Bitwise '&' operator is not well defined for negative unsized values"),
          ("3 ^ -2s", ExprError(), "Bitwise '\\^' operator is not well defined for negative unsized values"),
          ("3 | -2s", ExprError(), "Bitwise '\\|' operator is not well defined for negative unsized values"),
          //////////////////////////////////////////////
          // unsigned unsigned
          //////////////////////////////////////////////
          // Always valid
          ("3 >  2", ExprNum(false, 1), ""),
          ("3 >= 2", ExprNum(false, 1), ""),
          ("3 <  2", ExprNum(false, 0), ""),
          ("3 <= 2", ExprNum(false, 0), ""),
          ("3 == 2", ExprNum(false, 0), ""),
          ("3 != 2", ExprNum(false, 1), ""),
          ("3 && 2", ExprNum(false, 1), ""),
          ("3 || 2", ExprNum(false, 1), ""),
          // Arith
          ("3 * 2", ExprNum(false, 6), ""),
          ("3 / 2", ExprNum(false, 1), ""),
          ("3 % 2", ExprNum(false, 1), ""),
          ("3 + 2", ExprNum(false, 5), ""),
          ("3 - 2", ExprNum(false, 1), ""),
          ("3 - 4", ExprError(), "Result of operator '-' is unsigned, but value is negative"),
          // Shifts
          ("3 <<  2", ExprNum(false, 12), ""),
          ("3 >>  2", ExprNum(false, 0), ""),
          ("3 <<< 2", ExprNum(false, 12), ""),
          ("3 >>> 2", ExprNum(false, 0), ""),
          // Bitwise
          ("3 &  2", ExprNum(false, 2), ""),
          ("3 ^  2", ExprNum(false, 1), ""),
          ("3 |  2", ExprNum(false, 3), "")
          // format: on
        )
      } {
        val e = expr.trim.replaceAll(" +", " ")
        e in {
          e.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "shifts with an unsized left hand side and a sized right hand side" - {
      for {
        (expr, result, msg) <- List(
          //////////////////////////////////////////////
          // signed signed
          //////////////////////////////////////////////
          (" 3s <<   8'sd2", ExprNum(true, 12), ""),
          (" 3s >>   8'sd2", ExprNum(true, 0), ""),
          (" 3s <<<  8'sd2", ExprNum(true, 12), ""),
          (" 3s >>>  8'sd2", ExprNum(true, 0), ""),
          (" 3s <<  -8'sd2", ExprError(), "Negative shift amount"),
          (" 3s >>  -8'sd2", ExprError(), "Negative shift amount"),
          (" 3s <<< -8'sd2", ExprError(), "Negative shift amount"),
          (" 3s >>> -8'sd2", ExprError(), "Negative shift amount"),
          ("-3s <<   8'sd2", ExprNum(true, -12), ""),
          ("-3s >>   8'sd2", ExprError(), "'>>' is not well defined for negative unsized values"),
          ("-3s <<<  8'sd2", ExprNum(true, -12), ""),
          ("-3s >>>  8'sd2", ExprNum(true, -1), ""),
          ("-3s <<  -8'sd2", ExprError(), "Negative shift amount"),
          ("-3s >>  -8'sd2", ExprError(), "Negative shift amount"), // ***
          ("-3s <<< -8'sd2", ExprError(), "Negative shift amount"),
          ("-3s >>> -8'sd2", ExprError(), "Negative shift amount"),
          //////////////////////////////////////////////
          // signed unsigned
          //////////////////////////////////////////////
          (" 3s <<  8'd2", ExprNum(true, 12), ""),
          (" 3s >>  8'd2", ExprNum(true, 0), ""),
          (" 3s <<< 8'd2", ExprNum(true, 12), ""),
          (" 3s >>> 8'd2", ExprNum(true, 0), ""),
          ("-3s <<  8'd2", ExprNum(true, -12), ""),
          ("-3s >>  8'd2", ExprError(), "'>>' is not well defined for negative unsized values"),
          ("-3s <<< 8'd2", ExprNum(true, -12), ""),
          ("-3s >>> 8'd2", ExprNum(true, -1), ""),
          //////////////////////////////////////////////
          // unsigned signed
          //////////////////////////////////////////////
          ("3 <<   8'sd2", ExprNum(false, 12), ""),
          ("3 >>   8'sd2", ExprNum(false, 0), ""),
          ("3 <<<  8'sd2", ExprNum(false, 12), ""),
          ("3 >>>  8'sd2", ExprNum(false, 0), ""),
          ("3 <<  -8'sd2", ExprError(), "Negative shift amount"),
          ("3 >>  -8'sd2", ExprError(), "Negative shift amount"),
          ("3 <<< -8'sd2", ExprError(), "Negative shift amount"),
          ("3 >>> -8'sd2", ExprError(), "Negative shift amount"),
          //////////////////////////////////////////////
          // unsigned unsigned
          //////////////////////////////////////////////
          ("3 <<  8'd2", ExprNum(false, 12), ""),
          ("3 >>  8'd2", ExprNum(false, 0), ""),
          ("3 <<< 8'd2", ExprNum(false, 12), ""),
          ("3 >>> 8'd2", ExprNum(false, 0), "")
        )
      } {
        val e = expr.trim.replaceAll(" +", " ")
        e in {
          e.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "unary operators applied to sized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed positive operand
          ("+(8'sd2)", ExprInt(true, 8, 2), ""),
          ("-(8'sd2)", ExprInt(true, 8, -2), ""),
          ("~(8'sd2)", ExprInt(true, 8, -3), ""),
          ("!(8'sd2)", ExprInt(false, 1, 0), ""),
          ("&(8'sd2)", ExprInt(false, 1, 0), ""),
          ("|(8'sd2)", ExprInt(false, 1, 1), ""),
          ("^(8'sd2)", ExprInt(false, 1, 1), ""),
          // signed negative operand
          ("+(-8'sd2)", ExprInt(true, 8, -2), ""),
          ("-(-8'sd2)", ExprInt(true, 8, 2), ""),
          ("~(-8'sd2)", ExprInt(true, 8, 1), ""),
          ("!(-8'sd2)", ExprInt(false, 1, 0), ""),
          ("&(-8'sd2)", ExprInt(false, 1, 0), ""),
          ("|(-8'sd2)", ExprInt(false, 1, 1), ""),
          ("^(-8'sd2)", ExprInt(false, 1, 1), ""),
          // signed 0 operand
          ("+(8'sd0)", ExprInt(true, 8, 0), ""),
          ("-(8'sd0)", ExprInt(true, 8, 0), ""),
          ("~(8'sd0)", ExprInt(true, 8, -1), ""),
          ("!(8'sd0)", ExprInt(false, 1, 1), ""),
          ("&(8'sd0)", ExprInt(false, 1, 0), ""),
          ("|(8'sd0)", ExprInt(false, 1, 0), ""),
          ("^(8'sd0)", ExprInt(false, 1, 0), ""),
          // signed -1 operand
          ("+(-8'sd1)", ExprInt(true, 8, -1), ""),
          ("-(-8'sd1)", ExprInt(true, 8, 1), ""),
          ("~(-8'sd1)", ExprInt(true, 8, 0), ""),
          ("!(-8'sd1)", ExprInt(false, 1, 0), ""),
          ("&(-8'sd1)", ExprInt(false, 1, 1), ""),
          ("|(-8'sd1)", ExprInt(false, 1, 1), ""),
          ("^(-8'sd1)", ExprInt(false, 1, 0), ""),
          // unsigned non-0 operand
          ("+(8'd2)", ExprInt(false, 8, 2), ""),
          ("-(8'd2)", ExprError(), "Unary '-' is not well defined for unsigned values"),
          ("~(8'd2)", ExprInt(false, 8, 253), ""),
          ("!(8'd2)", ExprInt(false, 1, 0), ""),
          ("&(8'd2)", ExprInt(false, 1, 0), ""),
          ("|(8'd2)", ExprInt(false, 1, 1), ""),
          ("^(8'd2)", ExprInt(false, 1, 1), ""),
          // unsigned 0 operand
          ("+(8'd0)", ExprInt(false, 8, 0), ""),
          ("-(8'd0)", ExprInt(false, 8, 0), ""),
          ("~(8'd0)", ExprInt(false, 8, 255), ""),
          ("!(8'd0)", ExprInt(false, 1, 1), ""),
          ("&(8'd0)", ExprInt(false, 1, 0), ""),
          ("|(8'd0)", ExprInt(false, 1, 0), ""),
          ("^(8'd0)", ExprInt(false, 1, 0), ""),
          // reductions ff
          ("&(8'hff)", ExprInt(false, 1, 1), ""),
          ("|(8'hff)", ExprInt(false, 1, 1), ""),
          ("^(8'hff)", ExprInt(false, 1, 0), ""),
          // reductions 0
          ("&(8'h0)", ExprInt(false, 1, 0), ""),
          ("|(8'h0)", ExprInt(false, 1, 0), ""),
          ("^(8'h0)", ExprInt(false, 1, 0), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "binary operators applied to equally sized integer literals" - {
      for {
        (expr, result, msg) <- List(
          //////////////////////////////////////////////
          // signed signed
          //////////////////////////////////////////////
          // Always valid
          ("4'sd3 >  4'sd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 >= 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 <  4'sd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 <= 4'sd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 == 4'sd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 != 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 && 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 || 4'sd2", ExprInt(false, 1, 1), ""),
          // Arith
          ("4'sd3 * 4'sd2", ExprInt(true, 4, 6), ""),
          ("4'sd3 / 4'sd2", ExprInt(true, 4, 1), ""),
          ("4'sd3 % 4'sd2", ExprInt(true, 4, 1), ""),
          ("4'sd3 + 4'sd2", ExprInt(true, 4, 5), ""),
          ("4'sd3 - 4'sd2", ExprInt(true, 4, 1), ""),
          ("4'sd3 - 4'sd4", ExprInt(true, 4, -1), ""),
          // Bitwise
          (" 4'sd3 &   4'sd2", ExprInt(true, 4, 2), ""),
          (" 4'sd3 ^   4'sd2", ExprInt(true, 4, 1), ""),
          (" 4'sd3 |   4'sd2", ExprInt(true, 4, 3), ""),
          (" 4'sd3 &  -4'sd2", ExprInt(true, 4, 2), ""),
          (" 4'sd3 ^  -4'sd2", ExprInt(true, 4, -3), ""),
          (" 4'sd3 |  -4'sd2", ExprInt(true, 4, -1), ""),
          ("-4'sd3 &   4'sd2", ExprInt(true, 4, 0), ""),
          ("-4'sd3 ^   4'sd2", ExprInt(true, 4, -1), ""),
          ("-4'sd3 |   4'sd2", ExprInt(true, 4, -1), ""),
          ("-4'sd3 &  -4'sd2", ExprInt(true, 4, -4), ""),
          ("-4'sd3 ^  -4'sd2", ExprInt(true, 4, 3), ""),
          ("-4'sd3 |  -4'sd2", ExprInt(true, 4, -1), ""),
          //////////////////////////////////////////////
          // signed unsigned
          //////////////////////////////////////////////
          // Always valid
          ("4'sd3 >  4'd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 >= 4'd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 <  4'd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 <= 4'd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 == 4'd2", ExprInt(false, 1, 0), ""),
          ("4'sd3 != 4'd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 && 4'd2", ExprInt(false, 1, 1), ""),
          ("4'sd3 || 4'd2", ExprInt(false, 1, 1), ""),
          // Bitwise
          (" 4'sd3 &  4'd2", ExprInt(false, 4, 2), ""),
          (" 4'sd3 ^  4'd2", ExprInt(false, 4, 1), ""),
          (" 4'sd3 |  4'd2", ExprInt(false, 4, 3), ""),
          ("-4'sd3 &  4'd2", ExprInt(false, 4, 0), ""),
          ("-4'sd3 ^  4'd2", ExprInt(false, 4, 15), ""),
          ("-4'sd3 |  4'd2", ExprInt(false, 4, 15), ""),
          //////////////////////////////////////////////
          // unsigned signed
          //////////////////////////////////////////////
          // Always valid
          ("4'd3 >  4'sd2", ExprInt(false, 1, 1), ""),
          ("4'd3 >= 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'd3 <  4'sd2", ExprInt(false, 1, 0), ""),
          ("4'd3 <= 4'sd2", ExprInt(false, 1, 0), ""),
          ("4'd3 == 4'sd2", ExprInt(false, 1, 0), ""),
          ("4'd3 != 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'd3 && 4'sd2", ExprInt(false, 1, 1), ""),
          ("4'd3 || 4'sd2", ExprInt(false, 1, 1), ""),
          // Bitwise
          ("4'd3 &  4'sd2", ExprInt(false, 4, 2), ""),
          ("4'd3 ^  4'sd2", ExprInt(false, 4, 1), ""),
          ("4'd3 |  4'sd2", ExprInt(false, 4, 3), ""),
          ("4'd3 & -4'sd2", ExprInt(false, 4, 2), ""),
          ("4'd3 ^ -4'sd2", ExprInt(false, 4, 13), ""),
          ("4'd3 | -4'sd2", ExprInt(false, 4, 15), ""),
          //////////////////////////////////////////////
          // unsigned unsigned
          //////////////////////////////////////////////
          // Always valid
          ("4'd3 >  4'd2", ExprInt(false, 1, 1), ""),
          ("4'd3 >= 4'd2", ExprInt(false, 1, 1), ""),
          ("4'd3 <  4'd2", ExprInt(false, 1, 0), ""),
          ("4'd3 <= 4'd2", ExprInt(false, 1, 0), ""),
          ("4'd3 == 4'd2", ExprInt(false, 1, 0), ""),
          ("4'd3 != 4'd2", ExprInt(false, 1, 1), ""),
          ("4'd3 && 4'd2", ExprInt(false, 1, 1), ""),
          ("4'd3 || 4'd2", ExprInt(false, 1, 1), ""),
          // Arith
          ("4'd3 * 4'd2", ExprInt(false, 4, 6), ""),
          ("4'd3 / 4'd2", ExprInt(false, 4, 1), ""),
          ("4'd3 % 4'd2", ExprInt(false, 4, 1), ""),
          ("4'd3 + 4'd2", ExprInt(false, 4, 5), ""),
          ("4'd3 - 4'd2", ExprInt(false, 4, 1), ""),
          ("4'd3 - 4'd4", ExprInt(false, 4, 15), ""),
          // Bitwise
          ("4'd3 &  4'd2", ExprInt(false, 4, 2), ""),
          ("4'd3 ^  4'd2", ExprInt(false, 4, 1), ""),
          ("4'd3 |  4'd2", ExprInt(false, 4, 3), "")
        )
      } {
        val e = expr.trim.replaceAll(" +", " ")
        e in {
          e.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "ternary operator" - {
      for {
        (text, pattern, msg) <- List[(String, PartialFunction[Any, Unit], String)](
          ("0 ? 1 : 2", { case ExprNum(false, v) if v == 2                                     => }, ""),
          ("1 ? 1 : 2", { case ExprNum(false, v) if v == 1                                     => }, ""),
          ("@randbit() ? 1 : 1", { case ExprNum(false, v) if v == 1                            => }, ""),
          ("@randbit() ? 2 : 2", { case ExprNum(false, v) if v == 2                            => }, ""),
          ("@randbit() ? 8'd0 : 8'd0", { case ExprInt(false, 8, v) if v == 0                   => }, ""),
          ("@randbit() ? 8'd0 : 8'd1", { case ExprTernary(_: ExprCall, _: ExprInt, _: ExprInt) => },
           ""),
          ("@randbit() ? 8'd0 : 8'sd0", { case ExprTernary(_: ExprCall, _: ExprInt, _: ExprInt) => },
           "")
        )
      } {
        val expr = text.trim
        expr in {
          xform(expr.asTree[Expr]) should matchPattern(pattern)
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "index into sized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed operand
          ("4'sd2[0]", ExprInt(false, 1, 0), ""),
          ("4'sd2[1]", ExprInt(false, 1, 1), ""),
          ("4'sd2[2]", ExprInt(false, 1, 0), ""),
          ("4'sd2[3]", ExprInt(false, 1, 0), ""),
          // unsigned operand
          ("4'd2[0]", ExprInt(false, 1, 0), ""),
          ("4'd2[1]", ExprInt(false, 1, 1), ""),
          ("4'd2[2]", ExprInt(false, 1, 0), ""),
          ("4'd2[3]", ExprInt(false, 1, 0), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "slice into sized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed operand
          ("4'sb0101[1 :0]", ExprInt(false, 2, 1), ""),
          ("4'sb0101[2 :0]", ExprInt(false, 3, 5), ""),
          ("4'sb0101[3 :0]", ExprInt(false, 4, 5), ""),
          ("4'sb0101[1+:1]", ExprInt(false, 1, 0), ""),
          ("4'sb0101[1+:2]", ExprInt(false, 2, 2), ""),
          ("4'sb0101[1+:3]", ExprInt(false, 3, 2), ""),
          ("4'sb0101[3-:1]", ExprInt(false, 1, 0), ""),
          ("4'sb0101[3-:2]", ExprInt(false, 2, 1), ""),
          ("4'sb0101[3-:3]", ExprInt(false, 3, 2), ""),
          // unsigned operand
          ("4'b0101[1 :0]", ExprInt(false, 2, 1), ""),
          ("4'b0101[2 :0]", ExprInt(false, 3, 5), ""),
          ("4'b0101[3 :0]", ExprInt(false, 4, 5), ""),
          ("4'b0101[1+:1]", ExprInt(false, 1, 0), ""),
          ("4'b0101[1+:2]", ExprInt(false, 2, 2), ""),
          ("4'b0101[1+:3]", ExprInt(false, 3, 2), ""),
          ("4'b0101[3-:1]", ExprInt(false, 1, 0), ""),
          ("4'b0101[3-:2]", ExprInt(false, 2, 1), ""),
          ("4'b0101[3-:3]", ExprInt(false, 3, 2), ""),
          // long range
          ("36'hf0f0f0f0f[35:0]", ExprInt(false, 36, BigInt("f0f0f0f0f", 16)), ""),
          ("68'hf0f0f0f0f0f0f0f0f[67:0]", ExprInt(false, 68, BigInt("f0f0f0f0f0f0f0f0f", 16)), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "index into unsized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed operand
          (" 2s[0]", ExprInt(false, 1, 0), ""),
          (" 2s[1]", ExprInt(false, 1, 1), ""),
          (" 2s[2]", ExprInt(false, 1, 0), ""),
          (" 2s[3]", ExprInt(false, 1, 0), ""),
          ("-2s[0]", ExprInt(false, 1, 0), ""),
          ("-2s[1]", ExprInt(false, 1, 1), ""),
          ("-2s[2]", ExprInt(false, 1, 1), ""),
          ("-2s[3]", ExprInt(false, 1, 1), ""),
          // unsigned operand
          (" 2[0]", ExprInt(false, 1, 0), ""),
          (" 2[1]", ExprInt(false, 1, 1), ""),
          (" 2[2]", ExprInt(false, 1, 0), ""),
          (" 2[3]", ExprInt(false, 1, 0), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "slice into unsized integer literals" - {
      for {
        (text, result, msg) <- List(
          // signed operand
          (" 5s[1 :0]", ExprInt(false, 2, 1), ""),
          (" 5s[2 :0]", ExprInt(false, 3, 5), ""),
          (" 5s[3 :0]", ExprInt(false, 4, 5), ""),
          (" 5s[1+:1]", ExprInt(false, 1, 0), ""),
          (" 5s[1+:2]", ExprInt(false, 2, 2), ""),
          (" 5s[1+:3]", ExprInt(false, 3, 2), ""),
          (" 5s[3-:1]", ExprInt(false, 1, 0), ""),
          (" 5s[3-:2]", ExprInt(false, 2, 1), ""),
          (" 5s[3-:3]", ExprInt(false, 3, 2), ""),
          ("-5s[1 :0]", ExprInt(false, 2, 3), ""),
          ("-5s[2 :0]", ExprInt(false, 3, 3), ""),
          ("-5s[3 :0]", ExprInt(false, 4, 11), ""),
          ("-5s[1+:1]", ExprInt(false, 1, 1), ""),
          ("-5s[1+:2]", ExprInt(false, 2, 1), ""),
          ("-5s[1+:3]", ExprInt(false, 3, 5), ""),
          ("-5s[3-:1]", ExprInt(false, 1, 1), ""),
          ("-5s[3-:2]", ExprInt(false, 2, 2), ""),
          ("-5s[3-:3]", ExprInt(false, 3, 5), ""),
          // unsigned operand
          ("5[1 :0]", ExprInt(false, 2, 1), ""),
          ("5[2 :0]", ExprInt(false, 3, 5), ""),
          ("5[3 :0]", ExprInt(false, 4, 5), ""),
          ("5[1+:1]", ExprInt(false, 1, 0), ""),
          ("5[1+:2]", ExprInt(false, 2, 2), ""),
          ("5[1+:3]", ExprInt(false, 3, 2), ""),
          ("5[3-:1]", ExprInt(false, 1, 0), ""),
          ("5[3-:2]", ExprInt(false, 2, 1), ""),
          ("5[3-:3]", ExprInt(false, 3, 2), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "index over a slice" - {
      for {
        (text, pattern) <- List[(String, PartialFunction[Any, Unit])](
          // format: off
          ("a[8  : 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 3 => }),
          ("a[9  : 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 3 => }),
          ("a[8  : 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 5 => }),
          ("a[9  : 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 5 => }),
          ("a[8 +: 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 8 => }),
          ("a[9 +: 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 9 => }),
          ("a[8 +: 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 10 => }),
          ("a[9 +: 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 11 => }),
          ("a[8 -: 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 6 => }),
          ("a[9 -: 3][0]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 7 => }),
          ("a[8 -: 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 8 => }),
          ("a[9 -: 3][2]", { case ExprIndex(ExprRef(s), ExprInt(false, 4, v)) if s.name == "a" && v == 9 => })
          // format: on
        )
      } {
        text in {
          val src = s"""|fsm f {
                        |  in u10 a;
                        |  out u1 b;
                        |  fence { b = ${text}; }
                        |}""".stripMargin
          val tree = xform(src.asTree[Root])
          val expr = tree getFirst { case StmtAssign(_, rhs) => rhs }
          expr should matchPattern(pattern)
          cc.messages shouldBe empty
        }
      }
    }

    "slice over a slice" - {
      for {
        (text, pattern) <- List[(String, PartialFunction[Any, Unit])](
          // format: off
          ("a[8  : 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), ":", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 4 => }),
          ("a[9  : 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), ":", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 4 => }),
          ("a[8  : 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), ":", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 5 => }),
          ("a[9  : 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), ":", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 5 => }),
          ("a[8  : 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 2 => }),
          ("a[9  : 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 2 => }),
          ("a[8  : 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[9  : 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[8  : 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 2 => }),
          ("a[9  : 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 5 && r == 2 => }),
          ("a[8  : 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[9  : 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[8 +: 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 8 && r == 2 => }),
          ("a[9 +: 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 9 && r == 2 => }),
          ("a[8 +: 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 9 && r == 2 => }),
          ("a[9 +: 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 10 && r == 2 => }),
          ("a[8 +: 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 9 && r == 2 => }),
          ("a[9 +: 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 10 && r == 2 => }),
          ("a[8 +: 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 10 && r == 2 => }),
          ("a[9 +: 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 11 && r == 2 => }),
          ("a[8 +: 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 9 && r == 2 => }),
          ("a[9 +: 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 10 && r == 2 => }),
          ("a[8 +: 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 10 && r == 2 => }),
          ("a[9 +: 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 11 && r == 2 => }),
          ("a[8 -: 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[9 -: 4][1  : 0]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[8 -: 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[9 -: 4][2  : 1]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 8 && r == 2 => }),
          ("a[8 -: 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[9 -: 4][1 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[8 -: 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[9 -: 4][2 +: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "+:", ExprInt(false, 4, r)) if s.name == "a" && l == 8 && r == 2 => }),
          ("a[8 -: 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 6 && r == 2 => }),
          ("a[9 -: 4][1 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[8 -: 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 7 && r == 2 => }),
          ("a[9 -: 4][2 -: 2]", { case ExprSlice(ExprRef(s), ExprInt(false, 4, l), "-:", ExprInt(false, 4, r)) if s.name == "a" && l == 8 && r == 2 => })
          // format: on
        )
      } {
        text in {
          val src = s"""|fsm f {
                        |  in u10 a;
                        |  out u2 b;
                        |  fence { b = ${text}; }
                        |}""".stripMargin
          val tree = xform(src.asTree[Root])
          val expr = tree getFirst { case StmtAssign(_, rhs) => rhs }
          expr should matchPattern(pattern)
          cc.messages shouldBe empty
        }
      }
    }

    "index over $signed/$unsigned" - {
      for {
        (text, pattern) <- List[(String, PartialFunction[Any, Unit])](
          ("$signed(a)[3'd0]", {
            case ExprIndex(ExprRef(term), ExprInt(false, 3, v)) if term.name == "a" && v == 0 =>
          }),
          ("$signed(a)[3'd1]", {
            case ExprIndex(ExprRef(term), ExprInt(false, 3, v)) if term.name == "a" && v == 1 =>
          }),
          ("$unsigned(a)[3'd0]", {
            case ExprIndex(ExprRef(term), ExprInt(false, 3, v)) if term.name == "a" && v == 0 =>
          }),
          ("$unsigned(a)[3'd1]", {
            case ExprIndex(ExprRef(term), ExprInt(false, 3, v)) if term.name == "a" && v == 1 =>
          }),
        )
      } {
        val expr = text.trim
        expr in {
          val src = s"""|fsm f {
                        |  in u8 a;
                        |  out u1 b;
                        |  fence { b = ${expr}; }
                        |}""".stripMargin
          val res = (xform(src.asTree[Root]) collectFirst { case StmtAssign(_, rhs) => rhs }).value
          res should matchPattern(pattern)
          cc.messages shouldBe empty
        }
      }
    }

    "slice over $signed/$unsigned" - {
      for {
        (text, pattern) <- List[(String, PartialFunction[Any, Unit])](
          ("$signed(a)[3'd1  : 3'd0]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), ":", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 0 =>
          }),
          ("$signed(a)[3'd2  : 3'd1]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), ":", ExprInt(false, 3, r))
                if term.name == "a" && l == 2 && r == 1 =>
          }),
          ("$signed(a)[3'd0 +: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "+:", ExprInt(false, 3, r))
                if term.name == "a" && l == 0 && r == 2 =>
          }),
          ("$signed(a)[3'd1 +: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "+:", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 2 =>
          }),
          ("$signed(a)[3'd2 -: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "-:", ExprInt(false, 3, r))
                if term.name == "a" && l == 2 && r == 2 =>
          }),
          ("$signed(a)[3'd1 -: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "-:", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 2 =>
          }),
          ("$unsigned(a)[3'd1  : 3'd0]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), ":", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 0 =>
          }),
          ("$unsigned(a)[3'd2  : 3'd1]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), ":", ExprInt(false, 3, r))
                if term.name == "a" && l == 2 && r == 1 =>
          }),
          ("$unsigned(a)[3'd0 +: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "+:", ExprInt(false, 3, r))
                if term.name == "a" && l == 0 && r == 2 =>
          }),
          ("$unsigned(a)[3'd1 +: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "+:", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 2 =>
          }),
          ("$unsigned(a)[3'd2 -: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "-:", ExprInt(false, 3, r))
                if term.name == "a" && l == 2 && r == 2 =>
          }),
          ("$unsigned(a)[3'd1 -: 3'd2]", {
            case ExprSlice(ExprRef(term), ExprInt(false, 3, l), "-:", ExprInt(false, 3, r))
                if term.name == "a" && l == 1 && r == 2 =>
          })
        )
      } {
        val expr = text.trim
        expr in {
          val src = s"""|fsm f {
                        |  in u8 a;
                        |  out u2 b;
                        |  fence { b = ${expr}; }
                        |}""".stripMargin
          val res = (xform(src.asTree[Root]) collectFirst { case StmtAssign(_, rhs) => rhs }).value
          res should matchPattern(pattern)
          cc.messages shouldBe empty
        }
      }
    }

    "width 1 slices" - {
      for {
        (text, pattern) <- List[(String, PartialFunction[Any, Unit])](
          // format: off
          ("c = a[7:7]", { case ExprIndex(ExprRef(a), ExprInt(false, 3, i)) if a.name == "a" && i == 7 => }),
          ("c = a[6 +: 1]", { case ExprIndex(ExprRef(a), ExprInt(false, 3, i)) if a.name == "a" && i == 6 => }),
          ("c = a[5 +: 1]", { case ExprIndex(ExprRef(a), ExprInt(false, 3, i)) if a.name == "a" && i == 5 => }),
          ("c = a[4 -: 1]", { case ExprIndex(ExprRef(a), ExprInt(false, 3, i)) if a.name == "a" && i == 4 => }),
          ("c = a[3 -: 1]", { case ExprIndex(ExprRef(a), ExprInt(false, 3, i)) if a.name == "a" && i == 3 => }),
          ("d = a[2  : 1]", { case ExprSlice(ExprRef(a), ExprInt(false, 3, l), ":", ExprInt(false, 3, r)) if a.name == "a" && l == 2 && r == 1 => }),
          ("c = a[b +: 1]", { case ExprIndex(ExprRef(a), ExprRef(b)) if a.name == "a" && b.name == "b" => }),
          ("c = a[b -: 1]", { case ExprIndex(ExprRef(a), ExprRef(b)) if a.name == "a" && b.name == "b" => })
          // format: on
        )
      } {
        text in {
          val src = s"""|fsm f {
                        |  in u8 a;
                        |  (* unused *) in u3 b;
                        |  (* unused *) out u1 c;
                        |  (* unused *) out u2 d;
                        |  fence { ${text}; }
                        |}""".stripMargin
          val tree = xform(src.asTree[Root])
          val expr = tree getFirst { case StmtAssign(_, rhs) => rhs }
          expr should matchPattern(pattern)
          cc.messages shouldBe empty
        }
      }
    }

    "concatenation of sized integer literals" - {
      for {
        (text, result, msg) <- List(
          ("{4'd2, 4'd2}", ExprInt(false, 8, 34), ""),
          ("{4'd2, 4'sd2}", ExprInt(false, 8, 34), ""),
          ("{4'sd2, 4'd2}", ExprInt(false, 8, 34), ""),
          ("{4'sd2, 4'sd2}", ExprInt(false, 8, 34), ""),
          ("{1'b1, 2'b11, 3'b111, 4'b1111}", ExprInt(false, 10, 1023), ""),
          ("{-1'sd1, -2'sd1, -3'sd1, -4'sd1}", ExprInt(false, 10, 1023), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "repetition of sized integer literals" - {
      for {
        (text, result, msg) <- List(
          ("{4{1'b1}}", ExprInt(false, 4, 15), ""),
          ("{4{1'b0}}", ExprInt(false, 4, 0), ""),
          ("{4{-1'sb1}}", ExprInt(false, 4, 15), ""),
          ("{2{2'b1}}", ExprInt(false, 4, 5), ""),
          ("{2{2'b0}}", ExprInt(false, 4, 0), ""),
          ("{2{-4'sb1}}", ExprInt(false, 8, 255), ""),
          ("{4{2'b10}}", ExprInt(false, 8, 170), "")
        )
      } {
        val expr = text.trim
        expr in {
          expr.asTree[Expr] rewrite fold shouldBe result
          if (msg.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](msg)
          }
        }
      }
    }

    "builtin functions" - {
      "@max" - {
        for {
          (expr, result, msg) <- List(
            ("@max(1s)", ExprNum(true, 1), ""),
            ("@max(1)", ExprNum(false, 1), ""),
            ("@max(1s, 2s)", ExprNum(true, 2), ""),
            ("@max(1s, 2)", ExprNum(false, 2), ""),
            ("@max(1, 2s)", ExprNum(false, 2), ""),
            ("@max(1, 2)", ExprNum(false, 2), ""),
            ("@max(0s, 1s)", ExprNum(true, 1), ""),
            ("@max(-2s, -1s)", ExprNum(true, -1), ""),
            ("@max(-2s, 1)", ExprNum(false, 1), ""),
            ("@max(0, 1, 2, 3, 4, 5)", ExprNum(false, 5), "")
          )
        } {
          val e = expr.trim.replaceAll(" +", " ")
          e in {
            xform(e.asTree[Expr]) shouldBe result
            if (msg.isEmpty) {
              cc.messages shouldBe empty
            } else {
              cc.messages.loneElement should beThe[Error](msg)
            }
          }
        }
      }

      // TODO: @ex

      // TODO: @msb

      "@zx" - {
        for {
          (expr, result, msg) <- List(
            // format: off
            ("@zx(3, 2'b00)", ExprInt(false, 3, 0), ""),
            ("@zx(3, 2'b01)", ExprInt(false, 3, 1), ""),
            ("@zx(3, 2'b10)", ExprInt(false, 3, 2), ""),
            ("@zx(3, 2'b11)", ExprInt(false, 3, 3), ""),
            ("@zx(3, 2'sb00)", ExprInt(true, 3, 0), ""),
            ("@zx(3, 2'sb01)", ExprInt(true, 3, 1), ""),
            ("@zx(3, 2'sb10)", ExprInt(true, 3, 2), ""),
            ("@zx(3, 2'sb11)", ExprInt(true, 3, 3), ""),
            ("@zx(2, 2'b00)", ExprInt(false, 2, 0), ""),
            ("@zx(2, 2'b01)", ExprInt(false, 2, 1), ""),
            ("@zx(2, 2'b10)", ExprInt(false, 2, 2), ""),
            ("@zx(2, 2'b11)", ExprInt(false, 2, 3), ""),
            ("@zx(2, 2'sb00)", ExprInt(true, 2, 0), ""),
            ("@zx(2, 2'sb01)", ExprInt(true, 2, 1), ""),
            ("@zx(2, 2'sb10)", ExprInt(true, 2, -2), ""),
            ("@zx(2, 2'sb11)", ExprInt(true, 2, -1), ""),
            ("@zx(1, 2'b00)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'b01)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'b10)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'b11)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'sb00)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'sb01)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'sb10)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@zx(1, 2'sb11)", ExprError(), "Result width 1 of extension is less than argument width 2")
            // format: on
          )
        } {
          val e = expr.trim.replaceAll(" +", " ")
          e in {
            xform(e.asTree[Expr]) shouldBe result
            val errors = cc.messages filter { _.isInstanceOf[Error] }
            if (msg.isEmpty) {
              errors shouldBe empty
            } else {
              errors.loneElement should beThe[Error](Pattern.quote(msg))
            }
          }
        }
      }

      "@sx" - {
        for {
          (expr, result, msg) <- List(
            // format: off
            ("@sx(3, 2'b00)", ExprInt(false, 3, 0), ""),
            ("@sx(3, 2'b01)", ExprInt(false, 3, 1), ""),
            ("@sx(3, 2'b10)", ExprInt(false, 3, 6), ""),
            ("@sx(3, 2'b11)", ExprInt(false, 3, 7), ""),
            ("@sx(3, 2'sb00)", ExprInt(true, 3, 0), ""),
            ("@sx(3, 2'sb01)", ExprInt(true, 3, 1), ""),
            ("@sx(3, 2'sb10)", ExprInt(true, 3, -2), ""),
            ("@sx(3, 2'sb11)", ExprInt(true, 3, -1), ""),
            ("@sx(2, 2'b00)", ExprInt(false, 2, 0), ""),
            ("@sx(2, 2'b01)", ExprInt(false, 2, 1), ""),
            ("@sx(2, 2'b10)", ExprInt(false, 2, 2), ""),
            ("@sx(2, 2'b11)", ExprInt(false, 2, 3), ""),
            ("@sx(2, 2'sb00)", ExprInt(true, 2, 0), ""),
            ("@sx(2, 2'sb01)", ExprInt(true, 2, 1), ""),
            ("@sx(2, 2'sb10)", ExprInt(true, 2, -2), ""),
            ("@sx(2, 2'sb11)", ExprInt(true, 2, -1), ""),
            ("@sx(1, 2'b00)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'b01)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'b10)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'b11)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'sb00)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'sb01)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'sb10)", ExprError(), "Result width 1 of extension is less than argument width 2"),
            ("@sx(1, 2'sb11)", ExprError(), "Result width 1 of extension is less than argument width 2")
            // format: on
          )
        } {
          val e = expr.trim.replaceAll(" +", " ")
          e in {
            xform(e.asTree[Expr]) shouldBe result
            val errors = cc.messages filter { _.isInstanceOf[Error] }
            if (msg.isEmpty) {
              errors shouldBe empty
            } else {
              errors.loneElement should beThe[Error](Pattern.quote(msg))
            }
          }
        }
      }

      "@bits" - {
        for {
          (expr, result, msg) <- List(
            ("@bits(1'b0)", ExprNum(false, 1), ""),
            ("@bits(2'b0)", ExprNum(false, 2), ""),
            ("@bits(2'sb0)", ExprNum(false, 2), ""),
            ("@bits(bool)", ExprNum(false, 1), ""),
            ("@bits(u3)", ExprNum(false, 3), ""),
            ("@bits(i3)", ExprNum(false, 3), ""),
            ("@bits(a)", ExprNum(false, 10), ""),
            ("@bits(a.f0)", ExprNum(false, 1), ""),
            ("@bits(a.f1)", ExprNum(false, 7), ""),
            ("@bits(a.f2)", ExprNum(false, 2), ""),
            ("@bits(a.f2.f0)", ExprNum(false, 2), ""),
            ("@bits(a_t)", ExprNum(false, 10), ""),
            ("@bits(a_t.f0)", ExprNum(false, 1), ""),
            ("@bits(a_t.f1)", ExprNum(false, 7), ""),
            ("@bits(a_t.f2)", ExprNum(false, 2), ""),
            ("@bits(a_t.f2.f0)", ExprNum(false, 2), "")
          )
        } {
          val e = expr.trim.replaceAll(" +", " ")
          e in {
            val tree = s"""|struct b_t {
                           |  u2 f0;
                           |};
                           |
                           |struct a_t {
                           |  bool f0;
                           |  i7   f1;
                           |  b_t  f2;
                           |};
                           |
                           |fsm x {
                           |  a_t a;
                           |  fence {
                           |    ${expr};
                           |  }
                           |}""".stripMargin.asTree[Root]
            val exprOpt = xform(tree) collectFirst { case StmtExpr(e) => e }
            val errors = cc.messages filter { _.isInstanceOf[Error] }
            if (msg.isEmpty) {
              exprOpt.value shouldBe result
              errors shouldBe empty
            } else {
              errors.loneElement should beThe[Error](Pattern.quote(msg))
            }
          }
        }
      }
    }

    "reference to constant" in {
      val entity = """|fsm a {
                      |  const u36 A = {{24{1'b0}}, {12{1'b1}}};
                      |  const u41 B = {5'h1, A[35:0]};
                      |
                      |  out u41 o;
                      |
                      |  void main() {
                      |    o = B;
                      |    fence;
                      |  }
                      |}""".stripMargin.asTree[Entity]

      val expr = xform(entity) collectFirst {
        case StmtAssign(_, rhs) => rhs
      }

      expr.value.simplify shouldBe ExprInt(false, 41, 0x1000000fffL)
    }

    "cast" - {
      for {
        (expr, res, err) <- List(
          // format: off
          (ExprCast(TypeUInt(Expr(8)), Expr(32)), ExprInt(false, 8, 32), ""),
          (ExprCast(TypeSInt(Expr(8)), Expr(32)), ExprInt(true, 8, 32), ""),
          (ExprCast(TypeSInt(Expr(8)), ExprNum(true, -1)), ExprInt(true, 8, -1), ""),
          (ExprCast(TypeUInt(Expr(4)), Expr(32)), ExprError(), "Value 32 cannot be represented with 4 unsigned bits"),
          (ExprCast(TypeSInt(Expr(4)), Expr(32)), ExprError(), "Value 32 cannot be represented with 4 signed bits"),
          (ExprCast(TypeUInt(Expr(5)), Expr(31)), ExprInt(false, 5, 31), ""),
          (ExprCast(TypeSInt(Expr(5)), Expr(31)), ExprError(), "Value 31 cannot be represented with 5 signed bits"),
          (ExprCast(TypeUInt(Expr(8)), ExprNum(true, -1)), ExprError(), "Negative value cannot be represented as unsigned"),
          (ExprCast(TypeNum(true), ExprInt(true, 10, 12)), ExprNum(true, 12), ""),
          (ExprCast(TypeNum(true), ExprInt(false, 10, 12)), ExprNum(true, 12), ""),
          (ExprCast(TypeNum(false), ExprInt(true, 10, 12)), ExprNum(false, 12), ""),
          (ExprCast(TypeNum(false), ExprInt(false, 10, 12)), ExprNum(false, 12), ""),
          (ExprCast(TypeNum(true), ExprInt(true, 10, -1)), ExprNum(true, -1), ""),
          (ExprCast(TypeNum(false), ExprInt(true, 10, -1)), ExprError(), "Negative value cannot be represented as unsigned")
          // format: on
        )
      } {
        expr.toSource in {
          expr regularize Loc.synthetic
          expr rewrite fold shouldBe res
          if (err.isEmpty) {
            cc.messages shouldBe empty
          } else {
            cc.messages.loneElement should beThe[Error](err)
          }
        }
      }
    }
  }
}
