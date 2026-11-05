package io.github.caizhixing.trail.model

data class LogEvent(
    val ts: Long,
    val traceId: String?,
    val tag: String,
    val name: String,
    val audience: Audience,
    val level: Level,
    val attrs: Map<String, String>,
    val tid: String?,
)
