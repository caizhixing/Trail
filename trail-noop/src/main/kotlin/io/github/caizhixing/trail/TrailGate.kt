package io.github.caizhixing.trail

object TrailGate {
    // Constant gate: every guarded branch folds to dead code at compile time,
    // so the production dex keeps zero Trail footprint (except the withTrace
    // shell, which cannot be constant-gated; see DECISIONS.md).
    const val ENABLED: Boolean = false
}
