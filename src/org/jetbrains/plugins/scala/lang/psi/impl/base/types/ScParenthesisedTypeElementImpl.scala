package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.result.{Success, TypingContext}
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis: " + getText

  protected def innerType(ctx: TypingContext) = {
    typeElement match {
      case Some(el) => el.getType(ctx)
      case None => Success(psi.types.Unit, Some(this))
    }
  }

    override def accept(visitor: ScalaElementVisitor) {
        visitor.visitParenthesisedTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitParenthesisedTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}