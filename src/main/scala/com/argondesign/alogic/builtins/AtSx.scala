////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2020 Argon Design Ltd. All rights reserved.
//
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Builtin '@sx'
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.builtins

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Loc
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.frontend.Complete
import com.argondesign.alogic.frontend.Frontend

private[builtins] class AtSx(implicit cc: CompilerContext) extends BuiltinPolyFunc {

  val name = "@sx"

  def returnType(args: List[Expr], feOpt: Option[Frontend]): Option[TypeFund] = args match {
    case List(width, expr) if expr.tpe.isPacked =>
      feOpt match {
        case Some(fe) =>
          fe.evaluate(width, s"first argument of '$name' (width)") match {
            case Complete(value) => Some(TypeInt(expr.tpe.isSigned, value.toInt))
            case _               => None
          }
        case None => Some(TypeInt(expr.tpe.isSigned, width.value.get.toInt))
      }
    case _ => None
  }

  val isPure: Boolean = true

  def simplify(loc: Loc, args: List[Expr]) = {
    val List(width, expr) = args
    AtMsb.fold(loc, expr) flatMap { msb =>
      AtEx.fold(loc, msb, width, expr)
    }
  }

}
