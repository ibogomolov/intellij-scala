package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.types._
import nonvalue.Parameter
import quickfix.ReportHighlightingErrorQuickFix
import result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameters, ScParameter}
import com.intellij.psi.{PsiElement, PsiParameter, PsiNamedElement, PsiMethod}
import lang.psi.api.statements.{ScValue, ScFunction}
import codeInspection.varCouldBeValInspection.ValToVarQuickFix
import extensions._
import lang.psi.api.expr._
import lang.psi.impl.expr.ScInterpolatedStringPrefixReference

/**
 * Pavel.Fatin, 31.05.2010
 */
trait ApplicationAnnotator {
  def annotateReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    for {result <- reference.multiResolve(false)
         r = result.asInstanceOf[ScalaResolveResult]} {
      if (r.isAssignment) {
        annotateAssignmentReference(reference, holder)
      }
      if (!r.isApplicable) {
        r.element match {
          case f@(_: ScFunction | _: PsiMethod | _: ScSyntheticFunction) => {
            reference.getContext match {
              case genCall: ScGenericCall =>
                val missing = for (MissedTypeParameter(p) <- r.problems) yield p.name
                missing match {
                  case Seq() =>
                  case as =>
                    holder.createErrorAnnotation(genCall.typeArgs.getOrElse(genCall),
                      "Unspecified type parameters: " + as.mkString(", "))
                }
                r.problems.foreach {
                  case MissedTypeParameter(_) =>
                    // handled in bulk above
                  case DoesNotTakeTypeParameters =>
                    holder.createErrorAnnotation(genCall.typeArgs.getOrElse(genCall), f.name + " does not take type parameters")
                  case ExcessTypeArgument(arg) =>
                    holder.createErrorAnnotation(arg, "Too many type arguments for " + f.name)
                  case _ =>
                    //holder.createErrorAnnotation(call.argsElement, "Not applicable to " + signatureOf(f))
                }
              case call: MethodInvocation => {
                val missed =
                  for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText

                if (!missed.isEmpty)
                  holder.createErrorAnnotation(call.argsElement,
                    "Unspecified value parameters: " + missed.mkString(", "))

                r.problems.foreach {
                  case DoesNotTakeParameters() =>
                    holder.createErrorAnnotation(call.argsElement, f.name + " does not take parameters")
                  case ExcessArgument(argument) =>
                    if (argument != null) {
                      holder.createErrorAnnotation(argument, "Too many arguments for method " + nameOf(f))
                    } else {
                      //TODO investigate case when argument is null. It's possible when new Expression(ScType)
                    }
                  case TypeMismatch(expression, expectedType) =>
                    if (expression != null)
                      for (t <- expression.getType(TypingContext.empty)) {
                        //TODO show parameter name
                        val annotation = holder.createErrorAnnotation(expression,
                          "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
                        annotation.registerFix(ReportHighlightingErrorQuickFix)
                      }
                    else {
                      //TODO investigate case when expression is null. It's possible when new Expression(ScType)
                    }
                  case MissedValueParameter(_) => // simultaneously handled above
                  case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                  case MalformedDefinition() =>
                    holder.createErrorAnnotation(call.getInvokedExpr, f.name + " has malformed definition")
                  case ExpansionForNonRepeatedParameter(expression) =>
                    if (expression != null) {
                      holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
                    } else {
                      //TODO investigate case when expression is null. It's possible when new Expression(ScType)
                    }
                  case PositionalAfterNamedArgument(argument) =>
                    if (argument != null) {
                      holder.createErrorAnnotation(argument, "Positional after named argument")
                    } else {
                      //TODO investigate case when argument is null. It's possible when new Expression(ScType)
                    }
                  case ParameterSpecifiedMultipleTimes(assignment) =>
                    if (assignment != null) {
                      holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
                    } else {
                      //TODO investigate case when assignment is null. It's possible when new Expression(ScType)
                    }
                  case WrongTypeParameterInferred => //todo: ?
                  case ElementApplicabilityProblem(element, actual, expected) => 
                    holder.createErrorAnnotation(element, ScalaBundle.message("return.expression.does.not.conform", 
                      actual.presentableText, expected.presentableText)) 
                  case a => 
                    holder.createErrorAnnotation(call.argsElement, "Not applicable to " + signatureOf(f))
                }
              }
              case _ => {
                r.problems.foreach {
                  case MissedParametersClause(clause) if !reference.isInstanceOf[ScInterpolatedStringPrefixReference] =>
                    holder.createErrorAnnotation(reference, "Missing arguments for method " + nameOf(f))
                  case _ =>
                }
              }
            }
          }
          case _ =>
        }
      }
    }
  }

