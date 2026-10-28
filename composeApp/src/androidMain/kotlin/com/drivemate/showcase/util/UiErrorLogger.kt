package com.drivemate.showcase.util

import android.util.Log
import com.drivemate.showcase.BuildConfig

object UiErrorLogger {
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } else {
            Log.e(tag, message)
        }
    }
}
