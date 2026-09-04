package com.studyfinder.app.util

/**
 * Common UI states for data-backed screens.
 *
 * [Offline] is deliberately distinct from [Error]: it carries cached Room data
 * and the UI shows a "showing cached data" hint rather than a retry-only
 * error screen.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    /** Query succeeded but legitimately returned nothing. */
    data class Empty(val message: String? = null) : UiState<Nothing>

    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>

    data class Offline<T>(val cached: T) : UiState<T>
}

/** Result of a one-shot action (join, approve, upload) rather than a stream. */
sealed interface ActionResult {
    data object Idle : ActionResult
    data object Success : ActionResult
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val errorCode: String? = null
    ) : ActionResult
}

/** Result of an action that returns data. */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>
}
