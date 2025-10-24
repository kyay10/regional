package io.github.kyay10.regional

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Target(AnnotationTarget.TYPE)
public annotation class Regional

public val insertionPoint: Unit get() = Unit

@OptIn(ExperimentalContracts::class)
public fun <T> assertHasRegion(value: Any?, next: Unit = Unit) {
  contract {
    returns() implies (value is T)
  }
}
