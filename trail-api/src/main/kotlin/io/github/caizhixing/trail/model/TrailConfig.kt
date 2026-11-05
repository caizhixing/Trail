package io.github.caizhixing.trail.model

data class TrailConfig(
    val ringCapacity: Int = 2000,
    val maxFileBytes: Long = 5L * 1024 * 1024,
    val maxFiles: Int = 3,
    val attrValueMaxLength: Int = 512,
    val logcatEcho: Boolean = false,
)
