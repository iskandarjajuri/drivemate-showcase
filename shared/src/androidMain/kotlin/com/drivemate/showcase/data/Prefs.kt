package com.drivemate.showcase.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivemate.showcase.core.log.DMLog

private const val STORE_NAME = "drive_mate_prefs"
val Context.dataStore by preferencesDataStore(STORE_NAME)

object Prefs {
    private const val TAG = "DM/Prefs"

    /**
     * One-time cleanup for obsolete keys from pre-UID-only era.
     * Call once at startup if needed.
     */
    suspend fun migrateCleanup(ctx: Context) {
        DMLog.i(TAG, "migrateCleanup() — removing legacy keys [role, driverId, userType]")
        ctx.dataStore.edit { p ->
            p.remove(stringPreferencesKey("role"))
            p.remove(stringPreferencesKey("driverId"))
            p.remove(stringPreferencesKey("userType"))
        }
    }
}
