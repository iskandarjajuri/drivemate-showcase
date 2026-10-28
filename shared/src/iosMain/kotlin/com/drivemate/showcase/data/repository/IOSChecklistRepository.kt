package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSChecklistPayload
import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.ChecklistRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IOSChecklistRepository : ChecklistRepository {
    private val checklistBridge
        get() = IOSPlatformBridgeRegistry.checklistBridge
            ?: error("IOSChecklistPlatformBridge is not installed")

    override suspend fun submitChecklist(checklist: Checklist): AppResult<Unit> =
        suspendCancellableCoroutine { cont ->
            checklistBridge.submitChecklist(checklist.toPayload()) { alreadySubmitted, errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        alreadySubmitted != null -> AppResult.failure(alreadySubmitted)
                        errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                        else -> AppResult.success(Unit)
                    }
                )
            }
        }

    override suspend fun hasChecklist(driverUid: String, dateEpochDay: Int): AppResult<Boolean> =
        suspendCancellableCoroutine { cont ->
            val docId = "${driverUid}_${dateEpochDay}"
            checklistBridge.hasChecklist(docId) { exists, errorMessage ->
                if (!cont.isCompleted) cont.resume(
                    when {
                        errorMessage != null -> AppResult.failure(IllegalStateException(errorMessage))
                        else -> AppResult.success(exists)
                    }
                )
            }
        }
}

private fun Checklist.toPayload() = IOSChecklistPayload(
    hasIssue = hasIssue,
    vehicleId = vehicleId,
    odometerKm = odometerKm,
    fuelLevel = fuelLevel,
    tiresFrontLeftOk = tiresFrontLeftOk,
    tiresFrontRightOk = tiresFrontRightOk,
    tiresRearLeftOk = tiresRearLeftOk,
    tiresRearRightOk = tiresRearRightOk,
    tirePressureNote = tirePressureNote,
    brakesOk = brakesOk,
    lightsOk = lightsOk,
    turnSignalBrakeLightsOk = turnSignalBrakeLightsOk,
    hornOk = hornOk,
    wipersOk = wipersOk,
    mirrorsOk = mirrorsOk,
    seatbeltOk = seatbeltOk,
    engineOilOk = engineOilOk,
    coolantOk = coolantOk,
    batteryOk = batteryOk,
    washerFluidOk = washerFluidOk,
    cleanlinessOk = cleanlinessOk,
    documentsOk = documentsOk,
    fireExtinguisherOk = fireExtinguisherOk,
    firstAidOk = firstAidOk,
    warningTriangleOk = warningTriangleOk,
    notes = notes
)
