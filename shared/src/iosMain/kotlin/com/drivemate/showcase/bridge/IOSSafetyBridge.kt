package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitSafetyDeclarationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSSafetyBridge(
    private val scope: CoroutineScope,
    private val getSafetyStatus: GetSafetyDeclarationStatusUseCase,
    private val submitSafetyDeclarationUseCase: SubmitSafetyDeclarationUseCase,
    private val currentSession: () -> UserSession?
) {
    fun loadTodaySafetyDeclaration(completion: (IOSSafetySnapshot?) -> Unit) {
        val session = currentSession() ?: run { completion(null); return }
        val dateEpochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt()
        scope.launch {
            when (val result = getSafetyStatus(session.uid, dateEpochDay)) {
                is AppResult.Success -> {
                    val isSubmitted = result.data
                    val dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone)
                    completion(
                        IOSSafetySnapshot(
                            dateLabel = dateLabel,
                            isSubmitted = isSubmitted,
                            declaredFit = false,
                            notes = null,
                            submittedAtLabel = null
                        )
                    )
                }
                is AppResult.Failure -> completion(null)
            }
        }
    }

    /**
     * Every attestation is a separate parameter, deliberately.
     *
     * This used to take a single `declaredFit` flag and derive six further attestations
     * from it (`noAlcoholOrDrugs = declaredFit`, …) while hardcoding `noSevereStress = true`.
     * That persisted six safety declarations the driver never actually made — a
     * data-integrity defect, not a UI gap. Do not reintroduce a convenience overload
     * that fills these in: the caller must supply what the driver genuinely checked.
     *
     * `hazardAwareness` and `weatherCondition` are required by Android's own submit
     * validation, so they are non-null here.
     */
    fun submitSafetyDeclaration(
        fitToWork: Boolean,
        noAlcoholOrDrugs: Boolean,
        noSevereStress: Boolean,
        willFollowSpeedLimit: Boolean,
        willFollowRestBreaks: Boolean,
        willNotUsePhoneWhileDriving: Boolean,
        willWearSeatbelt: Boolean,
        confirmed: Boolean,
        sleepHours: Int?,
        hazardAwareness: String,
        weatherCondition: String,
        routeRiskNote: String?,
        incidentReport: String?,
        completion: (Boolean, String?) -> Unit
    ) {
        currentSession() ?: run { completion(false, "Not authenticated"); return }
        val declaration = SafetyDeclaration(
            fitToWork = fitToWork,
            sleepHours = sleepHours,
            noAlcoholOrDrugs = noAlcoholOrDrugs,
            noSevereStress = noSevereStress,
            willFollowSpeedLimit = willFollowSpeedLimit,
            willFollowRestBreaks = willFollowRestBreaks,
            willNotUsePhoneWhileDriving = willNotUsePhoneWhileDriving,
            willWearSeatbelt = willWearSeatbelt,
            hazardAwareness = hazardAwareness.takeIf { it.isNotBlank() },
            weatherCondition = weatherCondition.takeIf { it.isNotBlank() },
            routeRiskNote = routeRiskNote?.takeIf { it.isNotBlank() },
            incidentReport = incidentReport?.takeIf { it.isNotBlank() },
            confirmed = confirmed
        )
        scope.launch {
            when (val result = submitSafetyDeclarationUseCase(declaration)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(
                    false,
                    result.throwable.message ?: "Failed to submit safety declaration."
                )
            }
        }
    }
}
