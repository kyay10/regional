// RUN_PIPELINE_TILL: FRONTEND

package foo.bar

import io.github.kyay10.regional.Regional

class Region<R>

fun withRegion(block: Region<out @Regional Any?>.() -> Unit) {}

<!NOTHING_TO_INLINE!>inline<!> fun withRegion2() {
  withRegion {
    val x: Region<<!UNRESOLVED_REFERENCE!>WithRegionRegion<!>> = this
  }
}
