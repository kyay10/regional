// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters

import io.github.kyay10.regional.Regional
import java.nio.file.Path
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.RestrictsSuspension
import kotlin.io.path.reader

// Library code
interface AutoCloseScope<in R> {
  fun onClose(action: () -> Unit)
}

context(scope: AutoCloseScope<R>)
fun <R, AC : AutoCloseable> AC.autoClose(): AC = also { scope.onClose(::close) }

class AutoCloseScopeImpl<in R> : AutoCloseScope<R>, AutoCloseable {
  private val closeActions = mutableListOf<() -> Unit>()

  override fun onClose(action: () -> Unit) {
    closeActions.add(action)
  }

  override fun close() {
    for (action in closeActions) {
      try {
        action()
      } catch (e: Exception) {
        // Handle exceptions from close actions if necessary
        e.printStackTrace()
      }
    }
    closeActions.clear()
  }
}

@RestrictsSuspension
class Region<out R> @PublishedApi internal constructor() {
  suspend inline fun <T> subregion(block: suspend context(Region<@Regional R>, AutoCloseScope<@Regional Nothing>) () -> T): T =
    AutoCloseScopeImpl<R>().use { block.invoke(Region(), it) }

  companion object {
    fun <R> region(block: suspend context(Region<@Regional Any?>, AutoCloseScope<@Regional Nothing>) () -> R): R =
      AutoCloseScopeImpl<Any?>().use {
        @Suppress("UNCHECKED_CAST")
        block as (Region<Any?>, AutoCloseScopeImpl<Any?>, Continuation<R>) -> R
        block(Region(), it, Continuation(EmptyCoroutineContext) {})
      }
  }
}

fun <R> region(block: suspend context(Region<@Regional Any?>, AutoCloseScope<@Regional Nothing>) () -> R): R =
  Region.region(block)

context(region: Region<R>)
suspend fun <R, T> subregion(block: suspend context(Region<@Regional R>, AutoCloseScope<@Regional Nothing>) () -> T): T =
  region.subregion(block)

// Trusted Kernel
class FileHandle<in R> private constructor(private val reader: java.io.Reader) {
  context(_: Region<R>)
  suspend fun read(): Char = reader.read().toChar()

  companion object {
    context(_: AutoCloseScope<R>)
    fun <R> Path.open(): FileHandle<R> = FileHandle(reader().autoClose())
  }
}

context(_: AutoCloseScope<R>)
fun <R> Path.open(): FileHandle<R> = with(FileHandle) { open() }

// User code

fun <T> example() = region {
  val file = Path.of("example.txt").open()
  println(file.read())
  val file2 = subregion {
    val subfile = Path.of("example2.txt").open()
    println(file.read())
    println(subfile.read())
    Path.of("example3.txt").open<RegionRegion>()
  }
  file2.read()
}