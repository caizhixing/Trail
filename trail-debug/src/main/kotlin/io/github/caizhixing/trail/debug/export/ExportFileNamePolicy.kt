package io.github.caizhixing.trail.debug.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Frozen export file name (PRD §2.5, behavior 6.4):
 * `trail_export_<yyyyMMdd_HHmmss>.jsonl`. The clock instant is injected; the
 * zone defaults to the device-local zone so the stamp reads naturally in the
 * share sheet, and tests pass a fixed zone for deterministic assertions.
 */
internal object ExportFileNamePolicy {

    fun fileName(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat(STAMP_PATTERN, Locale.US)
        format.timeZone = zone
        return PREFIX + format.format(Date(nowMs)) + SUFFIX
    }

    private const val PREFIX = "trail_export_"
    private const val STAMP_PATTERN = "yyyyMMdd_HHmmss"
    private const val SUFFIX = ".jsonl"
}
