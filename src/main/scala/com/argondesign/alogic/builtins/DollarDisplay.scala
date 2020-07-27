////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2020 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Builtin '$display'
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.builtins

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Loc
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.frontend.Frontend

private[builtins] class DollarDisplay(implicit cc: CompilerContext) extends BuiltinPolyFunc {

  val name = "$display"

  private def validArg(expr: Expr) = expr.tpe match {
    case TypeVoid   => false
    case _: TypeNum => true
    case kind       => kind.isPacked
  }

  def returnType(args: List[Expr], feOpt: Option[Frontend]): Option[TypeFund] = args partialMatch {
    case Nil                                                         => TypeVoid
    case str :: rest if str.tpe == TypeStr && (rest forall validArg) => TypeVoid
  }

  def isKnown(args: List[Expr]) = false

  val isPure: Boolean = false

  def simplify(loc: Loc, args: List[Expr]) = None
}
