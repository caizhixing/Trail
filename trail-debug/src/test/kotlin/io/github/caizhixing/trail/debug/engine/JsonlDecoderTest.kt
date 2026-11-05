package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip and rejection behavior of the hand-written decoder: it accepts
 * exactly what [JsonlEncoder] emits plus unknown keys, and returns null for
 * anything malformed so readers can skip corrupt lines.
 */
class JsonlDecoderTest {

    @Test
    fun roundTripPreservesAllFieldsIncludingEscapes() {
        val attrs = LinkedHashMap<String, String>()
        attrs["path"] = "a\"b\\c\nd"
        attrs["city"] = "城市"
        attrs["ctl"] = "\u0001\u001F"
        val original = LogEvent(
            ts = 1234567890123L,
            traceId = null,
            tag = "widget",
            name = "bind_start",
            audience = Audience.AI,
            level = Level.INFO,
            attrs = attrs,
            tid = null,
        )

        assertEquals(original, JsonlDecoder.decodeEvent(JsonlEncoder.encodeEvent(original)))
    }

    @Test
    fun roundTripWithNonNullTraceAndTid() {
        val original = LogEvent(
            ts = -42L,
            traceId = "t_00112233445566ff",
            tag = "network",
            name = "http_response",
            audience = Audience.BOTH,
            level = Level.WARN,
            attrs = emptyMap(),
            tid = "DefaultDispatcher-worker-1",
        )

        assertEquals(original, JsonlDecoder.decodeEvent(JsonlEncoder.encodeEvent(original)))
    }

    @Test
    fun roundTripKeepsAttrsInsertionOrder() {
        val attrs = LinkedHashMap<String, String>()
        attrs["z"] = "1"
        attrs["a"] = "2"

        val decoded = decodeMinimal(attrs)

        assertEquals(listOf("z", "a"), decoded.attrs.keys.toList())
    }

    @Test
    fun decodeSkipsUnknownKeysForForwardCompatibility() {
        val line = "{\"ts\":7,\"future\":\"text\",\"traceId\":null,\"tag\":\"t\",\"name\":\"n\"," +
            "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null,\"nested\":{\"k\":\"v\"},\"flag\":true}"

        val decoded = JsonlDecoder.decodeEvent(line)

        assertEquals(7L, decoded?.ts)
        assertEquals("t", decoded?.tag)
        assertEquals(emptyMap<String, String>(), decoded?.attrs)
    }

    @Test
    fun decodeRejectsMissingRequiredFields() {
        val line = "{\"traceId\":null,\"tag\":\"t\",\"name\":\"n\",\"audience\":\"AI\"," +
            "\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertNull(JsonlDecoder.decodeEvent(line))
    }

    @Test
    fun decodeRejectsUnknownEnumValue() {
        val line = "{\"ts\":1,\"traceId\":null,\"tag\":\"t\",\"name\":\"n\"," +
            "\"audience\":\"EVERYONE\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertNull(JsonlDecoder.decodeEvent(line))
    }

    @Test
    fun decodeRejectsRawControlCharacterInsideString() {
        val line = "{\"ts\":1,\"traceId\":null,\"tag\":\"ab\",\"name\":\"n\"," +
            "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertNull(JsonlDecoder.decodeEvent(line.replace("ab", "a\u0001b")))
    }

    @Test
    fun decodeRejectsUnsupportedEscape() {
        val line = "{\"ts\":1,\"traceId\":null,\"tag\":\"a\\xb\",\"name\":\"n\"," +
            "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertNull(JsonlDecoder.decodeEvent(line))
    }

    @Test
    fun decodeRejectsNonObjectLine() {
        assertNull(JsonlDecoder.decodeEvent("abc"))
        assertNull(JsonlDecoder.decodeEvent(""))
    }

    @Test
    fun decodeRejectsTruncatedString() {
        val line = "{\"ts\":1,\"traceId\":null,\"tag\":\"unterminated\",\"name\":\"n\"," +
            "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertNull(JsonlDecoder.decodeEvent(line.replace("unterminated", "unterm\\u00")))
    }

    @Test
    fun decodeAcceptsUpperAndLowercaseHexEscapes() {
        val line = "{\"ts\":1,\"traceId\":null,\"tag\":\"a\\u000Ab\",\"name\":\"n\"," +
            "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{},\"tid\":null}"

        assertEquals("a\nb", JsonlDecoder.decodeEvent(line)?.tag)
    }

    private fun decodeMinimal(attrs: Map<String, String>): LogEvent {
        val event = LogEvent(
            ts = 1L,
            traceId = null,
            tag = "t",
            name = "n",
            audience = Audience.AI,
            level = Level.INFO,
            attrs = attrs,
            tid = null,
        )
        return JsonlDecoder.decodeEvent(JsonlEncoder.encodeEvent(event))
            ?: error("minimal event must round-trip")
    }
}
