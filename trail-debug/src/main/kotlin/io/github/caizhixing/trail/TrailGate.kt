package io.github.caizhixing.trail

object TrailGate {
    // Constant gate for inline call sites: R8 treats the folded branch as dead
    // code in the noop artifact and keeps it here.
    const val ENABLED: Boolean = true
}
