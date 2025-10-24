// RUN_PIPELINE_TILL: BACKEND

package foo.bar

import io.github.kyay10.regional.Regional

class Region<R>

typealias NewScope<R> = Region<out @Regional R>

typealias BlockType = NewScope<Any?>.() -> Unit

fun withRegion(block: BlockType) {}

fun test() {
  withRegion {
    val x: Region<WithRegionRegion> = this@withRegion
  }
}
