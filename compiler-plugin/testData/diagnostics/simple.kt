// RUN_PIPELINE_TILL: BACKEND

package foo.bar

import io.github.kyay10.regional.Regional

class Region<R>

fun withRegion(block: Region<out @Regional Any?>.() -> Unit) {}

fun test() {
  withRegion {
    val x: Region<WithRegionRegion> = this
  }
}
