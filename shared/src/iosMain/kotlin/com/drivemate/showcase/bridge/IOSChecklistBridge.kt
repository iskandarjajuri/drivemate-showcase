package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val CHECKLIST_ITEMS = listOf(
    "brakesOk" to "ios_checklist_item_brakes",
    "lightsOk" to "ios_checklist_item_lights",
    "turnSignalBrakeLightsOk" to "ios_checklist_item_turn_signal_brake_lights",
    "hornOk" to "ios_checklist_item_horn",
    "wipersOk" to "ios_checklist_item_wipers",
    "mirrorsOk" to "ios_checklist_item_mirrors",
    "seatbeltOk" to "ios_checklist_item_seatbelt",
    "engineOilOk" to "ios_checklist_item_engine_oil",
    "coolantOk" to "ios_checklist_item_coolant",
    "batteryOk" to "ios_checklist_item_battery",
    "washerFluidOk" to "ios_checklist_item_washer_fluid",
    "cleanlinessOk" to "ios_checklist_item_cleanliness",
    "documentsOk" to "ios_checklist_item_documents",
    "fireExtinguisherOk" to "ios_checklist_item_fire_extinguisher",
    "firstAidOk" to "ios_checklist_item_first_aid",
    "warningTriangleOk" to "ios_checklist_item_warning_triangle",
    "tiresFrontLeftOk" to "ios_checklist_item_tires_fl",
    "tiresFrontRightOk" to "ios_checklist_item_tires_fr",
    "tiresRearLeftOk" to "ios_checklist_item_tires_rl",
    "tiresRearRightOk" to "ios_checklist_item_tires_rr",
)

internal class IOSChecklistBridge(
    private val scope: CoroutineScope,
    private val getChecklistStatus: GetChecklistStatusUseCase,
    private val submitChecklistUseCase: SubmitChecklistUseCase,
    private val currentSession: () -> UserSession?
) {
    fun loadTodayChecklist(completion: (IOSChecklistSnapshot?) -> Unit) {
        val session = currentSession() ?: run { completion(null); return }
        val dateEpochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt()
        scope.launch {
            when (val result = getChecklistStatus(session.uid, dateEpochDay)) {
                is AppResult.Success -> {
                    val isSubmitted = result.data
                    val dateLabel = TimeUtil.formatYmdFromEpochDay(dateEpochDay, IOSDriverTimezone)
                    val docId = "${session.uid}_${dateEpochDay}"
                    val snapshot = IOSChecklistSnapshot(
                        id = docId,
                        dateLabel = dateLabel,
                        isSubmitted = isSubmitted,
                        items = CHECKLIST_ITEMS.map { (key, labelKey) ->
                            IOSChecklistItemSnapshot(key = key, label = labelKey)
                        }
                    )
                    completion(snapshot)
                }
                is AppResult.Failure -> completion(null)
            }
        }
    }

    /**
     * Submits today's checklist.
     *
     * [answers] must contain an entry **only** for items the driver actually
     * answered. A missing key maps to `null` on [Checklist], which is the model's
     * "Pending" state — the same tri-state Android records via `TriStateRow`. Do
     * not have the caller default unanswered items to `true`: that writes an "OK"
     * the driver never gave.
     *
     * The five non-boolean fields are part of the same document. They used to be
     * omitted entirely here, so every checklist submitted from iOS persisted
     * `vehicleId`/`odometerKm`/`fuelLevel`/`tirePressureNote`/`notes` as null and
     * the admin detail screen rendered them blank.
     */
    fun submitChecklist(
        answers: Map<String, Boolean>,
        vehicleId: String?,
        odometerKm: Int?,
        fuelLevel: String?,
        tirePressureNote: String?,
        notes: String?,
        // (success, alreadySubmitted, errorMessage) — alreadySubmitted is a real
        // Kotlin-side `is AlreadySubmittedException` check, not a string match;
        // Swift only needs to branch on the boolean, never compare error text.
        completion: (Boolean, Boolean, String?) -> Unit
    ) {
        currentSession() ?: run { completion(false, false, "Not authenticated"); return }
        // Only an explicit "No" is an issue; unanswered stays neutral. Mirrors
        // DriverChecklistViewModel.hasIssue() on Android (`any { it == false }`).
        val hasIssue = answers.values.any { !it }
        val checklist = Checklist(
            vehicleId = vehicleId?.takeIf { it.isNotBlank() },
            odometerKm = odometerKm,
            fuelLevel = fuelLevel?.takeIf { it.isNotBlank() },
            tirePressureNote = tirePressureNote?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            brakesOk = answers["brakesOk"],
            lightsOk = answers["lightsOk"],
            turnSignalBrakeLightsOk = answers["turnSignalBrakeLightsOk"],
            hornOk = answers["hornOk"],
            wipersOk = answers["wipersOk"],
            mirrorsOk = answers["mirrorsOk"],
            seatbeltOk = answers["seatbeltOk"],
            engineOilOk = answers["engineOilOk"],
            coolantOk = answers["coolantOk"],
            batteryOk = answers["batteryOk"],
            washerFluidOk = answers["washerFluidOk"],
            cleanlinessOk = answers["cleanlinessOk"],
            documentsOk = answers["documentsOk"],
            fireExtinguisherOk = answers["fireExtinguisherOk"],
            firstAidOk = answers["firstAidOk"],
            warningTriangleOk = answers["warningTriangleOk"],
            tiresFrontLeftOk = answers["tiresFrontLeftOk"],
            tiresFrontRightOk = answers["tiresFrontRightOk"],
            tiresRearLeftOk = answers["tiresRearLeftOk"],
            tiresRearRightOk = answers["tiresRearRightOk"],
            hasIssue = hasIssue
        )
        scope.launch {
            when (val result = submitChecklistUseCase(checklist)) {
                is AppResult.Success -> completion(true, false, null)
                is AppResult.Failure -> completion(
                    false,
                    result.throwable is AlreadySubmittedException,
                    result.throwable.message ?: "Failed to submit checklist."
                )
            }
        }
    }
}
