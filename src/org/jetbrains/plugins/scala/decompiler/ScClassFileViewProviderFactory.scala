package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{SingleRootFileViewProvider, PsiManager, FileViewProviderFactory}
import com.intellij.openapi.fileTypes.StdFileTypes
import java.io.IOException

/**
 * @author ilyas
 */

class ScClassFileViewProviderFactory extends FileViewProviderFactory {
  def createFileViewProvider(file: VirtualFile, language: Language, manager: PsiManager, physical: Boolean) = {
    if (file.getFileType == StdFileTypes.CLASS && ScClsStubBuilderFactory.canBeProcessed(file)) {
      new ScClassFileViewProvider(manager, file, physical, DecompilerUtil.isScalaFile(file))
    } else {
      new SingleRootFileViewProvider(manager, file, physical)
    }
  }
}