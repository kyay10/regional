package io.github.kyay10.regional.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
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

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    return listOf(
      createConstructor(
        context.owner,
        GeneratedRegionKey,
        isPrimary = true,
        generateDelegatedNoArgConstructorCall = true
      ).symbol
    )
  }
}