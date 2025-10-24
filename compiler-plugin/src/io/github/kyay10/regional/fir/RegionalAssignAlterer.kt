package io.github.kyay10.regional.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.isObject
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.declarations.EmptyDeprecationsProvider
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirLocalScopes
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirTowerDataContext
import org.jetbrains.kotlin.fir.declarations.FirTowerDataElement
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.declarations.builder.buildOuterClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotationWithClassId
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.declarations.utils.nameOrSpecialName
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.FirFunctionCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildVariableAssignment
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
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
import org.jetbrains.kotlin.fir.resolve.calls.ConeSimpleLeafResolutionAtom
import org.jetbrains.kotlin.fir.resolve.calls.FirCallResolver
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.calls.candidate.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.candidate.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.candidate.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.stages.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.calls.stages.mapArguments
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.dfa.FirDataFlowAnalyzer
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.FirSyntheticCallGenerator
import org.jetbrains.kotlin.fir.resolve.transformers.IntegerLiteralAndOperatorApproximationTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.ReturnTypeCalculator
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeCapturedType
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeIntegerConstantOperatorType
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralConstantType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeStubType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.ConeTypeVariableType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.ProjectionKind
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.impl.FirImplicitAnyTypeRef
import org.jetbrains.kotlin.fir.types.isAnyOrNullableAny
import org.jetbrains.kotlin.fir.types.isSomeFunctionType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance

private val PACKAGE_FQN = FqName("io.github.kyay10.regional")
private val REGIONAL_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Regional"))
private val ASSERT_HAS_REGION = Name.identifier("assertHasRegion")
private val INSERTION_POINT = Name.identifier("insertionPoint")
private val INSERT_CLASS_HERE = Name.special("<insert class here>")
private val INSERT_CASTS_HERE = Name.special("<insert casts here>")

data object GeneratedRegionKey : GeneratedDeclarationKey()
object ClassAnchorElementKey : FirDeclarationDataKey()

var FirClass.anchor: KtSourceElement? by FirDeclarationDataRegistry.data(ClassAnchorElementKey)
val FirRegularClassSymbol.anchor: KtSourceElement? by FirDeclarationDataRegistry.symbolAccessor(ClassAnchorElementKey)

class RegionalAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session), SessionHolder {
  private fun MutableList<ConeKotlinType>.addRegionalUpperTypesFrom(paramType: ConeKotlinType) {
    val paramClassSymbol = paramType.toRegularClassSymbol() ?: return
    paramType.typeArguments.zip(paramClassSymbol.typeParameterSymbols).forEach { (projection, paramSymbol) ->
      if (projection.kind == ProjectionKind.IN) return@forEach
      if (paramSymbol.variance == Variance.IN_VARIANCE) return@forEach
      val subType = projection.type?.fullyExpandedType() ?: return@forEach
      if (subType !is ConeClassLikeType) return@forEach
      val symbol = subType.toRegularClassSymbol() ?: return@forEach
      if (!symbol.isAbstract) return@forEach
      if (symbol.origin != GeneratedRegionKey.origin) return@forEach
      if (subType.customAnnotations.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID }) add(subType)
    }
  }

  @OptIn(SymbolInternals::class)
  override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
    val lValue =
      ((variableAssignment.rValue as? FirPropertyAccessExpression)?.explicitReceiver as? FirPropertyAccessExpression)?.calleeReference as? FirResolvedNamedReference
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
            addRegionalUpperTypesFrom(param.returnTypeRef.coneType.fullyExpandedType())
          }
          val receiverParam = anonFunction.receiverParameter ?: return@buildList
          addRegionalUpperTypesFrom(receiverParam.typeRef.coneType.fullyExpandedType())
        }
        val superType = superTypes.firstOrNull { !it.isAnyOrNullableAny } ?: return@apply
        // TODO add tests for this branch
        replaceSuperTypeRefs(listOf(buildResolvedTypeRef {
          source = regionClassSymbol.source
          coneType = superType
        }))
      }

      INSERT_CASTS_HERE -> {
        val receiver = anonFunction.receiverParameter
        val assertCallForReceiver =
          if (receiver != null && receiver.typeRef.coneType.fullyExpandedType().typeArguments.any {
              it.type?.fullyExpandedType()?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
            }) {
            receiver.buildTypeAssertCall(regionClassSymbol)
          } else null
        (anonFunction.contextParameters + anonFunction.valueParameters)
          .filter { variable ->
            variable.returnTypeRef.coneType.fullyExpandedType().typeArguments.any {
              it.type?.fullyExpandedType()?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
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

  private val FirBasedSymbol<*>.isObject: Boolean
    get() = this is FirAnonymousObjectSymbol || this is FirRegularClassSymbol && classKind.isObject

  private fun List<FirDeclaration>.isInsideInlineContext(): Boolean {
    for (it in asReversed()) {
      when {
        it is FirFunction && it.isInline -> return true
        it.symbol.isObject -> return false
      }
    }
    return false
  }

  @OptIn(SymbolInternals::class)
  override fun intercept(
    callInfo: CallInfo,
    symbol: FirNamedFunctionSymbol
  ): CallReturnType? {
    if (callInfo.containingDeclarations.isInsideInlineContext()) return null
    val mapping = fakeBodyResolveComponents.mapArguments(
      callInfo.argumentAtoms,
      symbol.fir,
      null,
      callInfo.origin == FirFunctionCallOrigin.Operator
    )
    val capturedTypeParams = callInfo.containingDeclarations.capturedTypeParams()
    val usedNames = mutableSetOf<String>()
    for ((parameter, argument) in mapping.parameterToCallArgumentMap) {
      val paramType = parameter.returnTypeRef.coneTypeOrNull?.fullyExpandedType() ?: continue
      if (paramType.isSomeFunctionType(session) && paramType.typeArguments.any {
          it.type?.fullyExpandedType()?.typeArguments?.any { subType ->
            subType.type?.fullyExpandedType()?.customAnnotations?.any { it.toAnnotationClassId(session) == REGIONAL_CLASS_ID } == true
          } == true
        }) {
        for (subArgument in argument.arguments) {
          val expression = subArgument.expression
          if (expression is FirAnonymousFunctionExpression) {
            val body = expression.anonymousFunction.body
            if (body == null || body.statements.isEmpty()) continue
            val firstStatement = body.statements.first()
            if (firstStatement is FirClass && firstStatement.origin == GeneratedRegionKey.origin) continue
            if (firstStatement is FirVariableAssignment && (firstStatement.rValue as? FirPropertyAccessExpression)?.calleeReference?.name in
              setOf(INSERT_CLASS_HERE, INSERT_CASTS_HERE)
            ) continue
            var regionName = (expression.anonymousFunction.label?.name?.titleCase().orEmpty()) + "Region"
            if (regionName in usedNames) {
              regionName += usedNames.size
            }
            usedNames.add(regionName)
            val regionClassId = ClassId(CallableId.PACKAGE_FQ_NAME_FOR_LOCAL, FqName(regionName), true)
            val regionClass = buildRegularClass {
              // Gives it the same source as the label would in return@someLambda or this@someLambda
              source = expression.anonymousFunction.label?.source ?: expression.source?.fakeElement(
                KtFakeSourceElementKind.GeneratedLambdaLabel
              )
              moduleData = session.moduleData
              resolvePhase = FirResolvePhase.BODY_RESOLVE
              origin = GeneratedRegionKey.origin
              status = FirDeclarationStatusImpl(Visibilities.Local, Modality.ABSTRACT)
              deprecationsProvider = EmptyDeprecationsProvider
              classKind = ClassKind.CLASS
              scopeProvider = FirKotlinScopeProvider()
              superTypeRefs += FirImplicitAnyTypeRef(null)
              typeParameters.addAll(capturedTypeParams)

              name = regionClassId.shortClassName
              this.symbol = FirRegularClassSymbol(regionClassId)
            }
            regionClass.anchor = callInfo.callSite.source
            expression.anonymousFunction.replaceBody(buildBlock {
              source = body.source
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

  override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement = symbol.anchor!!

  override fun ownsSymbol(symbol: FirRegularClassSymbol) = symbol.anchor != null

  override fun restoreSymbol(
    call: FirFunctionCall,
    name: Name
  ): FirRegularClassSymbol? {
    for (argument in call.argumentList.arguments) {
      val expression = argument as? FirAnonymousFunctionExpression ?: continue
      val body = expression.anonymousFunction.body ?: continue
      if (body.statements.isEmpty()) continue
      val firstStatement = body.statements.first()
      if (firstStatement is FirRegularClass && firstStatement.origin == GeneratedRegionKey.origin && firstStatement.nameOrSpecialName == name) {
        return firstStatement.symbol
      }
    }
    return null
  }
}

// TODO test this better
private fun List<FirDeclaration>.capturedTypeParams() = buildList {
  for (declaration in this@capturedTypeParams.asReversed()) {
    if (declaration is FirTypeParameterRefsOwner) addAll(declaration.typeParameters.map {
      buildOuterClassTypeParameterRef {
        symbol = it.symbol
      }
    })
    // inner/local classes already have captured type params handled, while functions don't
    if (declaration !is FirFunction) break
  }
}

class RegionalFixSmartCastInReceivers(session: FirSession) : FirExpressionResolutionExtension(session), SessionHolder {
  override fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirCallableSymbol<*>
  ) = addNewImplicitReceivers(functionCall, sessionHolder, containingCallableSymbol as FirBasedSymbol<*>)

  @OptIn(UnresolvedExpressionTypeAccess::class, Candidate.UpdatingCandidateInvariants::class)
  fun addNewImplicitReceivers(
    functionCall: FirFunctionCall,
    sessionHolder: SessionAndScopeSessionHolder,
    containingCallableSymbol: FirBasedSymbol<*>
  ): List<ImplicitExtensionReceiverValue> {
    if ((functionCall.calleeReference.symbol as? FirFunctionSymbol<*>)?.isSuspend != true) return emptyList()
    functionCall.dispatchReceiver?.let { dispatchReceiver ->
      if (dispatchReceiver is FirSmartCastExpression && dispatchReceiver.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
        functionCall.replaceDispatchReceiver(dispatchReceiver.originalExpression)
        //dispatchReceiver.originalExpression.replaceConeTypeOrNull(dispatchReceiver.coneTypeOrNull?.deintersect())
      }
    }
    functionCall.extensionReceiver?.let { extensionReceiver ->
      if (extensionReceiver is FirSmartCastExpression && extensionReceiver.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
        functionCall.replaceExtensionReceiver(extensionReceiver.originalExpression)
        //extensionReceiver.originalExpression.replaceConeTypeOrNull(extensionReceiver.coneTypeOrNull?.deintersect())
      }
    }
    var changedAnyContext = false
    val newContextArguments = functionCall.contextArguments.map {
      if (it is FirSmartCastExpression && it.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
        changedAnyContext = true
        //it.originalExpression.replaceConeTypeOrNull(it.coneTypeOrNull?.deintersect())
        it.originalExpression
      } else {
        it
      }
    }
    if (changedAnyContext) {
      functionCall.replaceContextArguments(newContextArguments)
    }
    (functionCall.calleeReference as? FirNamedReferenceWithCandidate)?.candidate?.let { candidate ->
      var changedAnyContext = false
      val newContextArguments = candidate.contextArguments?.map {
        val expr = (it as? ConeSimpleLeafResolutionAtom ?: return@map it).expression
        if (expr is FirSmartCastExpression && expr.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
          changedAnyContext = true
          //expr.originalExpression.replaceConeTypeOrNull(expr.coneTypeOrNull?.deintersect())
          ConeSimpleLeafResolutionAtom(expr.originalExpression, true)
        } else {
          it
        }
      }
      if (changedAnyContext) {
        candidate.contextArguments = newContextArguments
      }
      var changedAnyArguments = false
      val newArguments = candidate.arguments.map {
        val expr = (it as? ConeSimpleLeafResolutionAtom ?: return@map it).expression
        if (expr is FirSmartCastExpression && expr.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
          changedAnyArguments = true
          //expr.originalExpression.replaceConeTypeOrNull(expr.coneTypeOrNull?.deintersect())
          ConeSimpleLeafResolutionAtom(expr.originalExpression, true)
        } else {
          it
        }
      }
      if (changedAnyArguments) {
        candidate.replaceArgumentPrefix(newArguments)
      }
    }
    val argumentList = functionCall.argumentList as? FirResolvedArgumentList ?: return emptyList()
    var changedAnyArguments = false
    val newArgumentList = buildResolvedArgumentList(
      argumentList.originalArgumentList,
      linkedMapOf<FirExpression, FirValueParameter>().apply {
        argumentList.mapping.forEach { (expression, param) ->
          if (expression is FirSmartCastExpression && expression.originalExpression.resolvedType.isRestrictSuspensionReceiver()) {
            changedAnyArguments = true
            //expression.originalExpression.replaceConeTypeOrNull(expression.coneTypeOrNull?.deintersect())
            put(expression.originalExpression, param)
          } else {
            put(expression, param)
          }
        }
      }
    )
    if (changedAnyArguments) {
      functionCall.replaceArgumentList(newArgumentList)
    }

    return emptyList()
  }

  private fun ConeKotlinType.isRestrictSuspensionReceiver(): Boolean {
    when (this) {
      is ConeClassLikeType -> {
        val regularClassSymbol = fullyExpandedType().lookupTag.toRegularClassSymbol() ?: return false
        if (regularClassSymbol.hasAnnotationWithClassId(StandardClassIds.Annotations.RestrictsSuspension, session)) {
          return true
        }
        return regularClassSymbol.resolvedSuperTypes.any { it.isRestrictSuspensionReceiver() }
      }

      is ConeTypeParameterType -> {
        return lookupTag.typeParameterSymbol.resolvedBounds.any { it.coneType.isRestrictSuspensionReceiver() }
      }

      else -> return false
    }
  }
}

private tailrec fun ConeKotlinType.deintersect(): ConeKotlinType = when (this) {
  is ConeClassLikeType, is ConeTypeParameterType, is ConeCapturedType, is ConeStubType, is ConeTypeVariableType,
  is ConeIntegerConstantOperatorType, is ConeIntegerLiteralConstantType, is ConeLookupTagBasedType -> this

  is ConeFlexibleType -> upperBound
  is ConeDefinitelyNotNullType -> original.deintersect()
  is ConeIntersectionType -> intersectedTypes.first()
}

private fun buildInsertPropertyAssignment(
  insertionPointName: Name,
  function: FirAnonymousFunction,
  regionClassSymbol: FirRegularClassSymbol
) = buildVariableAssignment {
  source = function.source
  lValue = buildPropertyAccessExpression {
    source = function.source
    explicitReceiver = PACKAGE_FQN.pathSegments().fold(null) { acc, name ->
      buildPropertyAccessExpression {
        source = function.source
        calleeReference = buildSimpleNamedReference {
          source = function.source
          this.name = name
        }
        explicitReceiver = acc
      }
    }
    calleeReference = buildSimpleNamedReference {
      source = function.source
      name = INSERTION_POINT
    }
  }
  rValue = buildPropertyAccessExpression {
    source = function.source
    explicitReceiver = buildPropertyAccessExpression {
      source = function.source
      calleeReference = buildResolvedNamedReference {
        source = function.source
        this.name = insertionPointName
        resolvedSymbol = function.symbol
      }
    }
    calleeReference = buildResolvedNamedReference {
      source = function.source
      this.name = insertionPointName
      resolvedSymbol = regionClassSymbol
    }
  }
}

context(c: SessionHolder)
private fun FirReceiverParameter.buildTypeAssertCall(
  region: FirRegularClassSymbol,
): FirFunctionCall = buildTypeAssertCallBasic(region, typeRef.coneType.fullyExpandedType()) {
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
): FirFunctionCall = buildTypeAssertCallBasic(region, returnTypeRef.coneType.fullyExpandedType()) {
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
      source = this@buildTypeAssertCallBasic.source
      val regionType = region.constructType(region.typeParameterSymbols.map { it.defaultType }.toTypedArray())
      coneType = type.withArguments { arg ->
        val subType = arg.type ?: return@withArguments arg
        if (subType.fullyExpandedType().customAnnotations.any { it.toAnnotationClassId(c.session) == REGIONAL_CLASS_ID }) {
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