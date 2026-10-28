package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSSafetyPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSSafetyRepository : SafetyRepository {
    private val safetyBridge
        get() = IOSPlatformBridgeRegistry.safetyBridge
            ?: error("IOSSafetyPlatformBridge is not installed")

    override suspend fun submitSafetyDeclaration(payload: SafetyDeclaration): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            safetyBridge.submitSafetyDeclaration(payload.toPayload()) { errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    if (errorMessage == null) AppResult.success(Unit)
                    else AppResult.failure(IllegalStateException(errorMessage))
                )
            }
        }

    override suspend fun hasSafetyDeclaration(driverUid: String, dateEpochDay: Int): AppResult<Boolean> =
        suspendCancellableCoroutine { cont ->
            val docId = "${driverUid}_${dateEpochDay}"
            safetyBridge.hasSafetyDeclaration(docId) { exists, errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                        else -> AppResult.success(exists)
                    }
                )
            }
        }
}

private fun SafetyDeclaration.toPayload() = IOSSafetyPayload(
    fitToWork = fitToWork,
    noAlcoholOrDrugs = noAlcoholOrDrugs,
    noSevereStress = noSevereStress,
    willFollowSpeedLimit = willFollowSpeedLimit,
    willFollowRestBreaks = willFollowRestBreaks,
    willNotUsePhoneWhileDriving = willNotUsePhoneWhileDriving,
    willWearSeatbelt = willWearSeatbelt,
    confirmed = confirmed,
    sleepHours = sleepHours,
    hazardAwareness = hazardAwareness,
    weatherCondition = weatherCondition,
    routeRiskNote = routeRiskNote,
    incidentReport = incidentReport
)
