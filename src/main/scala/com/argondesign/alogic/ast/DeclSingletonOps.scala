////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.Symbol

trait DeclSingletonOps { this: DeclSingleton =>

  final lazy val ports: List[Symbol] = decls collect {
    case decl: DeclIn      => decl.symbol
    case decl: DeclOut     => decl.symbol
    case decl: DeclSnoop   => decl.symbol
    case decl: DeclPipeIn  => decl.symbol
    case decl: DeclPipeOut => decl.symbol
  }

}
