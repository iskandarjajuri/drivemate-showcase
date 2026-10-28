package com.drivemate.showcase.util

import android.content.Context
import android.content.pm.ApplicationInfo

fun isAppDebuggable(context: Context): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
