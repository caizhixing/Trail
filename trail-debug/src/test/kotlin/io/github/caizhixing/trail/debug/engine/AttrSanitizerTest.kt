package io.github.caizhixing.trail.debug.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Behavior 3.7: attr value truncation to the configured cap, after masking. */
class AttrSanitizerTest {

    @Test
    fun valueAboveLimitIsTruncatedToExactlyTheLimit() {
        val sanitizer = AttrSanitizer(MaskingPolicy.default(), maxValueLength = 512)

        val sanitized = sanitizer.sanitize(mapOf("note" to "a".repeat(513)))

        assertEquals(512, sanitized["note"]?.length)
        assertEquals("a".repeat(512), sanitized["note"])
    }

    @Test
    fun valueAtTheLimitIsUntouched() {
        val sanitizer = AttrSanitizer(MaskingPolicy.default(), maxValueLength = 512)

        val sanitized = sanitizer.sanitize(mapOf("note" to "a".repeat(512)))

        assertEquals("a".repeat(512), sanitized["note"])
    }

    @Test
    fun maskingRunsBeforeTruncation() {
        val sanitizer = AttrSanitizer(MaskingPolicy.default(), maxValueLength = 512)

        val sanitized = sanitizer.sanitize(mapOf("password" to "s".repeat(600)))

        // masked first -> short mask value, never re-truncated, nothing leaked
        assertEquals(MaskingPolicy.MASK, sanitized["password"])
    }

    @Test
    fun longSecretPastTheCutIsStillFullyMasked() {
        // the secret sits beyond the truncation point: masking must run first,
        // otherwise a naive truncate-then-mask would leak "prefix-p"
        val policy = MaskingPolicy.default().addValueRegex(Regex("password"))
        val sanitizer = AttrSanitizer(policy, maxValueLength = 8)

        val sanitized = sanitizer.sanitize(mapOf("note" to "prefix-password-suffix"))

        assertEquals(MaskingPolicy.MASK, sanitized["note"])
    }

    @Test
    fun orderPreservedAfterSanitize() {
        val sanitizer = AttrSanitizer(MaskingPolicy.default(), maxValueLength = 4)
        val attrs = LinkedHashMap<String, String>()
        attrs["b"] = "long-enough-value"
        attrs["a"] = "x"

        val sanitized = sanitizer.sanitize(attrs)

        assertEquals(listOf("b", "a"), sanitized.keys.toList())
        assertEquals("long", sanitized["b"])
        assertEquals("x", sanitized["a"])
    }

    @Test
    fun emptyAttrsReturnTheSameInstance() {
        val sanitizer = AttrSanitizer(MaskingPolicy.default(), maxValueLength = 512)
        val attrs = emptyMap<String, String>()

        assertSame(attrs, sanitizer.sanitize(attrs))
    }
}
