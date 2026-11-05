package io.github.caizhixing.trail.debug.export

import io.github.caizhixing.trail.debug.viewer.EventFilter
import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent

private const val MILLIS_PER_HOUR = 3_600_000L
private const val UNBOUNDED = "-"
private const val SEPARATOR = "|"

/**
 * Pure export scope selector (behavior 6.1). Every scope resolves to a
 * `LogEvent` predicate against an injected "now" instant, so `RecentHours`
 * stays side-effect free and fully testable.
 *
 * Boundary semantics (contract): a non-positive hour count and a blank trace
 * id resolve to an always-false predicate — an empty selection, never an
 * error; timestamps in the future (`ts > now`) fall outside the recent
 * window; an unknown trace id simply matches nothing.
 */
internal sealed interface ExportScope {

    /** The scope's inclusion predicate at instant [nowMs]. */
    fun toPredicate(nowMs: Long): (LogEvent) -> Boolean

    /** Deterministic one-line description for the meta `filter` key. */
    fun summary(nowMs: Long): String

    /** "Current filter results": delegates to the viewer's six-dimension filter. */
    data class CurrentFilter(val filter: EventFilter) : ExportScope {

        override fun toPredicate(nowMs: Long): (LogEvent) -> Boolean = filter::matches

        override fun summary(nowMs: Long): String = buildString {
            append("current_filter")
            if (filter.audiences.isNotEmpty()) {
                append(";audience=")
                append(Audience.entries.filter { it in filter.audiences }.joinToString(SEPARATOR) { it.name })
            }
            if (filter.tags.isNotEmpty()) {
                append(";tag=").append(filter.tags.sorted().joinToString(SEPARATOR))
            }
            if (filter.levels.isNotEmpty()) {
                append(";level=")
                append(Level.entries.filter { it in filter.levels }.joinToString(SEPARATOR) { it.name })
            }
            filter.traceId?.let { traceId ->
                append(";trace=").append(traceId)
            }
            filter.keyword?.takeIf { it.isNotBlank() }?.let { keyword ->
                append(";keyword=").append(keyword)
            }
            if (!filter.timeRange.isEmpty) {
                append(";time=[")
                append(filter.timeRange.from?.toString() ?: UNBOUNDED)
                append("..")
                append(filter.timeRange.to?.toString() ?: UNBOUNDED)
                append("]")
            }
        }
    }

    /**
     * "Last [hours] hours": the inclusive instant window
     * `[now - hours*1h, now]`; future timestamps are excluded, and a
     * non-positive [hours] yields an empty selection.
     */
    data class RecentHours(val hours: Int) : ExportScope {

        override fun toPredicate(nowMs: Long): (LogEvent) -> Boolean {
            if (hours <= 0) {
                return { false }
            }
            val from = nowMs - hours * MILLIS_PER_HOUR
            return { event -> event.ts >= from && event.ts <= nowMs }
        }

        override fun summary(nowMs: Long): String = "recent_hours=$hours"
    }

    /** "One trace": exact `traceId` match; a blank id yields an empty selection. */
    data class Trace(val traceId: String) : ExportScope {

        override fun toPredicate(nowMs: Long): (LogEvent) -> Boolean {
            if (traceId.isBlank()) {
                return { false }
            }
            return { event -> event.traceId == traceId }
        }

        override fun summary(nowMs: Long): String = "trace=$traceId"
    }
}
