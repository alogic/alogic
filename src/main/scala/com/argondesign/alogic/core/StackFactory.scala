////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Factory to build stack entities
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.core

import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.FlowControlTypes.FlowControlTypeNone
import com.argondesign.alogic.core.StorageTypes._
import com.argondesign.alogic.core.Types._
import com.argondesign.alogic.core.enums.EntityVariant

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

final class StackFactory {

  private val cache = mutable.Map[(TypeFund, Int), (DeclEntity, DefnEntity)]()

  def items: Iterator[((TypeFund, Int), (DeclEntity, DefnEntity))] = cache.iterator

  // Build a stack
  //
  //  fsm stack {
  //    typedef _ TYPE;
  //    const uint DEPTH = _;
  //
  //    static assert @bit(TYPE) >= 1;
  //    static assert DEPTH >= 2;
  //
  //    in       bool push;
  //    in       bool pop;
  //    in       bool set;
  //    in       TYPE d;
  //    out wire TYPE q;
  //
  //    gen for (uint n < DEPTH) {
  //      TYPE s#[n] = 0;
  //    }
  //
  //    storage#[0] -> q;
  //
  //    void() main {
  //      if (push & pop) {
  //        // Nothing to do if both pushing and popping. More specifically
  //        // ensure the top is not updated, to preserve outer locals.
  //      } else if (pop) {
  //        gen for (uint n = 0 ; n < DEPTH-1 ; n++) {
  //          s#[n] = s#[n+1];
  //        }
  //        s#[DEPTH-1] = 0;
  //      } else if (push) {
  //        gen for (uint n = DEPTH-1 ; n > 0; n--) {
  //          s#[n] = s#[n-1];
  //        }
  //        s#[0] = d;
  //      } else if (set) {
  //        s#[0] = d;
  //      }
  //      fence;
  //    }
  //  }

  private def build(kind: TypeFund, depth: Int): (DeclEntity, DefnEntity) = {
    val width = kind.width.toInt
    val signed = kind.isSigned
    val fcn = FlowControlTypeNone
    val stw = StorageTypeWire
    val loc = kind match {
      case k: TypeRecord => k.symbol.loc
      case _             => Loc.synthetic
    }

    val pusSymbol = Symbol("push", loc) tap { _.kind = TypeIn(TypeUInt(1), fcn) }
    val popSymbol = Symbol("pop", loc) tap { _.kind = TypeIn(TypeUInt(1), fcn) }
    val setSymbol = Symbol("set", loc) tap { _.kind = TypeIn(TypeUInt(1), fcn) }
    val dSymbol = Symbol("d", loc) tap { _.kind = TypeIn(kind, fcn) }
    val qSymbol = Symbol("q", loc) tap { _.kind = TypeOut(kind, fcn, stw) }
    val sSymbols = List from {
      (0 until depth).iterator map { n => Symbol(s"s$n", loc) tap { _.kind = kind } }
    }

    val pusDecl = pusSymbol.mkDecl regularize loc
    val popDecl = popSymbol.mkDecl regularize loc
    val setDecl = setSymbol.mkDecl regularize loc
    val dDecl = dSymbol.mkDecl regularize loc
    val qDecl = qSymbol.mkDecl regularize loc
    val sDecls = sSymbols map { sSymbol => sSymbol.mkDecl regularize loc }

    val pusDefn = pusSymbol.mkDefn
    val popDefn = popSymbol.mkDefn
    val setDefn = setSymbol.mkDefn
    val dDefn = dSymbol.mkDefn
    val qDefn = qSymbol.mkDefn
    val sDefns = sSymbols map { sSymbol =>
      sSymbol.mkDefn(ExprInt(signed, width, 0)) regularize loc
    }

    val pusRef = ExprSym(pusSymbol)
    val popRef = ExprSym(popSymbol)
    val setRef = ExprSym(setSymbol)
    val dRef = ExprSym(dSymbol)
    val qRef = ExprSym(qSymbol)
    val sRefs = sSymbols map ExprSym.apply

    val statements = List(
      StmtIf(
        pusRef & popRef,
        Nil,
        List(
          StmtIf(
            popRef,
            List from {
              (0 until depth) map { n =>
                StmtAssign(
                  sRefs(n),
                  if (n == depth - 1) ExprInt(signed, width, 0) else sRefs(n + 1)
                )
              }
            },
            List(
              StmtIf(
                pusRef,
                List from {
                  ((depth - 1) to 0 by -1) map { n =>
                    StmtAssign(sRefs(n), if (n == 0) dRef else sRefs(n - 1))
                  }
                },
                List(
                  StmtIf(setRef, List(StmtAssign(sRefs.head, dRef)), Nil)
                )
              )
            )
          )
        )
      )
    )

    val assign = EntAssign(qRef, sRefs.head)

    val decls = pusDecl :: popDecl :: setDecl :: dDecl :: qDecl :: sDecls
    val defns = (pusDefn :: popDefn :: setDefn :: dDefn :: qDefn :: sDefns) map EntSplice.apply

    val entitySymbol = Symbol(s"stack_${depth}_${kind.toName}", loc)
    entitySymbol.attr.compilerGenerated.set(true)
    val decl = DeclEntity(entitySymbol, decls) regularize loc
    val defn = DefnEntity(
      entitySymbol,
      EntityVariant.Fsm,
      EntCombProcess(statements) :: assign :: defns
    ) regularize loc
    (decl, defn)
  }

  def apply(kind: TypeFund, depth: Int): Symbol = synchronized {
    require(kind.isPacked)
    require(kind.width > 0)
    require(depth >= 2) // 1 deep stacks replaced in Replace1Stacks

    // TODO: Sometimes record types attached to trees are a bit out of date. Fix...
    val fixedKind = kind match {
      case k: TypeRecord => k.symbol.kind.asType.kind
      case other         => other
    }

    cache.getOrElseUpdate((fixedKind, depth), build(fixedKind, depth))._1.symbol
  }

}
