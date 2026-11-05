package io.github.caizhixing.trail.debug.engine

/**
 * Meta payload of the export header line (schema v1, PRD §2.5).
 * Pure data; clock/device/app providers are injected by the caller.
 */
internal data class ExportMeta(
    val schema: Int,
    val app: String,
    val version: String,
    val device: String,
    val exportedAt: String,
    val filter: String,
) {
    internal companion object {
        internal const val UNKNOWN_DEVICE = "unknown"

        /**
         * Behavior 3.5: a device provider that throws, returns null or blank
         * collapses to "unknown" instead of failing the export.
         */
        internal fun deviceOrUnknown(provider: () -> String?): String {
            val raw = runCatching { provider() }.getOrNull()
            return if (raw.isNullOrBlank()) UNKNOWN_DEVICE else raw
        }
    }
}
