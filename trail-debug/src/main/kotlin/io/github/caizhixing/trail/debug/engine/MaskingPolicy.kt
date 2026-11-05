package io.github.caizhixing.trail.debug.engine

/**
 * Sensitive-value masking (behavior 3.6, DECISIONS.md P-4). Key rules match
 * whole attr keys case-insensitively (built-in list plus custom exact keys and
 * regexes); value rules inspect only values. A hit replaces the value with
 * `***`. Key rules and value rules are deliberately separate so callers can
 * register e.g. an id-number pattern without touching key handling.
 */
internal class MaskingPolicy private constructor(
    private val exactKeys: MutableSet<String>,
    private val keyRegexes: MutableList<Regex>,
    private val valueRegexes: MutableList<Regex>,
) {

    /** Returns a new insertion-ordered map; keys themselves are never rewritten. */
    fun apply(attrs: Map<String, String>): LinkedHashMap<String, String> {
        val masked = LinkedHashMap<String, String>(attrs.size)
        for ((key, value) in attrs) {
            masked[key] = mask(key, value)
        }
        return masked
    }

    fun addExactKey(key: String): MaskingPolicy {
        exactKeys.add(key.lowercase())
        return this
    }

    fun addKeyRegex(regex: Regex): MaskingPolicy {
        keyRegexes.add(regex)
        return this
    }

    fun addValueRegex(regex: Regex): MaskingPolicy {
        valueRegexes.add(regex)
        return this
    }

    private fun mask(key: String, value: String): String {
        if (exactKeys.contains(key.lowercase())) {
            return MASK
        }
        if (keyRegexes.any { it.containsMatchIn(key) }) {
            return MASK
        }
        if (valueRegexes.any { it.containsMatchIn(value) }) {
            return MASK
        }
        return value
    }

    internal companion object {
        internal const val MASK = "***"

        /** DECISIONS.md P-4 built-in list, matched as whole keys, case-insensitive. */
        internal val BUILT_IN_KEYS: Set<String> = setOf(
            "token",
            "password",
            "secret",
            "authorization",
            "cookie",
            "set-cookie",
            "api_key",
            "phone",
        )

        internal fun default(): MaskingPolicy =
            MaskingPolicy(BUILT_IN_KEYS.toMutableSet(), mutableListOf(), mutableListOf())
    }
}
