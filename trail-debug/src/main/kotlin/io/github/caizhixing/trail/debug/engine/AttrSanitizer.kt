package io.github.caizhixing.trail.debug.engine

/**
 * Entry-stage attr cleanup: mask first, then truncate (behavior 3.6 + 3.7).
 * Masking happens before truncation so a secret beyond the cut is still
 * replaced rather than half-leaked past a truncation boundary. Insertion
 * order is preserved.
 */
internal class AttrSanitizer(
    private val masking: MaskingPolicy,
    private val maxValueLength: Int,
) {

    fun sanitize(attrs: Map<String, String>): Map<String, String> {
        if (attrs.isEmpty()) {
            return attrs
        }
        val masked = masking.apply(attrs)
        if (masked.values.all { it.length <= maxValueLength }) {
            return masked
        }
        return masked.mapValuesTo(LinkedHashMap(masked.size)) { it.value.take(maxValueLength) }
    }
}
