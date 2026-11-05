package io.github.caizhixing.trail.debug.export

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 6.4: the frozen file name format `trail_export_<yyyyMMdd_HHmmss>.jsonl`
 * with an injected clock instant and zone.
 */
class ExportFileNamePolicyTest {

    @Test
    fun formatsTheFrozenStampForAnInjectedInstant() {
        assertEquals(
            "trail_export_20261105_123456.jsonl",
            ExportFileNamePolicy.fileName(utcMillis(2026, 11, 5, 12, 34, 56), UTC),
        )
    }

    @Test
    fun zeroPadsEverySingleDigitField() {
        assertEquals(
            "trail_export_20260105_030405.jsonl",
            ExportFileNamePolicy.fileName(utcMillis(2026, 1, 5, 3, 4, 5), UTC),
        )
    }

    @Test
    fun everyNameMatchesTheFrozenPattern() {
        assertTrue(
            ExportFileNamePolicy.fileName(utcMillis(2026, 11, 5, 12, 34, 56), UTC)
                .matches(PATTERN.toRegex()),
        )
    }

    @Test
    fun theZoneShiftsTheStampNotTheInstant() {
        val nowMs = utcMillis(2026, 11, 5, 20, 0, 0)

        assertEquals(
            "the same instant in UTC+8 falls on the next calendar day",
            "trail_export_20261106_040000.jsonl",
            ExportFileNamePolicy.fileName(nowMs, TimeZone.getTimeZone("Asia/Shanghai")),
        )
        assertEquals(
            "trail_export_20261105_200000.jsonl",
            ExportFileNamePolicy.fileName(nowMs, UTC),
        )
    }

    @Test
    fun namesDifferAcrossSeconds() {
        val second = utcMillis(2026, 11, 5, 12, 34, 56)

        assertEquals(
            "same-second exports collide by name (deterministic, last wins)",
            ExportFileNamePolicy.fileName(second, UTC),
            ExportFileNamePolicy.fileName(second + 999, UTC),
        )
        assertNotEquals(
            ExportFileNamePolicy.fileName(second, UTC),
            ExportFileNamePolicy.fileName(second + 1_000, UTC),
        )
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val PATTERN = "^trail_export_\\d{8}_\\d{6}\\.jsonl$"
    }
}
