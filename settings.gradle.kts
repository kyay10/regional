@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("convention-plugins")
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
  id("com.gradleup.nmcp.settings").version("1.4.4")
}

nmcpSettings {
  centralPortal {
    val sonatypeUsername: String? by settings
    val sonatypePassword: String? by settings
    username = sonatypeUsername
    password = sonatypePassword
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "regional"

include("compiler-plugin")
include("gradle-plugin")
include("plugin-annotations")
