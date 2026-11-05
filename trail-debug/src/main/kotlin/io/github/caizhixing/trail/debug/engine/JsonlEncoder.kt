package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailSchema

/**
 * Hand-written JSONL encoder (DECISIONS.md "JSONL 手写编码器": no serialization
 * runtime on the api surface). Field order is frozen by schema v1:
 * ts, traceId, tag, name, audience, level, attrs, tid (behavior 3.4 / 3.5).
 *
 * Escaping rules: `"` and `\` use the two-character escapes, U+0000..U+001F
 * become `\u00xx` (lowercase hex), everything else — including non-ASCII — is
 * emitted verbatim, so a value containing a raw newline never breaks the line.
 */
internal object JsonlEncoder {

    fun encodeEvent(event: LogEvent): String = buildString {
        append('{')
        field(TrailSchema.KEY_TS)
        append(event.ts)
        field(TrailSchema.KEY_TRACE_ID)
        appendNullableString(event.traceId)
        field(TrailSchema.KEY_TAG)
        appendString(event.tag)
        field(TrailSchema.KEY_NAME)
        appendString(event.name)
        field(TrailSchema.KEY_AUDIENCE)
        appendString(event.audience.name)
        field(TrailSchema.KEY_LEVEL)
        appendString(event.level.name)
        field(TrailSchema.KEY_ATTRS)
        appendAttrs(event.attrs)
        field(TrailSchema.KEY_TID)
        appendNullableString(event.tid)
        append('}')
    }

    fun encodeMeta(meta: ExportMeta): String = buildString {
        append('{')
        field(TrailSchema.KEY_META_SCHEMA)
        append(meta.schema)
        field(TrailSchema.KEY_META_APP)
        appendString(meta.app)
        field(TrailSchema.KEY_META_VERSION)
        appendString(meta.version)
        field(TrailSchema.KEY_META_DEVICE)
        appendString(meta.device)
        field(TrailSchema.KEY_META_EXPORTED_AT)
        appendString(meta.exportedAt)
        field(TrailSchema.KEY_META_FILTER)
        appendString(meta.filter)
        append('}')
    }

    /** Visible for the escape-table tests; the same routine backs every string. */
    fun escape(value: String): String {
        if (value.none { it == '"' || it == '\\' || it.code < CONTROL_LIMIT }) {
            return value
        }
        return buildString(value.length + 8) {
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    else ->
                        if (ch.code < CONTROL_LIMIT) {
                            append("\\u")
                            append(ch.code.toString(HEX_RADIX).padStart(4, '0'))
                        } else {
                            append(ch)
                        }
                }
            }
        }
    }

    // Top-level field separator: relies on the fact that both entry encoders
    // open with exactly one '{' before the first field. Nested objects must
    // track their own separator (see appendAttrs).
    private fun StringBuilder.field(key: String) {
        if (length > 1) {
            append(',')
        }
        append('"').append(key).append("\":")
    }

    private fun StringBuilder.appendNullableString(value: String?) {
        if (value == null) {
            append("null")
        } else {
            appendString(value)
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"').append(escape(value)).append('"')
    }

    private fun StringBuilder.appendAttrs(attrs: Map<String, String>) {
        append('{')
        var firstEntry = true
        for ((key, value) in attrs) {
            if (!firstEntry) {
                append(',')
            }
            firstEntry = false
            append('"').append(escape(key)).append("\":")
            appendString(value)
        }
        append('}')
    }

    private const val CONTROL_LIMIT = 0x20
    private const val HEX_RADIX = 16
}
