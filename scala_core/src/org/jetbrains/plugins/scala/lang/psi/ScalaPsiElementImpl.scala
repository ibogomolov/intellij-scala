package org.jetbrains.plugins.scala.lang.psi

import _root_.com.intellij.extapi.psi.{StubBasedPsiElementBase, ASTWrapperPsiElement}
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, StubBasedPsiElement}

/**
  @author ven
*/
abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node) with ScalaPsiElement {
  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    getParent.getNode.replaceChild(node, newElement.getNode)
    newElement
  }
}

abstract class ScalaStubBasedElementImpl[T <: PsiElement](node: ASTNode)
extends StubBasedPsiElementBase[StubElement[T]](node) with ScalaPsiElement with StubBasedPsiElement[StubElement[T]] {
  override def getElementType() : IStubElementType[StubElement[T], T] =
    super.getElementType.asInstanceOf[IStubElementType[StubElement[T], T]]
}