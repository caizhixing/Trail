package io.github.caizhixing.trail.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailSchemaTest {

    @Test
    fun `version is one`() {
        assertEquals(1, TrailSchema.VERSION)
    }

    @Test
    fun `jsonl line keys match frozen literals`() {
        assertEquals("ts", TrailSchema.KEY_TS)
        assertEquals("traceId", TrailSchema.KEY_TRACE_ID)
        assertEquals("tag", TrailSchema.KEY_TAG)
        assertEquals("name", TrailSchema.KEY_NAME)
        assertEquals("audience", TrailSchema.KEY_AUDIENCE)
        assertEquals("level", TrailSchema.KEY_LEVEL)
        assertEquals("attrs", TrailSchema.KEY_ATTRS)
        assertEquals("tid", TrailSchema.KEY_TID)
    }

    @Test
    fun `export meta keys match frozen literals`() {
        assertEquals("schema", TrailSchema.KEY_META_SCHEMA)
        assertEquals("app", TrailSchema.KEY_META_APP)
        assertEquals("version", TrailSchema.KEY_META_VERSION)
        assertEquals("device", TrailSchema.KEY_META_DEVICE)
        assertEquals("exported_at", TrailSchema.KEY_META_EXPORTED_AT)
        assertEquals("filter", TrailSchema.KEY_META_FILTER)
    }
}
