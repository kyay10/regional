// LANGUAGE: +ContextParameters

import io.github.kyay10.regional.Regional
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

// Library code
@RestrictsSuspension
class Region<out R> @PublishedApi internal constructor() {
  suspend inline fun <T> subregion(block: suspend context(Region<@Regional R>) () -> T): T =
    block.invoke(Region())

  companion object {
    fun <R> region(block: suspend context(Region<@Regional Any?>) () -> R) =
      block.startCoroutineUninterceptedOrReturn(Region(), Continuation(EmptyCoroutineContext) {}) as R
  }
}

fun <R> region(block: suspend context(Region<@Regional Any?>) () -> R): R =
  Region.region(block)

context(region: Region<R>)
suspend fun <R, T> subregion(block: suspend context(Region<@Regional R>) () -> T): T =
  region.subregion(block)

// Trusted Kernel

class STRef<S, in R> private constructor(private var state: S) {
  companion object {
    context(_: Region<R>)
    suspend fun <S, R> new(init: S): STRef<S, R> = STRef(init)
  }

  context(_: Region<R>)
  suspend fun get(): S = state

  context(_: Region<R>)
  suspend fun set(value: S) {
    state = value
  }
}

// User code
fun box(): String = region {
  val ref = STRef.new(0)
  ref.set(42)
  require(ref.get() == 42)
  val refDeclaredInside = subregion {
    val innerRef = STRef.new(0)
    innerRef.set(100)
    require(innerRef.get() == 100)
    require(ref.get() == 42)
    STRef.new<_, RegionRegion>("OK")
  }
  refDeclaredInside.get()
}