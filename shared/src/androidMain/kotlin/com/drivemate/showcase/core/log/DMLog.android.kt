package com.drivemate.showcase.core.log

import android.util.Log

internal object AndroidDebugFlags {
    @Volatile var isDebug: Boolean = false
}

actual object DMLog {
    private val enabled: Boolean
        get() = AndroidDebugFlags.isDebug

    actual fun d(tag: String, msg: String) { if (enabled) Log.d(tag, msg) }
    actual fun i(tag: String, msg: String) { if (enabled) Log.i(tag, msg) }
    actual fun w(tag: String, msg: String, t: Throwable?) {
        if (!enabled) return
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
    }
    actual fun e(tag: String, msg: String, t: Throwable?) {
        if (!enabled) return
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }

    actual fun v(tag: String, msg: String) {
    }
}
