package com.drivemate.showcase.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.drivemate.showcase.core.model.TrackingStatus
import com.drivemate.showcase.data.dataStore
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PersistedTrackingStatusRepository(
    context: Context
) : TrackingStatusRepository {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val offDutyPrivacyKey = booleanPreferencesKey("tracking_hide_location_when_off_duty")

    // Seeded with the type's own default (hideLocationWhenOffDuty = false) instead of
    // blocking construction on a DataStore read. The init block below corrects this
    // reactively via the same distinctUntilChanged/collect path it already uses for
    // every later change, so callers never depended on the first emission being exact.
    private val _status = MutableStateFlow(TrackingStatus())
    override val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    init {
        scope.launch {
            appContext.dataStore.data
                .map { prefs -> prefs[offDutyPrivacyKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    _status.update { current ->
                        if (current.hideLocationWhenOffDuty == enabled) current
                        else current.copy(hideLocationWhenOffDuty = enabled)
                    }
                }
        }
    }

    override fun update(status: TrackingStatus) {
        val previous = _status.value
        _status.value = status
        if (previous.hideLocationWhenOffDuty != status.hideLocationWhenOffDuty) {
            scope.launch {
                appContext.dataStore.edit { prefs ->
                    prefs[offDutyPrivacyKey] = status.hideLocationWhenOffDuty
                }
            }
        }
    }
}
