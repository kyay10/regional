// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters

package foo.bar

import io.github.kyay10.regional.Regional
import kotlin.coroutines.*

@RestrictsSuspension
class MultishotScope<out Region>
class Prompt<R, in IR : OR, OR>
public typealias Choose<IR, OR> = Prompt<Unit, IR, OR>

context(scope: MultishotScope<Region>)
public suspend inline fun <R, Region> newReset(noinline body: suspend context(MultishotScope<@Regional Region>) Prompt<R, @Regional Nothing, Region>.() -> R): R {
  error("unreachable")
}

typealias RevState<S, R, IR, OR> = Prompt<Pair<suspend context(MultishotScope<OR>) () -> S, R>, IR, OR>

context(_: MultishotScope<Region>)
suspend fun <S, R, Region> runRevState(
  value: S,
  body: suspend context(MultishotScope<@Regional Region>) RevState<S, R, @Regional Nothing, Region>.() -> R
): Pair<suspend context(MultishotScope<Region>) () -> S, R> = newReset {
  val f: suspend context(MultishotScope<Region>) () -> S = { value }
  f to body()
}

data class CounterState(val count: Int)

context(_: MultishotScope<IR>)
suspend fun <IR> RevState<CounterState, Unit, IR, *>.incrementCounter() {
}

context(_: MultishotScope<IR>)
suspend fun <IR> RevState<CounterState, Unit, IR, *>.doubleCounter() {
}

suspend fun MultishotScope<Any?>.test() {
  runRevState(CounterState(0)) {
    doubleCounter()
    doubleCounter()
    incrementCounter()
  }.first()
}