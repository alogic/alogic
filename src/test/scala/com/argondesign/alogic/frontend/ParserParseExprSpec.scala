////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
//  Tests for parsing Expr
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.frontend

import com.argondesign.alogic.AlogicTest
import com.argondesign.alogic.SourceTextConverters._
import com.argondesign.alogic.ast.Trees.Expr.ImplicitConversions._
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Messages.Error
import com.argondesign.alogic.core.Messages.Warning
import com.argondesign.alogic.core.Types._
import org.scalatest.freespec.AnyFreeSpec

final class ParserParseExprSpec extends AnyFreeSpec with AlogicTest {

  implicit val cc: CompilerContext = new CompilerContext

  "The parser should build correct ASTs for Expr" - {
    // TODO: match Trees enumeration order
    "bool" in {
      "bool".asTree[Expr]() shouldBe ExprType(TypeUInt(1))
    }

    "bool is same as u1" in {
      "bool".asTree[Expr]() shouldBe "u1".asTree[Expr]()
    }

    "fixed unsigned ints" in {
      forAll(List("u1", "u2", "u3", "u44", "u128")) { str =>
        str.asTree[Expr]() shouldBe ExprType(TypeUInt(str.tail.toInt))
      }
    }

    "fixed signed ints" in {
      forAll(List("i1", "i2", "i3", "i44", "i128")) { str =>
        str.asTree[Expr]() shouldBe ExprType(TypeSInt(str.tail.toInt))
      }
    }

    "parametrized integers" - {
      "unsigned" in {
        "uint(N)".asTree[Expr]() shouldBe ExprCall(
          ExprType(TypeNum(false)),
          List(ArgP(ExprIdent("N", Nil)))
        )
      }

      "signed" in {
        "int(N)".asTree[Expr]() shouldBe ExprCall(
          ExprType(TypeNum(true)),
          List(ArgP(ExprIdent("N", Nil)))
        )
      }
    }

    "vectors" - {
      "1D u2" in {
        "u2[8]".asTree[Expr]() shouldBe ExprIndex(ExprType(TypeUInt(2)), Expr(8))
      }

      "2D u2" in {
        "u2[4][8]".asTree[Expr]() shouldBe {
          ExprIndex(ExprIndex(ExprType(TypeUInt(2)), Expr(4)), Expr(8))
        }
      }

      "1D i2" in {
        "i2[8]".asTree[Expr]() shouldBe ExprIndex(ExprType(TypeSInt(2)), Expr(8))
      }

      "2D i2" in {
        "i2[4][8]".asTree[Expr]() shouldBe {
          ExprIndex(ExprIndex(ExprType(TypeSInt(2)), Expr(4)), Expr(8))
        }
      }

      "1D uint(3)" in {
        "uint(3)[8]".asTree[Expr]() shouldBe ExprIndex(
          ExprCall(ExprType(TypeNum(false)), ArgP(Expr(3)) :: Nil),
          Expr(8)
        )
      }

      "2D uint(3)" in {
        "uint(3)[4][8]".asTree[Expr]() shouldBe {
          ExprIndex(
            ExprIndex(ExprCall(ExprType(TypeNum(false)), ArgP(Expr(3)) :: Nil), Expr(4)),
            Expr(8)
          )
        }
      }

      "1D int(3)" in {
        "int(3)[8]".asTree[Expr]() shouldBe ExprIndex(
          ExprCall(ExprType(TypeNum(true)), ArgP(Expr(3)) :: Nil),
          Expr(8)
        )
      }

      "2D int(3)" in {
        "int(3)[4][8]".asTree[Expr]() shouldBe {
          ExprIndex(
            ExprIndex(ExprCall(ExprType(TypeNum(true)), ArgP(Expr(3)) :: Nil), Expr(4)),
            Expr(8)
          )
        }
      }

      "1D bool" in {
        "bool[8]".asTree[Expr]() shouldBe ExprIndex(ExprType(TypeUInt(1)), Expr(8))
      }

      "2D bool" in {
        "bool[4][8]".asTree[Expr]() shouldBe {
          ExprIndex(ExprIndex(ExprType(TypeUInt(1)), Expr(4)), Expr(8))
        }
      }
    }

    "void" in {
      "void".asTree[Expr]() shouldBe ExprType(TypeVoid)
    }

    "unsized int" in {
      "int".asTree[Expr]() shouldBe ExprType(TypeNum(true))
    }

    "unsized uint" in {
      "uint".asTree[Expr]() shouldBe ExprType(TypeNum(false))
    }

    "literals" - {
      "string" in {
        "\"foo\"".asTree[Expr]() shouldBe ExprStr("foo")
      }

      "true" in {
        "true".asTree[Expr]() shouldBe ExprInt(false, 1, 1)
      }

      "false" in {
        "false".asTree[Expr]() shouldBe ExprInt(false, 1, 0)
      }

      "unsized integers" - {
        for {
          (literal, msgOrResult) <- List(
            ("      17 ", Right(ExprNum(false, 17))),
            ("     +17 ", Right(ExprNum(false, 17))),
            ("     -17 ", Left("Negative unsigned literal")),
            ("      17u", Right(ExprNum(false, 17))),
            ("     +17u", Right(ExprNum(false, 17))),
            ("     -17u", Left("Negative unsigned literal")),
            ("      17s", Right(ExprNum(true, 17))),
            ("     +17s", Right(ExprNum(true, 17))),
            ("     -17s", Right(ExprNum(true, -17))),
            (" 0b10001 ", Right(ExprNum(false, 17))),
            ("+0b10001 ", Right(ExprNum(false, 17))),
            ("-0b10001 ", Left("Negative unsigned literal")),
            (" 0b10001u", Right(ExprNum(false, 17))),
            ("+0b10001u", Right(ExprNum(false, 17))),
            ("-0b10001u", Left("Negative unsigned literal")),
            (" 0b10001s", Right(ExprNum(true, 17))),
            ("+0b10001s", Right(ExprNum(true, 17))),
            ("-0b10001s", Right(ExprNum(true, -17))),
            ("    0o21 ", Right(ExprNum(false, 17))),
            ("   +0o21 ", Right(ExprNum(false, 17))),
            ("   -0o21 ", Left("Negative unsigned literal")),
            ("    0o21u", Right(ExprNum(false, 17))),
            ("   +0o21u", Right(ExprNum(false, 17))),
            ("   -0o21u", Left("Negative unsigned literal")),
            ("    0o21s", Right(ExprNum(true, 17))),
            ("   +0o21s", Right(ExprNum(true, 17))),
            ("   -0o21s", Right(ExprNum(true, -17))),
            ("    0d17 ", Right(ExprNum(false, 17))),
            ("   +0d17 ", Right(ExprNum(false, 17))),
            ("   -0d17 ", Left("Negative unsigned literal")),
            ("    0d17u", Right(ExprNum(false, 17))),
            ("   +0d17u", Right(ExprNum(false, 17))),
            ("   -0d17u", Left("Negative unsigned literal")),
            ("    0d17s", Right(ExprNum(true, 17))),
            ("   +0d17s", Right(ExprNum(true, 17))),
            ("   -0d17s", Right(ExprNum(true, -17))),
            ("   0d017 ", Right(ExprNum(false, 17))),
            ("  +0d017 ", Right(ExprNum(false, 17))),
            ("  -0d017 ", Left("Negative unsigned literal")),
            ("   0d017u", Right(ExprNum(false, 17))),
            ("  +0d017u", Right(ExprNum(false, 17))),
            ("  -0d017u", Left("Negative unsigned literal")),
            ("   0d017s", Right(ExprNum(true, 17))),
            ("  +0d017s", Right(ExprNum(true, 17))),
            ("  -0d017s", Right(ExprNum(true, -17))),
            ("    0x11 ", Right(ExprNum(false, 17))),
            ("   +0x11 ", Right(ExprNum(false, 17))),
            ("   -0x11 ", Left("Negative unsigned literal")),
            ("    0x11u", Right(ExprNum(false, 17))),
            ("   +0x11u", Right(ExprNum(false, 17))),
            ("   -0x11u", Left("Negative unsigned literal")),
            ("    0x11s", Right(ExprNum(true, 17))),
            ("   +0x11s", Right(ExprNum(true, 17))),
            ("   -0x11s", Right(ExprNum(true, -17))),
            ("       0 ", Right(ExprNum(false, 0))),
            ("      +0 ", Right(ExprNum(false, 0))),
            ("      -0 ", Right(ExprNum(false, 0))),
            ("       0u", Right(ExprNum(false, 0))),
            ("      +0u", Right(ExprNum(false, 0))),
            ("      -0u", Right(ExprNum(false, 0))),
            ("       0s", Right(ExprNum(true, 0))),
            ("      +0s", Right(ExprNum(true, 0))),
            ("      -0s", Right(ExprNum(true, 0))),
            // Whitespace after sign
            ("     + 17 ", Right(ExprNum(false, 17))),
            ("     - 17 ", Left("Negative unsigned literal")),
            ("     + 17u", Right(ExprNum(false, 17))),
            ("     - 17u", Left("Negative unsigned literal")),
            ("     + 17s", Right(ExprNum(true, 17))),
            ("     - 17s", Right(ExprNum(true, -17))),
            ("+ 0b10001 ", Right(ExprNum(false, 17))),
            ("- 0b10001 ", Left("Negative unsigned literal")),
            ("+ 0b10001u", Right(ExprNum(false, 17))),
            ("- 0b10001u", Left("Negative unsigned literal")),
            ("+ 0b10001s", Right(ExprNum(true, 17))),
            ("- 0b10001s", Right(ExprNum(true, -17))),
            ("   + 0x11 ", Right(ExprNum(false, 17))),
            ("   - 0x11 ", Left("Negative unsigned literal")),
            ("   + 0x11u", Right(ExprNum(false, 17))),
            ("   - 0x11u", Left("Negative unsigned literal")),
            ("   + 0x11s", Right(ExprNum(true, 17))),
            ("   - 0x11s", Right(ExprNum(true, -17))),
            ("      + 0 ", Right(ExprNum(false, 0))),
            ("      - 0 ", Right(ExprNum(false, 0))),
            ("      + 0u", Right(ExprNum(false, 0))),
            ("      - 0u", Right(ExprNum(false, 0))),
            ("      + 0s", Right(ExprNum(true, 0))),
            ("      - 0s", Right(ExprNum(true, 0))),
            // Underscores
            ("      1_7 ", Right(ExprNum(false, 17))),
            ("     +1_7 ", Right(ExprNum(false, 17))),
            ("     -1_7 ", Left("Negative unsigned literal")),
            ("      1_7u", Right(ExprNum(false, 17))),
            ("     +1_7u", Right(ExprNum(false, 17))),
            ("     -1_7u", Left("Negative unsigned literal")),
            ("      1_7s", Right(ExprNum(true, 17))),
            ("     +1_7s", Right(ExprNum(true, 17))),
            ("     -1_7s", Right(ExprNum(true, -17))),
            (" 0b1_0_0_0_1 ", Right(ExprNum(false, 17))),
            ("+0b1_0_0_0_1 ", Right(ExprNum(false, 17))),
            ("-0b1_0_0_0_1 ", Left("Negative unsigned literal")),
            (" 0b1_0_0_0_1u", Right(ExprNum(false, 17))),
            ("+0b1_0_0_0_1u", Right(ExprNum(false, 17))),
            ("-0b1_0_0_0_1u", Left("Negative unsigned literal")),
            (" 0b1_0_0_0_1s", Right(ExprNum(true, 17))),
            ("+0b1_0_0_0_1s", Right(ExprNum(true, 17))),
            ("-0b1_0_0_0_1s", Right(ExprNum(true, -17))),
            ("    0x1_1 ", Right(ExprNum(false, 17))),
            ("   +0x1_1 ", Right(ExprNum(false, 17))),
            ("   -0x1_1 ", Left("Negative unsigned literal")),
            ("    0x1_1u", Right(ExprNum(false, 17))),
            ("   +0x1_1u", Right(ExprNum(false, 17))),
            ("   -0x1_1u", Left("Negative unsigned literal")),
            ("    0x1_1s", Right(ExprNum(true, 17))),
            ("   +0x1_1s", Right(ExprNum(true, 17))),
            ("   -0x1_1s", Right(ExprNum(true, -17))),
            // Malformed cases
            ("     0b2 ", Left("Invalid digit for base 2 value")),
            ("     0o8 ", Left("Invalid digit for base 8 value")),
            ("     0da ", Left("Invalid digit for base 10 value")),
            (
              "     017 ",
              Left(
                "Invalid literal '017',\nuse prefix '0o' for octal or '0d' for decimal with leading zeros"
              )
            )
          )
        } {
          literal in {
            msgOrResult match {
              case Left(msg) =>
                an[AsTreeSyntaxErrorException] shouldBe thrownBy(literal.asTree[Expr]())
                cc.messages.loneElement should beThe[Error]((msg split '\n').toSeq: _*)
              case Right(result) =>
                literal.asTree[Expr]() shouldBe result
            }
          }
        }
      }

      "sized integers" - {
        sealed trait Outcome
        case class Ok(expr: Expr) extends Outcome
        case class Warn(expr: Expr, msg: String) extends Outcome
        case class Err(msg: String) extends Outcome

        for {
          (literal, outcome) <- List(
            // format: off
            (" 4'd3     ", Ok(ExprInt(false, 4, 3))),
            (" 4'sd3    ", Ok(ExprInt(true, 4, 3))),
            ("+4'd3     ", Ok(ExprInt(false, 4, 3))),
            ("+4'sd3    ", Ok(ExprInt(true, 4, 3))),
            ("-4'd3     ", Err("Negative unsigned literal")),
            ("-4'sd3    ", Ok(ExprInt(true, 4, -3))),
            (" 4'd0     ", Ok(ExprInt(false, 4, 0))),
            (" 4'sd0    ", Ok(ExprInt(true, 4, 0))),
            ("+4'd0     ", Ok(ExprInt(false, 4, 0))),
            ("+4'sd0    ", Ok(ExprInt(true, 4, 0))),
            ("-4'd0     ", Ok(ExprInt(false, 4, 0))),
            ("-4'sd0    ", Ok(ExprInt(true, 4, 0))),
            (" 4'd1     ", Ok(ExprInt(false, 4, 1))),
            (" 4'sd1    ", Ok(ExprInt(true, 4, 1))),
            ("+4'd1     ", Ok(ExprInt(false, 4, 1))),
            ("+4'sd1    ", Ok(ExprInt(true, 4, 1))),
            ("-4'd1     ", Err("Negative unsigned literal")),
            ("-4'sd1    ", Ok(ExprInt(true, 4, -1))),
            (" 4'd15    ", Ok(ExprInt(false, 4, 15))),
            (" 4'sd15   ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("+4'd15    ", Ok(ExprInt(false, 4, 15))),
            ("+4'sd15   ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("-4'd15    ", Err("Negative unsigned literal")),
            ("-4'sd15   ", Warn(ExprInt(true, 4, 1), "Apparently negative literal stands for positive value 1")),
            (" 4'd7     ", Ok(ExprInt(false, 4, 7))),
            (" 4'sd7    ", Ok(ExprInt(true, 4, 7))),
            ("+4'd7     ", Ok(ExprInt(false, 4, 7))),
            ("+4'sd7    ", Ok(ExprInt(true, 4, 7))),
            ("-4'd7     ", Err("Negative unsigned literal")),
            ("-4'sd7    ", Ok(ExprInt(true, 4, -7))),
            (" 4'd8     ", Ok(ExprInt(false, 4, 8))),
            (" 4'sd8    ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("+4'd8     ", Ok(ExprInt(false, 4, 8))),
            ("+4'sd8    ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("-4'd8     ", Err("Negative unsigned literal")),
            ("-4'sd8    ", Ok(ExprInt(true, 4, -8))),
            (" 4'd16    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            (" 4'sd16   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'd16    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'sd16   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'd16    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'sd16   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            (" 4'b11    ", Ok(ExprInt(false, 4, 3))),
            (" 4'sb11   ", Ok(ExprInt(true, 4, 3))),
            ("+4'b11    ", Ok(ExprInt(false, 4, 3))),
            ("+4'sb11   ", Ok(ExprInt(true, 4, 3))),
            ("-4'b11    ", Err("Negative unsigned literal")),
            ("-4'sb11   ", Ok(ExprInt(true, 4, -3))),
            (" 4'b0     ", Ok(ExprInt(false, 4, 0))),
            (" 4'sb0    ", Ok(ExprInt(true, 4, 0))),
            ("+4'b0     ", Ok(ExprInt(false, 4, 0))),
            ("+4'sb0    ", Ok(ExprInt(true, 4, 0))),
            ("-4'b0     ", Ok(ExprInt(false, 4, 0))),
            ("-4'sb0    ", Ok(ExprInt(true, 4, 0))),
            (" 4'b1     ", Ok(ExprInt(false, 4, 1))),
            (" 4'sb1    ", Ok(ExprInt(true, 4, 1))),
            ("+4'b1     ", Ok(ExprInt(false, 4, 1))),
            ("+4'sb1    ", Ok(ExprInt(true, 4, 1))),
            ("-4'b1     ", Err("Negative unsigned literal")),
            ("-4'sb1    ", Ok(ExprInt(true, 4, -1))),
            (" 4'b1111  ", Ok(ExprInt(false, 4, 15))),
            (" 4'sb1111 ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("+4'b1111  ", Ok(ExprInt(false, 4, 15))),
            ("+4'sb1111 ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("-4'b1111  ", Err("Negative unsigned literal")),
            ("-4'sb1111 ", Warn(ExprInt(true, 4, 1), "Apparently negative literal stands for positive value 1")),
            (" 4'b111   ", Ok(ExprInt(false, 4, 7))),
            (" 4'sb111  ", Ok(ExprInt(true, 4, 7))),
            ("+4'b111   ", Ok(ExprInt(false, 4, 7))),
            ("+4'sb111  ", Ok(ExprInt(true, 4, 7))),
            ("-4'b111   ", Err("Negative unsigned literal")),
            ("-4'sb111  ", Ok(ExprInt(true, 4, -7))),
            (" 4'b1000  ", Ok(ExprInt(false, 4, 8))),
            (" 4'sb1000 ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("+4'b1000  ", Ok(ExprInt(false, 4, 8))),
            ("+4'sb1000 ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("-4'b1000  ", Err("Negative unsigned literal")),
            ("-4'sb1000 ", Ok(ExprInt(true, 4, -8))),
            (" 4'b10000 ", Err("Value specifier for 4 bit literal requires 5 bits")),
            (" 4'sb10000", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'b10000 ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'sb10000", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'b10000 ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'sb10000", Err("Value specifier for 4 bit literal requires 5 bits")),
            (" 4'h3     ", Ok(ExprInt(false, 4, 3))),
            (" 4'sh3    ", Ok(ExprInt(true, 4, 3))),
            ("+4'h3     ", Ok(ExprInt(false, 4, 3))),
            ("+4'sh3    ", Ok(ExprInt(true, 4, 3))),
            ("-4'h3     ", Err("Negative unsigned literal")),
            ("-4'sh3    ", Ok(ExprInt(true, 4, -3))),
            (" 4'h0     ", Ok(ExprInt(false, 4, 0))),
            (" 4'sh0    ", Ok(ExprInt(true, 4, 0))),
            ("+4'h0     ", Ok(ExprInt(false, 4, 0))),
            ("+4'sh0    ", Ok(ExprInt(true, 4, 0))),
            ("-4'h0     ", Ok(ExprInt(false, 4, 0))),
            ("-4'sh0    ", Ok(ExprInt(true, 4, 0))),
            (" 4'h1     ", Ok(ExprInt(false, 4, 1))),
            (" 4'sh1    ", Ok(ExprInt(true, 4, 1))),
            ("+4'h1     ", Ok(ExprInt(false, 4, 1))),
            ("+4'sh1    ", Ok(ExprInt(true, 4, 1))),
            ("-4'h1     ", Err("Negative unsigned literal")),
            ("-4'sh1    ", Ok(ExprInt(true, 4, -1))),
            (" 4'hf     ", Ok(ExprInt(false, 4, 15))),
            (" 4'shf    ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("+4'hf     ", Ok(ExprInt(false, 4, 15))),
            ("+4'shf    ", Warn(ExprInt(true, 4, -1), "Apparently positive literal stands for negative value -1")),
            ("-4'hf     ", Err("Negative unsigned literal")),
            ("-4'shf    ", Warn(ExprInt(true, 4, 1), "Apparently negative literal stands for positive value 1")),
            (" 4'h7     ", Ok(ExprInt(false, 4, 7))),
            (" 4'sh7    ", Ok(ExprInt(true, 4, 7))),
            ("+4'h7     ", Ok(ExprInt(false, 4, 7))),
            ("+4'sh7    ", Ok(ExprInt(true, 4, 7))),
            ("-4'h7     ", Err("Negative unsigned literal")),
            ("-4'sh7    ", Ok(ExprInt(true, 4, -7))),
            (" 4'h8     ", Ok(ExprInt(false, 4, 8))),
            (" 4'sh8    ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("+4'h8     ", Ok(ExprInt(false, 4, 8))),
            ("+4'sh8    ", Warn(ExprInt(true, 4, -8), "Apparently positive literal stands for negative value -8")),
            ("-4'h8     ", Err("Negative unsigned literal")),
            ("-4'sh8    ", Ok(ExprInt(true, 4, -8))),
            (" 4'h10    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            (" 4'sh10   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'sh10   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("+4'h10    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'h10    ", Err("Value specifier for 4 bit literal requires 5 bits")),
            ("-4'sh10   ", Err("Value specifier for 4 bit literal requires 5 bits")),
            // Malformed cases
            (" 4'b2     ", Err("Invalid digit for base 2 value")),
            (" 4'da     ", Err("Invalid digit for base 10 value")),
            (" 0'b0     ", Err("0 width integer literal")),
            (" 0'd0     ", Err("0 width integer literal")),
            (" 0'h0     ", Err("0 width integer literal"))
            // format: on
          )
        } {
          literal in {
            outcome match {
              case Ok(result) =>
                literal.asTree[Expr]() shouldBe result
              case Warn(result, msg) =>
                literal.asTree[Expr]() shouldBe result
                cc.messages.loneElement should beThe[Warning](msg)
              case Err(msg) =>
                an[AsTreeSyntaxErrorException] shouldBe thrownBy(literal.asTree[Expr]())
                cc.messages.loneElement should beThe[Error](msg)
            }
          }
        }
      }

    }

    "simple" - {
      "bracket" in {
        "(((1)))".asTree[Expr]() shouldBe Expr(1)
      }

      "call" - {
        "with no arguments" in {
          "a()".asTree[Expr]() shouldBe ExprCall(ExprIdent("a", Nil), Nil)
        }

        "with 1 positional argument" in {
          "b(2)".asTree[Expr]() shouldBe ExprCall(ExprIdent("b", Nil), List(ArgP(Expr(2))))
        }

        "with 2 positional arguments" in {
          "c(d, e)".asTree[Expr]() shouldBe {
            ExprCall(
              ExprIdent("c", Nil),
              List(ArgP(ExprIdent("d", Nil)), ArgP(ExprIdent("e", Nil)))
            )
          }
        }

        "with 1 named argument" in {
          "b(x = 2)"
            .asTree[Expr]() shouldBe ExprCall(ExprIdent("b", Nil), List(ArgN("x", Expr(2))))
        }

        "with 2 named arguments" in {
          "c(x=d, y=e)".asTree[Expr]() shouldBe {
            ExprCall(
              ExprIdent("c", Nil),
              List(ArgN("x", ExprIdent("d", Nil)), ArgN("y", ExprIdent("e", Nil)))
            )
          }
        }

        "with 1 positional and 1 named argument" in {
          "c(d, y=e)".asTree[Expr]() shouldBe {
            ExprCall(
              ExprIdent("c", Nil),
              List(ArgP(ExprIdent("d", Nil)), ArgN("y", ExprIdent("e", Nil)))
            )
          }
        }

        "with 1 dict argument" in {
          "c(x#[0]=d)".asTree[Expr]() shouldBe {
            ExprCall(
              ExprIdent("c", Nil),
              List(
                ArgD("x", Expr(0) :: Nil, ExprIdent("d", Nil))
              )
            )
          }
        }

        "with 2 dict arguments" in {
          "c(x#[0]=d, x#[1]=e)".asTree[Expr]() shouldBe {
            ExprCall(
              ExprIdent("c", Nil),
              List(
                ArgD("x", Expr(0) :: Nil, ExprIdent("d", Nil)),
                ArgD("x", Expr(1) :: Nil, ExprIdent("e", Nil))
              )
            )
          }
        }
      }

      for (
        op <- List(
          "+",
          "-",
          "~",
          "!",
          "&",
          "|",
          "^",
          "'"
        )
      ) {
        s"unary $op" in {
          s"$op(2)".asTree[Expr]() shouldBe ExprUnary(op, Expr(2))
        }
      }

      for (
        op <- List(
          "'",
          "*",
          "/",
          "%",
          "+",
          "-",
          "<<",
          ">>",
          ">>>",
          "<<<",
          ">",
          ">=",
          "<",
          "<=",
          "==",
          "!=",
          "&",
          "^",
          "|",
          "&&",
          "||"
        )
      ) {
        s"binary $op" in {
          s"4 $op 3".asTree[Expr]() shouldBe ExprBinary(Expr(4), op, Expr(3))
        }
      }

      "ternary" in {
        "1 ? 2 : 3".asTree[Expr]() shouldBe ExprCond(Expr(1), Expr(2), Expr(3))
      }

      "repetition" in {
        "{N{a}}"
          .asTree[Expr]() shouldBe ExprRep(ExprIdent("N", Nil), ExprIdent("a", Nil))
      }

      "concatenation" in {
        "{0, 1, 2}".asTree[Expr]() shouldBe ExprCat(List(Expr(0), Expr(1), Expr(2)))
      }

      "multiple concatenation " in {
        "{N{a, b}}".asTree[Expr]() shouldBe {
          ExprRep(
            ExprIdent("N", Nil),
            ExprCat(List(ExprIdent("a", Nil), ExprIdent("b", Nil)))
          )
        }
      }

      "index 1x" in {
        "a[0]".asTree[Expr]() shouldBe ExprIndex(ExprIdent("a", Nil), Expr(0))
      }

      "index 2x" in {
        "a[0][2]".asTree[Expr]() shouldBe {
          ExprIndex(ExprIndex(ExprIdent("a", Nil), Expr(0)), Expr(2))
        }
      }

      "slice 1x" in {
        "b[1:0]"
          .asTree[Expr]() shouldBe ExprSlice(ExprIdent("b", Nil), Expr(1), ":", Expr(0))
      }

      "slice 2x" in {
        "b[2+:0][1-:1]".asTree[Expr]() should matchPattern {
          case ExprSlice(
                ExprSlice(ExprIdent("b", Nil), Expr(2), "+:", Expr(0)),
                Expr(1),
                "-:",
                Expr(1)
              ) =>
        }
      }

      "select 1x" in {
        "a.b".asTree[Expr]() shouldBe ExprDot(ExprIdent("a", Nil), "b", Nil)
      }

      "select 2x" in {
        "a.b.c".asTree[Expr]() shouldBe ExprDot(
          ExprDot(ExprIdent("a", Nil), "b", Nil),
          "c",
          Nil
        )
      }

      "select in" in {
        "a.in".asTree[Expr]() shouldBe ExprDot(ExprIdent("a", Nil), "in", Nil)
      }

      "select out" in {
        "a.out".asTree[Expr]() shouldBe ExprDot(ExprIdent("a", Nil), "out", Nil)
      }

      "@id" in {
        "@zx".asTree[Expr]() shouldBe ExprIdent("@zx", Nil)
      }

      "$id" in {
        "$clog2".asTree[Expr]() shouldBe ExprIdent("$clog2", Nil)
      }

      "@ call" in {
        "@zx(0, a)".asTree[Expr]() shouldBe {
          ExprCall(
            ExprIdent("@zx", Nil),
            List(ArgP(Expr(0)), ArgP(ExprIdent("a", Nil)))
          )
        }
      }

      "$ call" in {
        "$clog2(a)".asTree[Expr]() shouldBe {
          ExprCall(ExprIdent("$clog2", Nil), List(ArgP(ExprIdent("a", Nil))))
        }
      }

      "identifier" in {
        "foo".asTree[Expr]() shouldBe ExprIdent("foo", Nil)
      }

      "type" in {
        "i8".asTree[Expr]() shouldBe ExprType(TypeSInt(8))
      }
    }

    "honouring precedence" - {
      "1 + 2 * 3" in {
        "1 + 2 * 3".asTree[Expr]() shouldBe {
          Expr(1) + ExprBinary(Expr(2), "*", Expr(3))
        }
      }
      "1 + 2 + 3" in {
        "1 + 2 + 3".asTree[Expr]() shouldBe {
          ExprBinary(Expr(1), "+", Expr(2)) + Expr(3)
        }
      }

      "a.b && a.c" in {
        "a.b && a.c".asTree[Expr]() shouldBe {
          ExprDot(ExprIdent("a", Nil), "b", Nil) && ExprDot(
            ExprIdent("a", Nil),
            "c",
            Nil
          )
        }
      }

      "a.b && a.c == 1" in {
        "a.b && a.c == 1".asTree[Expr]() shouldBe {
          ExprDot(ExprIdent("a", Nil), "b", Nil) &&
          ExprBinary(ExprDot(ExprIdent("a", Nil), "c", Nil), "==", Expr(1))
        }
      }

      "a.b && a[0]" in {
        "a.b && a[0]".asTree[Expr]() shouldBe {
          ExprDot(ExprIdent("a", Nil), "b", Nil) && ExprIndex(
            ExprIdent("a", Nil),
            0
          )
        }
      }

      "a.b && a[1:0]" in {
        "a.b && a[1:0]".asTree[Expr]() shouldBe {
          ExprDot(ExprIdent("a", Nil), "b", Nil) && ExprSlice(
            ExprIdent("a", Nil),
            1,
            ":",
            0
          )
        }
      }

      "a.b[1]" in {
        "a.b[1]".asTree[Expr]() shouldBe {
          ExprIndex(ExprDot(ExprIdent("a", Nil), "b", Nil), 1)
        }
      }

      // TODO: complete all precedence checks
    }

    "honouring associativity" - {
      for {
        (expr, equiv) <- List(
          ("a()()", "(a())()"),
          ("a[0][0]", "(a[0])[0]"),
          ("a[1:0][1:0]", "(a[1:0])[1:0]"),
          ("a.b.c", "(a.b).c"),
          ("+ + (a)", "+ (+ (a))"),
          ("- - (a)", "- (- (a))"),
          ("~ ~ (a)", "~ (~ (a))"),
          ("! ! (a)", "! (! (a))"),
          ("& & (a)", "& (& (a))"),
          ("| | (a)", "| (| (a))"),
          ("^ ^ (a)", "^ (^ (a))"),
          ("a * b * c", "(a * b) * c"),
          ("a / b / c", "(a / b) / c"),
          ("a % b % c", "(a % b) % c"),
          ("a + b + c", "(a + b) + c"),
          ("a - b - c", "(a - b) - c"),
          ("a << b << c", "(a << b) << c"),
          ("a >> b >> c", "(a >> b) >> c"),
          ("a <<< b <<< c", "(a <<< b) <<< c"),
          ("a >>> b >>> c", "(a >>> b) >>> c"),
          ("a > b > c", "(a > b) > c"),
          ("a >= b >= c", "(a >= b) >= c"),
          ("a < b < c", "(a < b) < c"),
          ("a <= b <= c", "(a <= b) <= c"),
          ("a == b == c", "(a == b) == c"),
          ("a != b != c", "(a != b) != c"),
          ("a & b & c", "(a & b) & c"),
          ("a ^ b ^ c", "(a ^ b) ^ c"),
          ("a | b | c", "(a | b) | c"),
          ("a && b && c", "(a && b) && c"),
          ("a || b || c", "(a || b) || c"),
          ("a ? b : c ? d : e", "a ? b : (c ? d : e)")
        )
      } {
        expr in { expr.asTree[Expr]() shouldBe equiv.asTree[Expr]() }
      }
    }

    "this" in {
      an[AsTreeSyntaxErrorException] shouldBe thrownBy("this".asTree[Expr]())
      cc.messages.loneElement should beThe[Error] {
        "'this' reference is not user accessible"
      }
    }

    "keywords" - {
      "in" in {
        "in".asTree[Expr]() shouldBe ExprIdent("in", Nil)
      }

      "out" in {
        "out".asTree[Expr]() shouldBe ExprIdent("out", Nil)
      }
    }
  }
}
