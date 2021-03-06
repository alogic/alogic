////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.AlogicTest
import com.argondesign.alogic.SourceTextConverters._
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Types.TypeUInt
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable.ListBuffer

final class StatelessTreeTransformerSpec extends AnyFlatSpec with AlogicTest {

  implicit val cc: CompilerContext = new CompilerContext

  "A TreeTransformer" should "rewrite all nodes where it applies" in {
    val tree = """{
                 |  u1 foo;
                 |  u2 foo;
                 |}""".stripMargin.asTree[Stmt]() rewrite new StatelessTreeTransformer {
      override val typed = false
      override def transform(tree: Tree): Tree = tree match {
        case _: Ident => Ident("bar", Nil) withLoc tree.loc
        case other    => other
      }
    }

    tree should matchPattern {
      case StmtBlock(
            List(
              StmtSplice(DescVar(Ident("bar", Nil), Nil, ExprType(TypeUInt(w1)), None)),
              StmtSplice(DescVar(Ident("bar", Nil), Nil, ExprType(TypeUInt(w2)), None))
            )
          ) if w1 == 1 && w2 == 2 =>
    }
  }

  it should "return the same tree instance if it is not rewritten" in {
    val oldTree = "{ a = b; bool c = a; }".asTree[Stmt]()

    val result = new ListBuffer[String]()

    val newTree = oldTree rewrite new StatelessTreeTransformer {
      override val typed = false
      private val it = Iterator.from(0)
      override def transform(tree: Tree): Tree = tree tap { _ =>
        result.addOne(s"Saw it ${it.next()}")
      }
    }

    newTree should be theSameInstanceAs oldTree

    result should have length 9
    result.zipWithIndex foreach {
      case (msg, idx) => msg shouldBe s"Saw it $idx"
    }

  }

}
