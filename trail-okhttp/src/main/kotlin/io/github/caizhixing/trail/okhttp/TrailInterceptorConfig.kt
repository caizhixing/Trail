package io.github.caizhixing.trail.okhttp

/**
 * Frozen defaults per PRD §2.6 / P-6: body recording on with a 4 KB cap per
 * captured body. [headerAttrs] is the custom extension point of behavior 7.6:
 * opt-in request-header names copied into event attrs (masked when sensitive).
 */
public data class TrailInterceptorConfig(
    val recordBodies: Boolean = true,
    val maxBodyBytes: Int = 4096,
    val headerAttrs: List<String> = emptyList(),
)
