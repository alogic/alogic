////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2020 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
//  Tests for parsing Rec
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.frontend

import com.argondesign.alogic.AlogicTest
import com.argondesign.alogic.SourceTextConverters._
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.FuncVariant
import com.argondesign.alogic.core.Types._
import org.scalatest.freespec.AnyFreeSpec

final class ParserParseRecSpec extends AnyFreeSpec with AlogicTest {

  implicit val cc: CompilerContext = new CompilerContext

  "The parser should build correct ASTs for Rec" - {
    "definition" - {
      "var" in {
        "bool x;".asTree[Rec] shouldBe {
          RecSplice(DescVar(Ident("x", Nil), Nil, ExprType(TypeUInt(1)), None))
        }
      }

      "method" in {
        """void f() {}""".asTree[Rec] shouldBe {
          RecSplice(
            DescFunc(Ident("f", Nil), Nil, FuncVariant.Method, ExprType(TypeVoid), Nil, Nil)
          )
        }
      }

      "static method" in {
        """static void f() {}""".asTree[Rec] shouldBe {
          RecSplice(
            DescFunc(Ident("f", Nil), Nil, FuncVariant.Static, ExprType(TypeVoid), Nil, Nil)
          )
        }
      }
    }

    "import" in {
      "import a;".asTree[Rec] shouldBe {
        RecSplice(ImportOne(0, ExprIdent(Ident("a", Nil)), None))
      }
    }

    "using" in {
      "using a;".asTree[Rec] shouldBe {
        RecSplice(UsingOne(ExprIdent(Ident("a", Nil)), None))
      }
    }

    "from" in {
      "from a import b;".asTree[Rec] shouldBe {
        RecSplice(FromOne(0, ExprIdent(Ident("a", Nil)), ExprIdent(Ident("b", Nil)), None))
      }
    }

    "assertion" in {
      "static assert false;".asTree[Rec] shouldBe {
        RecSplice(AssertionStatic(ExprInt(false, 1, 0), None))
      }
    }
  }
}
