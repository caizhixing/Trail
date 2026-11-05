package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 5.2: six-dimension filter combination as a pure domain function
 * (audience x tag x level x traceId x keyword x time range; empty selection
 * = no restriction).
 */
class EventFilterTest {

    private fun event(
        ts: Long = 1_000L,
        tag: String = "widget",
        name: String = "rendered",
        audience: Audience = Audience.BOTH,
        level: Level = Level.INFO,
        traceId: String? = null,
        attrs: Map<String, String> = emptyMap(),
    ): LogEvent = LogEvent(
        ts = ts,
        traceId = traceId,
        tag = tag,
        name = name,
        audience = audience,
        level = level,
        attrs = attrs,
        tid = null,
    )

    @Test
    fun emptyFilterMatchesEverything() {
        val filter = EventFilter()

        assertTrue(filter.matches(event(audience = Audience.AI, level = Level.ERROR)))
        assertTrue(filter.matches(event(audience = Audience.HUMAN)))
        assertTrue(filter.matches(event(tag = "payment", name = "charged")))
    }

    @Test
    fun audienceDimensionRestrictsAndEmptySetDoesNot() {
        val filter = EventFilter(audiences = setOf(Audience.HUMAN, Audience.BOTH))

        assertTrue(filter.matches(event(audience = Audience.HUMAN)))
        assertTrue(filter.matches(event(audience = Audience.BOTH)))
        assertFalse(filter.matches(event(audience = Audience.AI)))
        assertTrue(EventFilter(audiences = emptySet()).matches(event(audience = Audience.AI)))
    }

    @Test
    fun tagDimensionUsesExactMembership() {
        val filter = EventFilter(tags = setOf("payment", "widget"))

        assertTrue(filter.matches(event(tag = "payment")))
        assertTrue(filter.matches(event(tag = "widget")))
        assertFalse(filter.matches(event(tag = "payment_extra")))
        assertFalse(filter.matches(event(tag = "network")))
    }

    @Test
    fun levelDimensionRestricts() {
        val filter = EventFilter(levels = setOf(Level.WARN, Level.ERROR))

        assertTrue(filter.matches(event(level = Level.WARN)))
        assertTrue(filter.matches(event(level = Level.ERROR)))
        assertFalse(filter.matches(event(level = Level.INFO)))
    }

    @Test
    fun traceIdDimensionMatchesExactValue() {
        val filter = EventFilter(traceId = "t_abc")

        assertTrue(filter.matches(event(traceId = "t_abc")))
        assertFalse(filter.matches(event(traceId = "t_other")))
        assertFalse("null-trace events never match a pinned trace", filter.matches(event(traceId = null)))
    }

    @Test
    fun keywordMatchesTagOrNameOrAttrValueCaseInsensitively() {
        val filter = EventFilter(keyword = "Order")

        assertTrue("tag hit", filter.matches(event(tag = "checkout_ORDER")))
        assertTrue("name hit", filter.matches(event(name = "order_submitted")))
        assertTrue("attr value hit", filter.matches(event(attrs = mapOf("screen" to "OrderList"))))
        assertFalse("attr keys are not searched", filter.matches(event(attrs = mapOf("order_id" to "xyz"))))
        assertFalse(filter.matches(event(tag = "cart", name = "cleared")))
    }

    @Test
    fun blankKeywordDisablesTheDimension() {
        assertTrue(EventFilter(keyword = null).matches(event()))
        assertTrue(EventFilter(keyword = "").matches(event()))
        assertTrue(EventFilter(keyword = "   ").matches(event()))
    }

    @Test
    fun timeRangeBoundsAreInclusive() {
        val filter = EventFilter(timeRange = TimeRange(from = 100L, to = 300L))

        assertTrue(filter.matches(event(ts = 100L)))
        assertTrue(filter.matches(event(ts = 200L)))
        assertTrue(filter.matches(event(ts = 300L)))
        assertFalse(filter.matches(event(ts = 99L)))
        assertFalse(filter.matches(event(ts = 301L)))
    }

    @Test
    fun openEndedTimeRangesRestrictOneSideOnly() {
        val fromOnly = EventFilter(timeRange = TimeRange(from = 200L))
        assertTrue(fromOnly.matches(event(ts = 200L)))
        assertFalse(fromOnly.matches(event(ts = 199L)))

        val toOnly = EventFilter(timeRange = TimeRange(to = 200L))
        assertTrue(toOnly.matches(event(ts = 200L)))
        assertFalse(toOnly.matches(event(ts = 201L)))

        assertTrue(TimeRange().isEmpty)
        assertFalse(TimeRange(from = 1L).isEmpty)
        assertFalse(TimeRange(to = 1L).isEmpty)
    }

    @Test
    fun allSixDimensionsIntersect() {
        val filter = EventFilter(
            audiences = setOf(Audience.HUMAN),
            tags = setOf("payment"),
            levels = setOf(Level.ERROR),
            traceId = "t_fix",
            keyword = "declined",
            timeRange = TimeRange(from = 100L, to = 300L),
        )

        val hit = event(
            ts = 200L,
            tag = "payment",
            name = "card_declined",
            audience = Audience.HUMAN,
            level = Level.ERROR,
            traceId = "t_fix",
            attrs = mapOf("reason" to "insufficient funds"),
        )
        assertTrue(filter.matches(hit))

        assertFalse("audience miss", filter.matches(hit.copy(audience = Audience.AI)))
        assertFalse("tag miss", filter.matches(hit.copy(tag = "widget")))
        assertFalse("level miss", filter.matches(hit.copy(level = Level.INFO)))
        assertFalse("trace miss", filter.matches(hit.copy(traceId = "t_other")))
        assertFalse("keyword miss", filter.matches(hit.copy(name = "card_cleared", attrs = mapOf("reason" to "ok"))))
        assertFalse("below range", filter.matches(hit.copy(ts = 99L)))
        assertFalse("above range", filter.matches(hit.copy(ts = 301L)))
    }
}
