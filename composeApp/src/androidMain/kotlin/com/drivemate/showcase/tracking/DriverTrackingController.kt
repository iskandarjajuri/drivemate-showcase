package com.drivemate.showcase.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object DriverTrackingController {
    fun start(context: Context) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun stopForPermissionMissing(context: Context) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_PERMISSION_MISSING
        }
        context.startService(intent)
    }
}
