package com.drivemate.showcase.core.util
/**
 * ✅ KMM‑safe result wrapper.
 * - Use this instead of kotlin.Result (interop & KMM caveats).
 * - Keep minimal surface: Success(data) | Failure(throwable)
 */
sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Failure(val throwable: Throwable) : AppResult<Nothing>()

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun failure(throwable: Throwable): AppResult<Nothing> = Failure(throwable)
    }
}

class AlreadySubmittedException(message: String) : IllegalStateException(message)

// ──────────────────────────────────────────────────────────────────────────────
// 🧩 Functional-style extensions (KMM-safe)
// ──────────────────────────────────────────────────────────────────────────────

/** Transform data on success T → R; pass-through failures. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.success(transform(data))
        is AppResult.Failure -> this
    }

/** Flat-map chaining for operations that can fail. */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }

/** Get success data or null. */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success<T>)?.data

/** Map/transform the error throwable. */
inline fun <T> AppResult<T>.mapError(transform: (Throwable) -> Throwable): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.failure(transform(throwable))
    }

/** Convert any result to Unit (handy for logout/use-cases). */
fun <T> AppResult<T>.toUnit(): AppResult<Unit> = map { }

// ──────────────────────────────────────────────────────────────────────────────
// 🔁 Kotlin Result–like helpers (compat shims to avoid common porting mistakes)
// ──────────────────────────────────────────────────────────────────────────────

/** True if this is Success; false otherwise. */
val AppResult<*>.isSuccess: Boolean get() = this is AppResult.Success<*>

/** True if this is Failure; false otherwise. */
val AppResult<*>.isFailure: Boolean get() = this is AppResult.Failure

/** Return throwable when Failure; null otherwise. */
fun AppResult<*>.exceptionOrNull(): Throwable? = (this as? AppResult.Failure)?.throwable

// ──────────────────────────────────────────────────────────────────────────────
// 🧭 UI / ViewModel convenience helpers
// ──────────────────────────────────────────────────────────────────────────────

/** Run block if Success; returns self for chaining. */
inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success<T>) block(this.data)
    return this
}

/** Run block if Failure; returns self for chaining. */
inline fun <T> AppResult<T>.onFailure(block: (Throwable) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(this.throwable)
    return this
}

/** Get success data or throw the contained throwable. */
fun <T> AppResult<T>.getOrThrow(): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> throw throwable
}
