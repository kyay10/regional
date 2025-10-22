package io.github.kyay10.regional.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.EmptyDeprecationsProvider
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirLocalScopes
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirTowerDataContext
import org.jetbrains.kotlin.fir.declarations.FirTowerDataElement
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.builder.FirFunctionCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildVariableAssignment
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirFunctionCallRefinementExtension
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildImplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.FirDoubleColonExpressionResolver
import org.jetbrains.kotlin.fir.resolve.FirOuterClassManager
import org.jetbrains.kotlin.fir.resolve.FirSamResolver
import org.jetbrains.kotlin.fir.resolve.ImplicitValueStorage
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.FirCallResolver
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.stages.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.calls.stages.mapArguments
import org.jetbrains.kotlin.fir.resolve.dfa.FirDataFlowAnalyzer
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirSyntheticCallGenerator
import org.jetbrains.kotlin.fir.resolve.transformers.IntegerLiteralAndOperatorApproximationTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.ReturnTypeCalculator
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.impl.FirImplicitAnyTypeRef
import org.jetbrains.kotlin.fir.types.isAnyOrNullableAny
import org.jetbrains.kotlin.fir.types.isSomeFunctionType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

private val PACKAGE_FQN = FqName("io.github.kyay10.regional")
private val REGIONAL_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Regional"))
private val ASSERT_HAS_REGION = Name.identifier("assertHasRegion")
private val INSERT_CLASS_HERE = Name.special("<insert class here>")
private val INSERT_CASTS_HERE = Name.special("<insert casts here>")

data object GeneratedRegionKey : GeneratedDeclarationKey()

class RegionalAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session), SessionHolder {
  @OptIn(SymbolInternals::class)
  override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
    val lValue =
      (variableAssignment.lValue as? FirPropertyAccessExpression)?.calleeReference as? FirResolvedNamedReference
        ?: return null
    val rValue =
      (variableAssignment.rValue as? FirPropertyAccessExpression)?.calleeReference as? FirResolvedNamedReference
        ?: return null
    val anonFunction = (lValue.symbol as? FirAnonymousFunctionSymbol ?: return null).fir
    val regionClassSymbol = rValue.symbol as? FirRegularClassSymbol ?: return null
    return when (lValue.name) {
      INSERT_CLASS_HERE -> regionClassSymbol.fir.apply {
        val superTypes = buildList {
          for (param in anonFunction.valueParameters + anonFunction.contextParameters) {
            val paramType = param.returnTypeRef.coneType
            for (projection in paramType.typeArguments) {
              val subType = projection.type ?: continue
              if (subType.customAnnotations.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID }) add(subType)
            }
          }
          val receiverParam = anonFunction.receiverParameter ?: return@buildList
          val paramType = receiverParam.typeRef.coneType
          for (projection in paramType.typeArguments) {
            val subType = projection.type ?: continue
            if (subType.customAnnotations.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID }) add(subType)
          }
        }
        val superType = superTypes.firstOrNull { !it.isAnyOrNullableAny } ?: return@apply
        replaceSuperTypeRefs(superTypeRefs + buildResolvedTypeRef {
          coneType = superType
        })
      }

      INSERT_CASTS_HERE -> {
        val receiver = anonFunction.receiverParameter
        val assertCallForReceiver = if (receiver != null && receiver.typeRef.coneType.typeArguments.any {
            it.type?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
          }) {
          receiver.buildTypeAssertCall(regionClassSymbol)
        } else null
        (anonFunction.contextParameters + anonFunction.valueParameters)
          .filter { variable ->
            variable.returnTypeRef.coneType.typeArguments.any {
              it.type?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
            }
          }
          .fold(assertCallForReceiver) { acc, variable ->
            variable.buildTypeAssertCall(regionClassSymbol, acc)
          }
      }

      else -> null
    }
  }
}

@OptIn(FirExtensionApiInternals::class)
class RegionalFunctionTransformer(session: FirSession) : FirFunctionCallRefinementExtension(session), SessionHolder {
  private val fakeBodyResolveComponents = FakeBodyResolveComponents(session, ScopeSession())

