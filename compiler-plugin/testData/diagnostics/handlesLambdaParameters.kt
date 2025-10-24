// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters

package foo.bar

import io.github.kyay10.regional.Regional
import kotlin.coroutines.*

@RestrictsSuspension
class MultishotScope<out Region>
class Prompt<R, in IR: OR, OR>
public typealias Choose<IR, OR> = Prompt<Unit, IR, OR>
context(scope: MultishotScope<Region>)
public suspend inline fun <R, Region> newReset(noinline body: suspend context(MultishotScope<@Regional Region>) Prompt<R, @Regional Nothing, Region>.() -> R): R {
  error("unreachable")
}

context(_: MultishotScope<Region>)
public suspend fun <R, Region> runChoice(
  body: suspend context(Choose<@Regional Nothing, Region>, MultishotScope<@Regional Region>) () -> R,
  handler: suspend context(MultishotScope<Region>) (R) -> Unit
): Unit = newReset {
  handler(body())
}