package io.github.kyay10.regional.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildDelegatedConstructorCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class RegionGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
  @OptIn(SymbolInternals::class)
  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
    if (classSymbol.fir.origin == GeneratedRegionKey.origin) {
      return setOf(SpecialNames.INIT)
    }
    return emptySet()
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> = with(context) {
    val constructor = buildPrimaryConstructor {
      symbol = FirConstructorSymbol(owner.classId)
      source = owner.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
      resolvePhase = FirResolvePhase.BODY_RESOLVE
      moduleData = session.moduleData
      origin = GeneratedRegionKey.origin
      returnTypeRef = owner.defaultType().toFirResolvedTypeRef()
      status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
      delegatedConstructor = buildDelegatedConstructorCall {
        val singleSupertype = owner.resolvedSuperTypes.single()
        constructedTypeRef = singleSupertype.toFirResolvedTypeRef()
        val superSymbol =
          singleSupertype.toRegularClassSymbol(session) ?: error("Symbol for supertype $singleSupertype not found")
        val superConstructorSymbol = superSymbol.declaredMemberScope(session, memberRequiredPhase = null)
          .getDeclaredConstructors()
          .firstOrNull { it.valueParameterSymbols.isEmpty() }
          ?: error("No arguments constructor for class $singleSupertype not found")
        calleeReference = buildResolvedNamedReference {
          this.name = superConstructorSymbol.name
          resolvedSymbol = superConstructorSymbol
        }
        argumentList = FirEmptyArgumentList
        isThis = false
      }
    }
    constructor.containingClassForStaticMemberAttr = context.owner.toLookupTag()
    return listOf(constructor.symbol)
  }
}

