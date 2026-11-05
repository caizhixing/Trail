package io.github.caizhixing.trail.debug.export

import io.github.caizhixing.trail.debug.viewer.EventFilter
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 6.1: the three pure-domain scope selectors resolve to correct
 * `LogEvent` predicates; boundaries (N=0, future timestamps, nonexistent or
 * blank trace ids) yield empty selections without throwing.
 */
class ExportScopeTest {

    private fun event(
        ts: Long,
        traceId: String? = null,
        tag: String = "widget",
        name: String = "e$ts",
        audience: Audience = Audience.BOTH,
        level: Level = Level.INFO,
    ): LogEvent = LogEvent(
        ts = ts,
        traceId = traceId,
        tag = tag,
        name = name,
        audience = audience,
        level = level,
        attrs = emptyMap(),
        tid = null,
    )

    @Test
    fun recentHoursCoversTheInclusiveWindowOnly() {
        val scope = ExportScope.RecentHours(hours = 1)
        val predicate = scope.toPredicate(NOW)

        assertTrue("window start (now - 1h) is inclusive", predicate(event(NOW - HOUR_MILLIS)))
        assertTrue("window end (now) is inclusive", predicate(event(NOW)))
        assertFalse("older than the window is excluded", predicate(event(NOW - HOUR_MILLIS - 1)))
    }

    @Test
    fun recentHoursExcludesFutureTimestamps() {
        val predicate = ExportScope.RecentHours(hours = 1).toPredicate(NOW)

        assertFalse("a timestamp in the future is not 'recent'", predicate(event(NOW + 1)))
        assertFalse(predicate(event(Long.MAX_VALUE)))
    }

    @Test
    fun recentHoursZeroAndNegativeYieldAnEmptySelection() {
        val zero = ExportScope.RecentHours(hours = 0).toPredicate(NOW)
        val negative = ExportScope.RecentHours(hours = -2).toPredicate(NOW)

        assertFalse("N=0 stays an empty set even for ts == now", zero(event(NOW)))
        assertFalse(zero(event(0)))
        assertFalse(negative(event(NOW - HOUR_MILLIS)))
    }

    @Test
    fun traceScopeMatchesTheExactTraceIdOnly() {
        val predicate = ExportScope.Trace(traceId = "t_a").toPredicate(NOW)

        assertTrue(predicate(event(100, traceId = "t_a")))
        assertFalse("a different trace id is excluded", predicate(event(100, traceId = "t_b")))
        assertFalse("a null traceId is excluded", predicate(event(100, traceId = null)))
    }

    @Test
    fun unknownTraceIdYieldsAnEmptySelectionWithoutError() {
        val predicate = ExportScope.Trace(traceId = "t_missing").toPredicate(NOW)
        val sample = listOf(
            event(1, traceId = "t_a"),
            event(2, traceId = "t_b"),
            event(3, traceId = null),
        )

        sample.forEach { stored -> assertFalse(predicate(stored)) }
    }

    @Test
    fun blankTraceIdYieldsAnEmptySelection() {
        val predicate = ExportScope.Trace(traceId = "   ").toPredicate(NOW)

        assertFalse(predicate(event(100, traceId = "   ")))
        assertFalse(predicate(event(100, traceId = "t_a")))
    }

    @Test
    fun currentFilterDelegatesToTheViewerPredicate() {
        val filter = EventFilter(traceId = "t_a", levels = setOf(Level.WARN))
        val predicate = ExportScope.CurrentFilter(filter).toPredicate(NOW)

        assertTrue(predicate(event(100, traceId = "t_a", level = Level.WARN)))
        assertFalse("level dimension still applies", predicate(event(100, traceId = "t_a", level = Level.INFO)))
        assertFalse("trace dimension still applies", predicate(event(100, traceId = "t_b", level = Level.WARN)))

        val everything = ExportScope.CurrentFilter(EventFilter()).toPredicate(NOW)
        assertTrue("an empty filter matches any event", everything(event(1, traceId = null, level = Level.ERROR)))
    }

    @Test
    fun summariesAreDeterministicMetaFilterTexts() {
        assertEquals("recent_hours=1", ExportScope.RecentHours(1).summary(NOW))
        assertEquals("trace=t_a", ExportScope.Trace("t_a").summary(NOW))
        assertEquals("an empty filter summarizes as the scope name", "current_filter", ExportScope.CurrentFilter(EventFilter()).summary(NOW))
        assertEquals(
            "non-empty dimensions appear in a fixed order",
            "current_filter;audience=HUMAN|BOTH;tag=payment;level=INFO;trace=t_x;keyword=charge;time=[100..200]",
            ExportScope.CurrentFilter(
                EventFilter(
                    audiences = setOf(Audience.BOTH, Audience.HUMAN),
                    tags = setOf("payment"),
                    levels = setOf(Level.INFO),
                    traceId = "t_x",
                    keyword = "charge",
                    timeRange = io.github.caizhixing.trail.debug.viewer.TimeRange(from = 100, to = 200),
                ),
            ).summary(NOW),
        )
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        const val NOW = 1_799_484_096_123L
    }
}
