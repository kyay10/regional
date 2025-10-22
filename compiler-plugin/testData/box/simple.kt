// LANGUAGE: +ContextParameters
package foo.bar

import io.github.kyay10.regional.Regional

data class Prompt<Region>(val name: String)
data class Scope<Region>(val name: String)

fun <R> withPrompt(block: context(Prompt<out @Regional Any?>) Scope<out @Regional Any?>.() -> R): R = block(Prompt<Any>("O"), Scope<Any>("K"))

context(p: Prompt<Region>, s: Scope<Region>)
fun <Region> usePromptInScope() = p.name + s.name

fun box(): String = withPrompt {
  usePromptInScope()
}
