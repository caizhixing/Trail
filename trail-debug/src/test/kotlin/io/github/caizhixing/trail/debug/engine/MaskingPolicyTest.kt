package io.github.caizhixing.trail.debug.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior 3.6: built-in key list (P-4), custom exact keys, key/value regex rules. */
class MaskingPolicyTest {

    @Test
    fun builtInKeysAreEachMasked() {
        val policy = MaskingPolicy.default()

        for (key in MaskingPolicy.BUILT_IN_KEYS) {
            assertEquals(
                "key <$key> must be masked",
                MaskingPolicy.MASK,
                policy.apply(mapOf(key to "sensitive-value"))[key],
            )
        }
    }

    @Test
    fun builtInKeysMatchCaseInsensitively() {
        val policy = MaskingPolicy.default()
        val attrs = mapOf(
            "TOKEN" to "a",
            "Password" to "b",
            "SET-COOKIE" to "c",
            "API_KEY" to "d",
            "PhOnE" to "e",
        )

        val masked = policy.apply(attrs)

        assertEquals(5, masked.values.filter { it == MaskingPolicy.MASK }.size)
    }

    @Test
    fun builtInKeysRequireWholeWordMatch() {
        val policy = MaskingPolicy.default()
        val attrs = mapOf(
            "user_token" to "a",
            "phone_number" to "b",
            "authorizationretry" to "c",
            "tokens" to "d",
        )

        val masked = policy.apply(attrs)

        assertEquals(attrs, masked)
    }

    @Test
    fun customExactKeyMasksOnlyThatKey() {
        val policy = MaskingPolicy.default().addExactKey("sessionKey")

        val masked = policy.apply(mapOf("sessionKey" to "abc", "other" to "keep"))

        assertEquals(MaskingPolicy.MASK, masked["sessionKey"])
        assertEquals("keep", masked["other"])
    }

    @Test
    fun customExactKeyMatchesCaseInsensitivelyLikeBuiltIns() {
        val policy = MaskingPolicy.default().addExactKey("DeviceId")

        assertEquals(MaskingPolicy.MASK, policy.apply(mapOf("deviceid" to "x"))["deviceid"])
    }

    @Test
    fun customKeyRegexMasksMatchingKeys() {
        val policy = MaskingPolicy.default().addKeyRegex(Regex("^ssn_\\w+$"))

        val masked = policy.apply(mapOf("ssn_primary" to "123", "ssn" to "456", "note" to "ok"))

        assertEquals(MaskingPolicy.MASK, masked["ssn_primary"])
        assertEquals("456", masked["ssn"])
        assertEquals("ok", masked["note"])
    }

    @Test
    fun customValueRegexMasksMatchingValuesOnly() {
        val policy = MaskingPolicy.default().addValueRegex(Regex("1[3-9]\\d{9}"))

        val masked = policy.apply(
            mapOf(
                "contact" to "13800138000",
                "count" to "13800138000",
                "id" to "12345",
            ),
        )

        assertEquals(MaskingPolicy.MASK, masked["contact"])
        assertEquals(MaskingPolicy.MASK, masked["count"])
        assertEquals("12345", masked["id"])
    }

    @Test
    fun keyRulesAndValueRulesAreIndependent() {
        val keyPolicy = MaskingPolicy.default().addKeyRegex(Regex("^note$"))
        val valuePolicy = MaskingPolicy.default().addValueRegex(Regex("leak"))

        // key rule fires on the key, value stays irrelevant
        assertEquals(MaskingPolicy.MASK, keyPolicy.apply(mapOf("note" to "clean"))["note"])
        assertEquals("clean", keyPolicy.apply(mapOf("memo" to "clean"))["memo"])

        // value rule fires on the value, key stays irrelevant
        assertEquals(MaskingPolicy.MASK, valuePolicy.apply(mapOf("memo" to "has leak"))["memo"])
        assertEquals("clean", valuePolicy.apply(mapOf("leak" to "clean"))["leak"])
    }

    @Test
    fun unmatchedEntriesAreUntouched() {
        val policy = MaskingPolicy.default()

        val attrs = mapOf("slot" to "search", "attempt" to "2")

        assertEquals(attrs, policy.apply(attrs))
    }

    @Test
    fun applyPreservesInsertionOrder() {
        val policy = MaskingPolicy.default()
        val attrs = LinkedHashMap<String, String>()
        attrs["password"] = "secret-value"
        attrs["slot"] = "search"

        val masked = policy.apply(attrs)

        assertEquals(listOf("password", "slot"), masked.keys.toList())
        assertEquals(MaskingPolicy.MASK, masked["password"])
        assertTrue(masked.containsKey("slot"))
    }
}
