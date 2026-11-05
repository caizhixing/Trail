package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import io.github.caizhixing.trail.model.TrailSchema

/**
 * Line-by-line reader for the JSONL produced by [JsonlEncoder]. Not a general
 * JSON parser: it accepts exactly the schema-v1 event shape and returns null
 * for anything malformed, so the viewer can silently skip corrupt lines
 * (behavior 3.8 read path / issue #5 paging).
 */
internal object JsonlDecoder {

    fun decodeEvent(line: String): LogEvent? {
        val parser = Parser(line)
        return runCatching { parser.parseEvent() }.getOrNull()
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseEvent(): LogEvent {
            expect('{')
            var ts: Long? = null
            var traceId: String? = null
            var tag: String? = null
            var name: String? = null
            var audience: Audience? = null
            var level: Level? = null
            var attrs: Map<String, String>? = null
            var tid: String? = null
            var first = true
            while (true) {
                skipWhitespace()
                if (peek() == '}') {
                    pos++
                    break
                }
                if (!first) {
                    expect(',')
                    skipWhitespace()
                }
                first = false
                val key = parseString()
                expect(':')
                skipWhitespace()
                when (key) {
                    TrailSchema.KEY_TS -> ts = parseNumber()
                    TrailSchema.KEY_TRACE_ID -> traceId = parseNullableString()
                    TrailSchema.KEY_TAG -> tag = parseNonEmptyString()
                    TrailSchema.KEY_NAME -> name = parseNonEmptyString()
                    TrailSchema.KEY_AUDIENCE -> audience = parseEnum(Audience.entries)
                    TrailSchema.KEY_LEVEL -> level = parseEnum(Level.entries)
                    TrailSchema.KEY_ATTRS -> attrs = parseAttrs()
                    TrailSchema.KEY_TID -> tid = parseNullableString()
                    else -> skipValue() // forward compatibility: unknown keys ignored
                }
            }
            require(ts != null && tag != null && name != null && audience != null && level != null && attrs != null) {
                "missing required event fields"
            }
            return LogEvent(
                ts = ts,
                traceId = traceId,
                tag = tag,
                name = name,
                audience = audience,
                level = level,
                attrs = attrs,
                tid = tid,
            )
        }

        private fun parseAttrs(): Map<String, String> {
            expect('{')
            val attrs = LinkedHashMap<String, String>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return attrs
            }
            while (true) {
                val key = parseString()
                expect(':')
                attrs[key] = parseString()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return attrs
                    }
                    else -> throw IllegalArgumentException("expected ',' or '}' in attrs")
                }
            }
        }

        private fun parseNullableString(): String? {
            skipWhitespace()
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            return parseString()
        }

        private fun parseNonEmptyString(): String {
            val value = parseString()
            require(value.isNotEmpty()) { "required string field must not be empty" }
            return value
        }

        private fun <T : Enum<T>> parseEnum(entries: List<T>): T {
            val raw = parseString()
            return entries.firstOrNull { it.name == raw }
                ?: throw IllegalArgumentException("unknown enum value $raw")
        }

        private fun parseString(): String {
            skipWhitespace()
            expect('"')
            val out = StringBuilder()
            while (pos < src.length) {
                when (val ch = src[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> out.append(parseEscape())
                    else -> {
                        require(ch.code >= CONTROL_LIMIT) { "raw control character in string" }
                        out.append(ch)
                    }
                }
            }
            throw IllegalArgumentException("unterminated string")
        }

        private fun parseEscape(): Char {
            require(pos < src.length) { "unterminated escape" }
            return when (val esc = src[pos++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw IllegalArgumentException("unsupported escape \\$esc")
            }
        }

        private fun parseUnicodeEscape(): Char {
            require(pos + 4 <= src.length) { "truncated unicode escape" }
            val hex = src.substring(pos, pos + 4)
            pos += 4
            val code = hex.toIntOrNull(16)
            require(code != null) { "bad unicode escape \\u$hex" }
            return code.toChar()
        }

        private fun parseNumber(): Long {
            skipWhitespace()
            val start = pos
            if (peek() == '-') {
                pos++
            }
            while (pos < src.length && src[pos].isDigit()) {
                pos++
            }
            require(pos > start) { "expected number" }
            return src.substring(start, pos).toLong()
        }

        private fun skipValue() {
            skipWhitespace()
            when (peek()) {
                '"' -> parseString()
                '{' -> {
                    pos++
                    var depth = 1
                    while (depth > 0 && pos < src.length) {
                        when (src[pos]) {
                            '{' -> {
                                depth++
                                pos++
                            }
                            '}' -> {
                                depth--
                                pos++
                            }
                            '"' -> parseString()
                            else -> pos++
                        }
                    }
                    require(depth == 0) { "unterminated object" }
                }
                else -> {
                    val start = pos
                    while (pos < src.length && src[pos] != ',' && src[pos] != '}') {
                        pos++
                    }
                    require(pos > start) { "expected a value" }
                }
            }
        }

        private fun peek(): Char {
            require(pos < src.length) { "unexpected end of input" }
            return src[pos]
        }

        private fun expect(ch: Char) {
            require(pos < src.length && src[pos] == ch) { "expected '$ch' at $pos" }
            pos++
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }

        private companion object {
            const val CONTROL_LIMIT = 0x20
        }
    }
}
