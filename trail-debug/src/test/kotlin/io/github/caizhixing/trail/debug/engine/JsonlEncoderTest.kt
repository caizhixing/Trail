package io.github.caizhixing.trail.debug.engine

import io.github.caizhixing.trail.model.Audience
import io.github.caizhixing.trail.model.Level
import io.github.caizhixing.trail.model.LogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior 3.4 (event line: frozen field order, null literals, enum names,
 * attrs insertion order, escape table) and behavior 3.5 (meta line six keys,
 * device fallback).
 */
class JsonlEncoderTest {

    @Test
    fun eventLineFrozenFieldOrderAndLiterals() {
        val event = eventOf(traceId = null, tid = null, attrs = linkedMapOf("slot" to "search"))

        assertEquals(
            "{\"ts\":1234567890123,\"traceId\":null,\"tag\":\"widget\",\"name\":\"bind_start\"," +
                "\"audience\":\"AI\",\"level\":\"INFO\",\"attrs\":{\"slot\":\"search\"},\"tid\":null}",
            JsonlEncoder.encodeEvent(event),
        )
    }

    @Test
    fun nonNullTraceIdAndTidAreQuotedStrings() {
        val event = eventOf(traceId = "t_00112233445566ff", tid = "main")

        val line = JsonlEncoder.encodeEvent(event)

        assertTrue(line.contains("\"traceId\":\"t_00112233445566ff\""))
        assertTrue(line.contains("\"tid\":\"main\""))
    }

    @Test
    fun enumValuesEmitTheirNames() {
        val line = JsonlEncoder.encodeEvent(eventOf(audience = Audience.BOTH, level = Level.ERROR))

        assertTrue(line.contains("\"audience\":\"BOTH\""))
        assertTrue(line.contains("\"level\":\"ERROR\""))
    }

    @Test
    fun emptyAttrsRenderAnEmptyObject() {
        assertTrue(JsonlEncoder.encodeEvent(eventOf()).contains("\"attrs\":{}"))
    }

    @Test
    fun attrsKeepInsertionOrder() {
        val attrs = LinkedHashMap<String, String>()
        attrs["zeta"] = "1"
        attrs["alpha"] = "2"
        attrs["mid"] = "3"

        val line = JsonlEncoder.encodeEvent(eventOf(attrs = attrs))

        assertTrue(line.contains("\"attrs\":{\"zeta\":\"1\",\"alpha\":\"2\",\"mid\":\"3\"}"))
    }

    @Test
    fun escapeTableCoversQuoteAndBackslash() {
        assertEquals("a\\\"b", JsonlEncoder.escape("a\"b"))
        assertEquals("a\\\\b", JsonlEncoder.escape("a\\b"))
        assertEquals("", JsonlEncoder.escape(""))
    }

    @Test
    fun escapeTableCoversControlRangeBoundaries() {
        assertEquals("\\u0000", JsonlEncoder.escape("\u0000"))
        assertEquals("\\u001f", JsonlEncoder.escape("\u001F"))
        assertEquals("\\u0001", JsonlEncoder.escape("\u0001"))
    }

    @Test
    fun escapeTableEmitsLowercaseHexUnicodeForCommonControls() {
        assertEquals("\\u000a", JsonlEncoder.escape("\n"))
        assertEquals("\\u000d", JsonlEncoder.escape("\r"))
        assertEquals("\\u0009", JsonlEncoder.escape("\t"))
        assertEquals("\\u0008", JsonlEncoder.escape("\b"))
        assertEquals("\\u000c", JsonlEncoder.escape("\u000C"))
    }

    @Test
    fun nonAsciiCharactersStayVerbatim() {
        assertEquals("城市 搜索", JsonlEncoder.escape("城市 搜索"))
        assertEquals("emoji:🚀", JsonlEncoder.escape("emoji:🚀"))
    }

    @Test
    fun forwardSlashAndHighControlCharsStayVerbatim() {
        assertEquals("a/b", JsonlEncoder.escape("a/b"))
        assertEquals("del\u007F", JsonlEncoder.escape("del\u007F"))
    }

    @Test
    fun valueWithRawNewlineStaysASinglePhysicalLine() {
        val line = JsonlEncoder.encodeEvent(eventOf(tag = "wi\ndget"))

        assertFalse(line.contains('\n'))
        assertTrue(line.contains("wi\\u000adget"))
    }

    @Test
    fun metaLineHasTheSixFrozenKeysInOrder() {
        val meta = ExportMeta(
            schema = 1,
            app = "io.github.caizhixing.trail.sample",
            version = "1.2.3",
            device = "Pixel 9",
            exportedAt = "2026-11-05T18:00:00+08:00",
            filter = "trace=t_00112233445566ff",
        )

        assertEquals(
            "{\"schema\":1,\"app\":\"io.github.caizhixing.trail.sample\",\"version\":\"1.2.3\"," +
                "\"device\":\"Pixel 9\",\"exported_at\":\"2026-11-05T18:00:00+08:00\"," +
                "\"filter\":\"trace=t_00112233445566ff\"}",
            JsonlEncoder.encodeMeta(meta),
        )
    }

    @Test
    fun metaDeviceProviderThrowingCollapsesToUnknown() {
        assertEquals("unknown", ExportMeta.deviceOrUnknown { throw IllegalStateException("stub!") })
    }

    @Test
    fun metaDeviceProviderNullOrBlankCollapsesToUnknown() {
        assertEquals("unknown", ExportMeta.deviceOrUnknown { null })
        assertEquals("unknown", ExportMeta.deviceOrUnknown { "" })
        assertEquals("unknown", ExportMeta.deviceOrUnknown { "   " })
    }

    @Test
    fun metaDeviceProviderValueIsKeptAsIs() {
        assertEquals("Pixel 9", ExportMeta.deviceOrUnknown { "Pixel 9" })
    }

    private fun eventOf(
        traceId: String? = null,
        tid: String? = null,
        tag: String = "widget",
        audience: Audience = Audience.AI,
        level: Level = Level.INFO,
        attrs: Map<String, String> = emptyMap(),
    ): LogEvent =
        LogEvent(
            ts = 1234567890123L,
            traceId = traceId,
            tag = tag,
            name = "bind_start",
            audience = audience,
            level = level,
            attrs = attrs,
            tid = tid,
        )
}