  @OptIn(SymbolInternals::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    val mapping = fakeBodyResolveComponents.mapArguments(
      callInfo.argumentAtoms,
      symbol.fir,
      null,
      callInfo.origin == FirFunctionCallOrigin.Operator
    )
    for ((parameter, argument) in mapping.parameterToCallArgumentMap) {
      val paramType = parameter.returnTypeRef.coneTypeOrNull ?: continue
      if (paramType.isSomeFunctionType(session) && paramType.typeArguments.any {
          it.type?.typeArguments?.any { subType ->
            subType.type?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
          } == true
        }) {
        for (subArgument in argument.arguments) {
          val expression = subArgument.expression
          if (expression is FirAnonymousFunctionExpression) {
            val body = expression.anonymousFunction.body
            if (body == null || body.statements.isEmpty()) continue
            val regionName = (expression.anonymousFunction.label?.name?.titleCase().orEmpty()) + "Region"
            val regionClassId = ClassId(CallableId.PACKAGE_FQ_NAME_FOR_LOCAL, FqName(regionName), true)
            val regionClass = buildRegularClass {
              moduleData = session.moduleData
              resolvePhase = FirResolvePhase.BODY_RESOLVE
              origin = GeneratedRegionKey.origin
              status =
                FirDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT)
              deprecationsProvider = EmptyDeprecationsProvider
              classKind = ClassKind.CLASS
              scopeProvider = FirKotlinScopeProvider()
              superTypeRefs += FirImplicitAnyTypeRef(null)

              name = regionClassId.shortClassName
              this.symbol = FirRegularClassSymbol(regionClassId)
            }
            expression.anonymousFunction.replaceBody(buildBlock {
              statements.add(
                buildInsertPropertyAssignment(
                  INSERT_CLASS_HERE,
                  expression.anonymousFunction,
                  regionClass.symbol
                )
              )
              statements.add(
                buildInsertPropertyAssignment(
                  INSERT_CASTS_HERE,
                  expression.anonymousFunction,
                  regionClass.symbol
                )
              )
              statements.addAll(body.statements)
            })
          }
        }
      }
    }
    return null
  }

  override fun transform(
    call: FirFunctionCall,
    originalSymbol: FirNamedFunctionSymbol
  ): FirFunctionCall {
    error("should not reach here")
  }

  override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement {
    error("should not reach here")
  }

  override fun ownsSymbol(symbol: FirRegularClassSymbol) = false

  override fun restoreSymbol(
    call: FirFunctionCall,
    name: Name
  ): FirRegularClassSymbol? = null
}

private fun buildInsertPropertyAssignment(
  name: Name,
  function: FirAnonymousFunction,
  regionClassSymbol: FirRegularClassSymbol
) = buildVariableAssignment {
  source = function.source
  lValue = buildPropertyAccessExpression {
    source = function.source
    calleeReference = buildResolvedNamedReference {
      source = function.source
      this.name = name
      resolvedSymbol = function.symbol
    }
  }
  rValue = buildPropertyAccessExpression {
    source = function.source
    calleeReference = buildResolvedNamedReference {
      source = function.source
      this.name = name
      resolvedSymbol = regionClassSymbol
    }
  }
}

context(c: SessionHolder)
private fun FirReceiverParameter.buildTypeAssertCall(
  region: FirRegularClassSymbol,
): FirFunctionCall = buildTypeAssertCallBasic(region, typeRef.coneType) {
  argumentList = buildArgumentList {
    source = this@buildTypeAssertCall.source
    arguments.add(buildThisReceiverExpression {
      source = this@buildTypeAssertCall.source
      coneTypeOrNull = typeRef.coneType
      calleeReference = buildImplicitThisReference {
        boundSymbol = this@buildTypeAssertCall.symbol
      }
    })
  }
}

context(c: SessionHolder)
private fun FirVariable.buildTypeAssertCall(
  region: FirRegularClassSymbol,
  next: FirExpression? = null
): FirFunctionCall = buildTypeAssertCallBasic(region, returnTypeRef.coneType) {
  argumentList = buildArgumentList {
    source = this@buildTypeAssertCall.source
    arguments.add(buildPropertyAccessExpression {
      source = this@buildTypeAssertCall.source
      coneTypeOrNull = returnTypeRef.coneType
      calleeReference = buildResolvedNamedReference {
        source = this@buildTypeAssertCall.source
        name = this@buildTypeAssertCall.name
        resolvedSymbol = symbol
      }
    })
    if (next != null) arguments.add(next)
  }
}

