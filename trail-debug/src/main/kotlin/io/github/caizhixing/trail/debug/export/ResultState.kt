package io.github.caizhixing.trail.debug.export

/**
 * Unified business success/failure contract (global coding rules, "ResultState
 * 统一规范"). Export (issue #6) is the first consumer; later consumers reuse
 * this type instead of `Boolean` flags.
 */
sealed interface ResultState<T> {
    data class Success<T>(val data: T) : ResultState<T>

    data class Error<T>(
        val message: String,
        val throwable: Throwable? = null,
    ) : ResultState<T>

    class Loading<T> : ResultState<T>
}
