package io.github.caizhixing.trail.debug.trace

import java.security.SecureRandom

/**
 * Trace id generation, format frozen by DECISIONS.md P-7: `t_` followed by
 * 16 hex chars (8 random bytes, lowercase).
 */
internal object TraceIds {

    private const val PREFIX = "t_"
    private const val RANDOM_BYTES = 8
    private const val HEX_DIGITS = "0123456789abcdef"

    private val random = SecureRandom()

    fun newId(): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        val builder = StringBuilder(PREFIX.length + RANDOM_BYTES * 2)
        builder.append(PREFIX)
        for (byte in bytes) {
            builder.append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
            builder.append(HEX_DIGITS[byte.toInt() and 0xF])
        }
        return builder.toString()
    }
}