context(c: SessionHolder)
private inline fun FirElement.buildTypeAssertCallBasic(
  region: FirRegularClassSymbol,
  type: ConeKotlinType,
  block: FirFunctionCallBuilder.() -> Unit
): FirFunctionCall = buildFunctionCall {
  source = this@buildTypeAssertCallBasic.source
  coneTypeOrNull = c.session.builtinTypes.unitType.coneType
  calleeReference = buildSimpleNamedReference {
    source = this@buildTypeAssertCallBasic.source
    name = ASSERT_HAS_REGION
  }
  explicitReceiver = PACKAGE_FQN.pathSegments().fold(null) { acc, name ->
    buildPropertyAccessExpression {
      source = this@buildTypeAssertCallBasic.source
      calleeReference = buildSimpleNamedReference {
        source = this@buildTypeAssertCallBasic.source
        this.name = name
      }
      explicitReceiver = acc
    }
  }
  typeArguments.add(buildTypeProjectionWithVariance {
    typeRef = buildResolvedTypeRef {
      val regionType = region.constructType()
      coneType = type.withArguments { arg ->
        val subType = arg.type ?: return@withArguments arg
        if (subType.customAnnotations.any { it.toAnnotationClassId(c.session) == REGIONAL_CLASS_ID }) {
          regionType
        } else {
          subType
        }
      }
    }
    variance = Variance.INVARIANT
  })
  block()
}

private fun String.titleCase() = replaceFirstChar { it.uppercaseChar() }

class FakeBodyResolveComponents(override val session: FirSession, override val scopeSession: ScopeSession) :
  BodyResolveComponents() {
  override val returnTypeCalculator: ReturnTypeCalculator
    get() = throw UnsupportedOperationException("Not implemented")
  override val implicitValueStorage: ImplicitValueStorage
    get() = throw UnsupportedOperationException("Not implemented")
  override val containingDeclarations: List<FirDeclaration>
    get() = throw UnsupportedOperationException("Not implemented")
  override val fileImportsScope: List<FirScope>
    get() = throw UnsupportedOperationException("Not implemented")
  override val towerDataElements: List<FirTowerDataElement>
    get() = throw UnsupportedOperationException("Not implemented")
  override val towerDataContext: FirTowerDataContext
    get() = throw UnsupportedOperationException("Not implemented")
  override val localScopes: FirLocalScopes
    get() = throw UnsupportedOperationException("Not implemented")
  override val noExpectedType: FirTypeRef
    get() = throw UnsupportedOperationException("Not implemented")
  override val symbolProvider: FirSymbolProvider
    get() = throw UnsupportedOperationException("Not implemented")
  override val file: FirFile
    get() = throw UnsupportedOperationException("Not implemented")
  override val container: FirDeclaration
    get() = throw UnsupportedOperationException("Not implemented")
  override val resolutionStageRunner: ResolutionStageRunner
    get() = throw UnsupportedOperationException("Not implemented")
  override val samResolver: FirSamResolver
    get() = throw UnsupportedOperationException("Not implemented")
  override val callResolver: FirCallResolver
    get() = throw UnsupportedOperationException("Not implemented")
  override val callCompleter: FirCallCompleter
    get() = throw UnsupportedOperationException("Not implemented")
  override val doubleColonExpressionResolver: FirDoubleColonExpressionResolver
    get() = throw UnsupportedOperationException("Not implemented")
  override val syntheticCallGenerator: FirSyntheticCallGenerator
    get() = throw UnsupportedOperationException("Not implemented")
  override val dataFlowAnalyzer: FirDataFlowAnalyzer
    get() = throw UnsupportedOperationException("Not implemented")
  override val outerClassManager: FirOuterClassManager
    get() = throw UnsupportedOperationException("Not implemented")
  override val integerLiteralAndOperatorApproximationTransformer: IntegerLiteralAndOperatorApproximationTransformer
    get() = throw UnsupportedOperationException("Not implemented")
  override val inlineFunction: FirFunction
    get() = throw UnsupportedOperationException("Not implemented")

}