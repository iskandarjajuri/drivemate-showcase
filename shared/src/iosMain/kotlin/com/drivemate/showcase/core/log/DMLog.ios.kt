package com.drivemate.showcase.core.log

import platform.Foundation.NSLog
import kotlin.native.Platform
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class) // ← wajib untuk Platform.isDebugBinary
actual object DMLog {
    private val enabled = Platform.isDebugBinary

    /**
     * Emit one already-formatted line to NSLog.
     *
     * NSLog is a C variadic whose FIRST argument is a printf-style format string.
     * Two things must never happen here:
     *
     *  1. Do NOT pass Kotlin `String`s as `%@` varargs. Kotlin/Native does not
     *     bridge a Kotlin String into a valid ObjC `id` across an ObjC variadic,
     *     so NSLog ends up sending `-description` to a non-object pointer and the
     *     app dies with EXC_BAD_ACCESS (SIGSEGV) inside objc_opt_respondsToSelector.
     *     This crashed the app on EVERY DMLog call made from commonMain — the
     *     driver dashboard segfaulted on login via GetWorkDaysByRangeUseCase.
     *
     *  2. Do NOT pass a caller-supplied message as the format string itself. Any
     *     stray `%` in a message (a `%s` in an error text, a URL-encoded value)
     *     would then be read as a format specifier and dereference garbage — the
     *     same crash, just harder to reproduce.
     *
     * So: build the whole line in Kotlin, escape `%`, and hand NSLog a format
     * string with no specifiers left in it.
     */
    private fun emit(line: String) {
        if (!enabled) return
        NSLog(line.replace("%", "%%"))
    }

    actual fun v(tag: String, msg: String) = emit("🟢 [VERBOSE] $tag: $msg")
    actual fun d(tag: String, msg: String) = emit("🔵 [DEBUG] $tag: $msg")
    actual fun i(tag: String, msg: String) = emit("🟣 [INFO] $tag: $msg")

    actual fun w(tag: String, msg: String, t: Throwable?) {
        emit("🟠 [WARN] $tag: $msg ${t?.message.orEmpty()}".trimEnd())
    }

    actual fun e(tag: String, msg: String, t: Throwable?) {
        emit("🔴 [ERROR] $tag: $msg ${t?.message.orEmpty()}".trimEnd())
    }
}
