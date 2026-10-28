package com.drivemate.showcase.core.log

/**
 * Multiplatform logging facade (common).
 * Implementations are in androidMain & iosMain.
 */
expect object DMLog {
    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, t: Throwable? = null)
    fun e(tag: String, msg: String, t: Throwable? = null)
}