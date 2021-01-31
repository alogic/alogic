////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.Symbols.Symbol

trait DeclOps { this: Decl =>

  def decls: List[Decl] = Nil

  def cpy(symbol: Symbol): Decl = this match {
    // $COVERAGE-OFF$ Trivial to keep full, but not necessarily used
    case node: DeclVar       => node.copy(symbol = symbol)
    case node: DeclVal       => node.copy(symbol = symbol)
    case node: DeclStatic    => node.copy(symbol = symbol)
    case node: DeclIn        => node.copy(symbol = symbol)
    case node: DeclOut       => node.copy(symbol = symbol)
    case node: DeclPipeVar   => node.copy(symbol = symbol)
    case node: DeclPipeIn    => node.copy(symbol = symbol)
    case node: DeclPipeOut   => node.copy(symbol = symbol)
    case node: DeclConst     => node.copy(symbol = symbol)
    case node: DeclArray     => node.copy(symbol = symbol)
    case node: DeclSram      => node.copy(symbol = symbol)
    case node: DeclStack     => node.copy(symbol = symbol)
    case node: DeclType      => node.copy(symbol = symbol)
    case node: DeclEntity    => node.copy(symbol = symbol)
    case node: DeclRecord    => node.copy(symbol = symbol)
    case node: DeclInstance  => node.copy(symbol = symbol)
    case node: DeclSingleton => node.copy(symbol = symbol)
    case node: DeclFunc      => node.copy(symbol = symbol)
    case node: DeclState     => node.copy(symbol = symbol)
    // $COVERAGE-ON$
  }

}

trait DeclObjOps { self: Decl.type =>

  final def unapply(decl: Decl): Some[Symbol] = Some(decl.symbol)

}
