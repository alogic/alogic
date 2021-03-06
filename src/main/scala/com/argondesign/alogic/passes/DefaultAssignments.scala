////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2021 Argon Design Ltd. All rights reserved.
// This file is covered by the BSD (with attribution) license.
// See the LICENSE file for the precise wording of the license.
//
// DESCRIPTION:
// Any symbol driven combinationally through the FSM logic must be assigned a
// value on all code paths to avoid latches. In this phase we add all such
// default assignments.
////////////////////////////////////////////////////////////////////////////////

package com.argondesign.alogic.passes

import com.argondesign.alogic.analysis.Liveness
import com.argondesign.alogic.analysis.WrittenSymbols
import com.argondesign.alogic.ast.Trees._
import com.argondesign.alogic.core.CompilerContext
import com.argondesign.alogic.core.Symbol
import com.argondesign.alogic.core.TypeAssigner
import com.argondesign.alogic.util.BigIntOps._

import scala.collection.mutable

object DefaultAssignments extends PairTransformerPass {
  val name = "default-assignments"

  override def skip(decl: Decl, defn: Defn)(implicit cc: CompilerContext): Boolean = defn match {
    case d: DefnEntity => d.combProcesses.isEmpty
    case _             => true
  }

  override def transform(decl: Decl, defn: Defn)(implicit cc: CompilerContext): (Tree, Tree) = {

    val entityDefn = defn.asInstanceOf[DefnEntity]

    val needsDefault = mutable.Set[Symbol]()

    decl.asInstanceOf[DeclEntity].decls.iterator foreach {
      case DeclVar(symbol, _) if !symbol.attr.flop.isSet       => needsDefault += symbol
      case DeclOut(symbol, _, _, _) if !symbol.attr.flop.isSet => needsDefault += symbol
      case _                                                   =>
    }

    if (needsDefault.isEmpty) {
      (decl, defn)
    } else {

      // Remove any nets driven through an assign
      for (EntAssign(lhs, _) <- entityDefn.assigns) {
        lhs.visit {
          case ExprSym(symbol) => needsDefault remove symbol
        }
      }

      // Remove any nets written in a clocked process
      for {
        EntClockedProcess(_, _, stmts) <- entityDefn.clockedProcesses
        stmt <- stmts
      } {
        stmt visit {
          case StmtAssign(lhs, _)     => WrittenSymbols(lhs) foreach needsDefault.remove
          case StmtOutcall(out, _, _) => WrittenSymbols(out) foreach needsDefault.remove
        }
      }

      // Remove symbols that are dead at the beginning of the cycle.
      val (liveSymbolBits, deadSymbolBits) = Liveness(entityDefn.combProcesses.head.stmts)

      if (needsDefault.nonEmpty) {
        assert(entityDefn.combProcesses.lengthIs == 1)

        // Keep only the symbols with all bits dead
        val deadSymbols = Set from {
          deadSymbolBits collect {
            case (symbol, bits) if bits == BigInt.mask(symbol.kind.width.toInt) => symbol
          }
        }

        // Now retain only the symbols that are not dead
        needsDefault filterInPlace { symbol =>
          !(deadSymbols contains symbol)
        }
      }

      // If a symbol is live or we use it's _q, initialize to its default value
      // otherwise zero.
      val initializeToDefault = Set from {
        def collect(tree: Tree): Iterator[Symbol] = tree flatCollect {
          case StmtDelayed(_: ExprSym, rhs) => collect(rhs)
          case ExprSym(symbol)              => symbol.attr.flop.get.iterator
        }
        collect(entityDefn)
      } union liveSymbolBits.keySet

      val newBody = entityDefn.body flatMap {
        case ent @ EntCombProcess(stmts) =>
          Some {
            // Add default assignments
            val leading = for {
              Defn(symbol) <- entityDefn.defns
              if needsDefault contains symbol
            } yield {
              val init = if ((initializeToDefault contains symbol) && symbol.attr.default.isSet) {
                symbol.attr.default.value
              } else {
                val kind = symbol.kind
                ExprInt(kind.isSigned, kind.width.toInt, 0)
              }
              StmtAssign(ExprSym(symbol), init) regularize symbol.loc
            }
            TypeAssigner(EntCombProcess(leading ::: stmts) withLoc ent.loc)
          }
        case other => Some(other)
      }

      val newDefn = TypeAssigner(entityDefn.copy(body = newBody) withLoc entityDefn.loc)

      (decl, newDefn)
    }
  }

}
