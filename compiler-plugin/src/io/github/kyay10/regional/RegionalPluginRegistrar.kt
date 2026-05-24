package io.github.kyay10.regional

import io.github.kyay10.regional.fir.RegionGenerator
import io.github.kyay10.regional.fir.RegionalAssignAlterer
import io.github.kyay10.regional.fir.RegionalFunctionTransformer
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class RegionalPluginRegistrar : FirExtensionRegistrar() {
  @OptIn(FirExtensionApiInternals::class)
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::RegionalAssignAlterer
    +::RegionalFunctionTransformer
    +::RegionGenerator
  }
}
