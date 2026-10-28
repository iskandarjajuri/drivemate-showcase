package com.drivemate.showcase.data

import android.content.Context
import android.content.SharedPreferences
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.toFirestore
import java.util.Locale
import androidx.core.content.edit

private const val TAG = "DM/RoleCache"
private const val PREF_NAME = "role_cache"
private const val PREF_KEY_VERSION = "role.cache.ver"
private const val CUR_VER = 2
private const val LEGACY_EMAIL_PREFIX = "role.email."
private const val LEGACY_LAST_EMAIL = "last_email"
private const val LEGACY_LAST_ROLE = "last_role"
private const val LEGACY_LAST_ROLE_UPDATED_AT = "last_role_updated_at"

interface RoleCache {
    fun save(uid: String, role: Role)
    fun get(uid: String): Role?
    fun clear()
}

class RoleCacheImpl(appContext: Context) : RoleCache {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun migrateIfNeeded() {
        val ver = prefs.getInt(PREF_KEY_VERSION, 0)
        if (ver >= CUR_VER) return

        DMLog.i(TAG, "migrateIfNeeded(): $ver → $CUR_VER — clearing legacy email-based keys")
        prefs.edit {
            prefs.all.keys
                .filter { key ->
                    key.startsWith(LEGACY_EMAIL_PREFIX) ||
                            key == LEGACY_LAST_EMAIL ||
                            key == LEGACY_LAST_ROLE ||
                            key == LEGACY_LAST_ROLE_UPDATED_AT
                }
                .forEach { key -> remove(key) }
            putInt(PREF_KEY_VERSION, CUR_VER)
        }
    }

    override fun save(uid: String, role: Role) {
        val key = "role.$uid"
        val raw = role.toFirestore()
        DMLog.i(TAG, "save(uid=$uid, role=$raw)")
        prefs.edit { putString(key, raw) }
    }

    override fun get(uid: String): Role? {
        val key = "role.$uid"
        val raw = prefs.getString(key, null)
        val role = raw?.toRoleOrNull()
        DMLog.d(TAG, "get(uid=$uid) -> $role")
        return role
    }

    override fun clear() {
        DMLog.i(TAG, "clear() — removing all role.* keys")
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith("role.") && it != PREF_KEY_VERSION }
                .forEach { remove(it) }
        }
    }
}

private fun String.toRoleOrNull(): Role? {
    val normalized = lowercase(Locale.ROOT)
    return when (normalized) {
        "admin" -> Role.ADMIN
        "driver" -> Role.DRIVER
        "expat" -> Role.EXPAT
        "expatriate", "expatriat" -> {
            DMLog.d(TAG, "Legacy role alias detected: $this -> expat")
            Role.EXPAT
        }
        else -> null
    }
}
