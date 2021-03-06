////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Build an Arg AST from an Antlr4 parse tree
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.antlr

import com.argondesign.alogic.antlr.AlogicParser._
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.MessageBuffer
import com.argondesign.alogic.core.SourceContext

object ArgBuilder extends BaseBuilder[ArgContext, Arg] {

  def apply(ctx: ArgContext)(implicit mb: MessageBuffer, sc: SourceContext): Arg = {
    object Visitor extends AlogicScalarVisitor[Arg] {
      override def visitArgNamed(ctx: ArgNamedContext): Arg = {
        val Ident(name, idxs) = IdentBuilder(ctx.ident)
        val expr = ExprBuilder(ctx.expr)
        val loc = ctx.loc.copy(point = ctx.point.getStartIndex)
        if (idxs.isEmpty) {
          ArgN(name, expr) withLoc loc
        } else {
          ArgD(name, idxs, expr) withLoc loc
        }
      }

      override def visitArgPositional(ctx: ArgPositionalContext): Arg =
        ArgP(ExprBuilder(ctx.expr)) withLoc ctx.loc
    }

    Visitor(ctx)
  }

  def apply(ctx: ArgsContext)(implicit mb: MessageBuffer, sc: SourceContext): List[Arg] =
    if (ctx == null) Nil else apply(ctx.arg)

}
