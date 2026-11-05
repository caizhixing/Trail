package io.github.caizhixing.trail.debug.viewer

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent

/**
 * Inclusive `[from, to]` event-time window; `null` bounds are unbounded.
 */
data class TimeRange(
    val from: Long? = null,
    val to: Long? = null,
) {
    val isEmpty: Boolean
        get() = from == null && to == null
}

/**
 * Pure six-dimension viewer predicate (behavior 5.2): audience x tag x level
 * x traceId x keyword x time range. Every dimension is optional — an empty
 * selection means "no restriction" for that dimension.
 *
 * Keyword matching is a case-insensitive substring test against the event
 * tag, name, and attr values (not keys); a null/blank keyword disables the
 * dimension.
 */
data class EventFilter(
    val audiences: Set<Audience> = emptySet(),
    val tags: Set<String> = emptySet(),
    val levels: Set<Level> = emptySet(),
    val traceId: String? = null,
    val keyword: String? = null,
    val timeRange: TimeRange = TimeRange(),
) {

    fun matches(event: LogEvent): Boolean {
        if (audiences.isNotEmpty() && event.audience !in audiences) {
            return false
        }
        if (tags.isNotEmpty() && event.tag !in tags) {
            return false
        }
        if (levels.isNotEmpty() && event.level !in levels) {
            return false
        }
        if (traceId != null && event.traceId != traceId) {
            return false
        }
        val query = keyword?.trim()
        if (!query.isNullOrEmpty() && !matchesKeyword(event, query)) {
            return false
        }
        val range = timeRange
        if (range.from != null && event.ts < range.from) {
            return false
        }
        if (range.to != null && event.ts > range.to) {
            return false
        }
        return true
    }

    private fun matchesKeyword(event: LogEvent, query: String): Boolean =
        event.tag.contains(query, ignoreCase = true) ||
            event.name.contains(query, ignoreCase = true) ||
            event.attrs.values.any { value -> value.contains(query, ignoreCase = true) }
}
