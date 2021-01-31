////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.ast

import com.argondesign.alogic.ast.Trees._

trait DefnFuncOps { this: DefnFunc =>

  final lazy val decls: List[Decl] = body collect { case StmtSplice(decl: Decl) => decl }

  final override lazy val defns: List[Defn] = body collect { case StmtSplice(defn: Defn) => defn }

}