  /**
   * Annotates: val a = 1; a += 1;
   */
  private def annotateAssignmentReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    val qualifier = reference.getContext match {
      case x: ScMethodCall => x.getEffectiveInvokedExpr match {
        case x: ScReferenceExpression => x.qualifier
        case _ => None
      }
      case x: ScInfixExpr => Some(x.lOp)
      case _ => None
    }
    val refElementOpt = qualifier.flatMap(_.asOptionOf[ScReferenceElement])
    val ref: Option[PsiElement] = refElementOpt.flatMap(_.resolve().toOption)
    val reassignment = ref.exists(ScalaPsiUtil.isReadonly)
    if (reassignment) {
      val annotation = holder.createErrorAnnotation(reference, "Reassignment to val")
      ref.get match {
        case named: PsiNamedElement if ScalaPsiUtil.nameContext(named).isInstanceOf[ScValue] =>
          annotation.registerFix(new ValToVarQuickFix(ScalaPsiUtil.nameContext(named).asInstanceOf[ScValue]))
        case _ =>
      }
    }
  }

  def annotateMethodCall(call: ScMethodCall, holder: AnnotationHolder) {
    //do we need to check it:
    call.getEffectiveInvokedExpr match {
      case ref: ScReferenceElement =>
        ref.bind() match {
          case Some(r) if r.notCheckedResolveResult || r.isDynamic => //it's unhandled case
          case _ => return //it's definetely handled case
        }
      case _ => //unhandled case (only ref expressions was checked)
    }

    val problems = call.applicationProblems
    val missed = for (MissedValueParameter(p) <- problems) yield p.name + ": " + p.paramType.presentableText

    if(!missed.isEmpty)
      holder.createErrorAnnotation(call.argsElement, "Unspecified value parameters: " + missed.mkString(", "))

    //todo: better error explanation?
    //todo: duplicate
    problems.foreach {
      case DoesNotTakeParameters() =>
        holder.createErrorAnnotation(call.argsElement, "Application does not take parameters")
      case ExcessArgument(argument) =>
        holder.createErrorAnnotation(argument, "Too many arguments")
      case TypeMismatch(expression, expectedType) =>
        for(t <- expression.getType(TypingContext.empty)) {
          //TODO show parameter name
          val annotation = holder.createErrorAnnotation(expression,
            "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
        }
      case MissedValueParameter(_) => // simultaneously handled above
      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
      case MalformedDefinition() =>
        holder.createErrorAnnotation(call.getInvokedExpr, "Application has malformed definition")
      case ExpansionForNonRepeatedParameter(expression) =>
        holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
      case PositionalAfterNamedArgument(argument) =>
        holder.createErrorAnnotation(argument, "Positional after named argument")
      case ParameterSpecifiedMultipleTimes(assignment) =>
        holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")

      case _ => holder.createErrorAnnotation(call.argsElement, "Not applicable")
    }
  }
  
  private def nameOf(f: PsiNamedElement) = f.name + signatureOf(f)

  private def signatureOf(f: PsiNamedElement): String = f match {
    case f: ScFunction =>
      if (f.parameters.isEmpty) "" else formatParamClauses(f.paramClauses)
    case m: PsiMethod =>
      val params: Array[PsiParameter] = m.getParameterList.getParameters
      if (params.isEmpty) "" else formatJavaParams(params)
    case syn: ScSyntheticFunction =>
      if (syn.paramClauses.isEmpty) "" else syn.paramClauses.map(formatSyntheticParams).mkString
  }

  private def formatParamClauses(paramClauses: ScParameters) = {
    def formatParams(parameters: Seq[ScParameter], types: Seq[ScType]) = {
      val parts = parameters.zip(types).map {
        case (p, t) => t.presentableText + (if(p.isRepeatedParameter) "*" else "")
      }
      parenthesise(parts)
    }
    paramClauses.clauses.map(clause => formatParams(clause.parameters, clause.paramTypes)).mkString
  }

  private def formatJavaParams(parameters: Seq[PsiParameter]) = {
    val types = ScalaPsiUtil.getTypesStream(parameters)
    val parts = parameters.zip(types).map {
      case (p, t) => t.presentableText + (if(p.isVarArgs) "*" else "")
    }
    parenthesise(parts)
  }

  private def formatSyntheticParams(parameters: Seq[Parameter]) = {
    val parts = parameters.map {
      case p => p.paramType.presentableText + (if(p.isRepeated) "*" else "")
    }
    parenthesise(parts)
  }

  private def parenthesise(items: Seq[_]) = items.mkString("(", ", ", ")")
}
