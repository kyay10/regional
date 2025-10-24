// RUN_PIPELINE_TILL: BACKEND

package foo.bar

import kotlin.coroutines.*
fun main() {
  withFoo {
    this <!UNCHECKED_CAST!>as Foo<Int><!>
    useIntFoo()
  }
}

suspend fun Foo<Int>.useIntFoo() {}

fun withFoo(block: suspend Foo<*>.() -> Unit) {}

@RestrictsSuspension
class Foo<T>