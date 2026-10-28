package com.drivemate.showcase.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.driverSettingsDataStore by preferencesDataStore(name = "driver_settings")

private const val DEFAULT_REMINDER_HOUR = 7
private const val DEFAULT_REMINDER_MINUTE = 0

data class DriverSettingsValues(
    /** null = follow system locale. */
    val languageTag: String? = null,
    val checklistReminderEnabled: Boolean = false,
    val checklistReminderHour: Int = DEFAULT_REMINDER_HOUR,
    val checklistReminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    val tapOutReminderEnabled: Boolean = false,
    val announcementsEnabled: Boolean = true,
    val newMessagesEnabled: Boolean = true
)

interface DriverSettingsPreferences {
    val values: Flow<DriverSettingsValues>
    suspend fun setLanguageTag(tag: String?)
    suspend fun setChecklistReminderEnabled(enabled: Boolean)
    suspend fun setChecklistReminderTime(hour: Int, minute: Int)
    suspend fun setTapOutReminderEnabled(enabled: Boolean)
    suspend fun setAnnouncementsEnabled(enabled: Boolean)
    suspend fun setNewMessagesEnabled(enabled: Boolean)
}

private object Keys {
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    val CHECKLIST_REMINDER_ENABLED = booleanPreferencesKey("checklist_reminder_enabled")
    val CHECKLIST_REMINDER_HOUR = intPreferencesKey("checklist_reminder_hour")
    val CHECKLIST_REMINDER_MINUTE = intPreferencesKey("checklist_reminder_minute")
    val TAP_OUT_REMINDER_ENABLED = booleanPreferencesKey("tap_out_reminder_enabled")
    val ANNOUNCEMENTS_ENABLED = booleanPreferencesKey("announcements_enabled")
    val NEW_MESSAGES_ENABLED = booleanPreferencesKey("new_messages_enabled")
}

class DataStoreDriverSettingsPreferences(private val context: Context) : DriverSettingsPreferences {

    override val values: Flow<DriverSettingsValues> = context.driverSettingsDataStore.data.map { prefs ->
        DriverSettingsValues(
            languageTag = prefs[Keys.LANGUAGE_TAG],
            checklistReminderEnabled = prefs[Keys.CHECKLIST_REMINDER_ENABLED] ?: false,
            checklistReminderHour = prefs[Keys.CHECKLIST_REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR,
            checklistReminderMinute = prefs[Keys.CHECKLIST_REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE,
            tapOutReminderEnabled = prefs[Keys.TAP_OUT_REMINDER_ENABLED] ?: false,
            announcementsEnabled = prefs[Keys.ANNOUNCEMENTS_ENABLED] ?: true,
            newMessagesEnabled = prefs[Keys.NEW_MESSAGES_ENABLED] ?: true
        )
    }

    override suspend fun setLanguageTag(tag: String?) {
        context.driverSettingsDataStore.edit { prefs ->
            if (tag == null) prefs.remove(Keys.LANGUAGE_TAG) else prefs[Keys.LANGUAGE_TAG] = tag
        }
    }

    override suspend fun setChecklistReminderEnabled(enabled: Boolean) {
        context.driverSettingsDataStore.edit { it[Keys.CHECKLIST_REMINDER_ENABLED] = enabled }
    }

    override suspend fun setChecklistReminderTime(hour: Int, minute: Int) {
        context.driverSettingsDataStore.edit {
            it[Keys.CHECKLIST_REMINDER_HOUR] = hour
            it[Keys.CHECKLIST_REMINDER_MINUTE] = minute
        }
    }

    override suspend fun setTapOutReminderEnabled(enabled: Boolean) {
        context.driverSettingsDataStore.edit { it[Keys.TAP_OUT_REMINDER_ENABLED] = enabled }
    }

    override suspend fun setAnnouncementsEnabled(enabled: Boolean) {
        context.driverSettingsDataStore.edit { it[Keys.ANNOUNCEMENTS_ENABLED] = enabled }
    }

    override suspend fun setNewMessagesEnabled(enabled: Boolean) {
        context.driverSettingsDataStore.edit { it[Keys.NEW_MESSAGES_ENABLED] = enabled }
    }
}
