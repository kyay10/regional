package io.github.kyay10.highkt

import io.github.kyay10.highkt.fir.SimpleClassGenerator
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class SimplePluginRegistrar : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::SimpleClassGenerator
  }
}
