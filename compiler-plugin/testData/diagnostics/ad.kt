// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters

import io.github.kyay10.regional.Regional

class MultishotScope<out Region>

typealias NewScope<Region> = MultishotScope<@Regional Region>

context(_: MultishotScope<Region>)
public suspend fun <E, Region> handle(body: suspend context(NewScope<Region>)() -> E): E =
  error("unreachable")

interface AD<Region> {
  context(_: MultishotScope<Region>)
  suspend fun foo()
}

context(_: MultishotScope<Region>)
suspend fun <Region> backwards() {
  suspend fun foo() = handle {
    val res = object : AD<HandleRegion> {
      context(_: MultishotScope<HandleRegion>)
      override suspend fun foo() {
      }
    }
  }
}