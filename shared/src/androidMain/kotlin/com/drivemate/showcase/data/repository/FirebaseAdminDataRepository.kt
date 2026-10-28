package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.ChecklistDetail
import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.SafetyDetail
import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.parseDeviationFlags
import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.model.resolveAttendanceDailyState
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.util.speedKphFromMps
import com.drivemate.showcase.core.util.speedMpsFromKph
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.checklist.ChecklistFollowUp
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpFields
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus
import com.drivemate.showcase.domain.checklist.buildChecklistFollowUpPayload
import com.drivemate.showcase.domain.checklist.mapChecklistFollowUp
import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep
import com.drivemate.showcase.domain.workday.WorkdayDecisionTrailFields
import com.drivemate.showcase.domain.workday.buildWorkdayDecisionStepPayload
import com.drivemate.showcase.domain.workday.mapWorkdayDecisionTrail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

class FirebaseAdminDataRepository(
    private val fs: FirestoreService
) : AdminDataRepository {

    private companion object { const val TAG = "DM/AdminRepo" }
    private data class AttendanceDailyClock(
        val clockInMillis: Long?,
        val clockOutMillis: Long?
    )

    private val zone = TimeZone.of("Asia/Jakarta")

    private suspend fun getAttendanceDailyClocksByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): Map<String, AttendanceDailyClock> {
        val where = mutableListOf<Pair<String, Any>>(
            "epochDay>=" to startEpochDay,
            "epochDay<=" to endEpochDay
        )
        driverUid?.let { where.add("driverUid" to it) }
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "attendance_daily",
                where = where,
                orderBy = "-epochDay",
                limit = limit
            )
        }
        return docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val epochDay = (m["epochDay"] as? Number)?.toInt() ?: return@mapNotNull null
            "${uid}_$epochDay" to AttendanceDailyClock(
                clockInMillis = (m["clockInMillis"] as? Number)?.toLong(),
                clockOutMillis = (m["clockOutMillis"] as? Number)?.toLong()
            )
        }.toMap()
    }

    private fun resolveClockMillis(primary: Long?, fallback: Long?): Long {
        val primaryResolved = primary?.takeIf { it > 0L }
        if (primaryResolved != null) return primaryResolved
        return fallback?.takeIf { it > 0L } ?: 0L
    }

    private fun resolveComputedMinutes(
        totalMinutes: Int,
        overtimeMinutes: Int,
        clockInMillis: Long,
        clockOutMillis: Long
    ): Pair<Int, Int> {
        val state = resolveAttendanceDailyState(
            clockInMillis = clockInMillis.takeIf { it > 0L },
            clockOutMillis = clockOutMillis.takeIf { it > 0L }
        )
        return if (state == AttendanceDailyState.OPEN) {
            0 to 0
        } else {
            totalMinutes.coerceAtLeast(0) to overtimeMinutes.coerceAtLeast(0)
        }
    }

    override suspend fun getAttendancesByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<AttendanceEntry>> = try {
        val startMillis = TimeUtil.parseHm("00:00", startEpochDay.toLong(), zone)
        val endMillisExclusive = TimeUtil.parseHm("00:00", (endEpochDay + 1).toLong(), zone)
        val where = mutableListOf<Pair<String, Any>>(
            "timestampMillis>=" to startMillis,
            "timestampMillis<" to endMillisExclusive
        )
        driverUid?.let { where.add("driverUid" to it) }

        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "attendances",
                where = where,
                orderBy = "-timestampMillis",
                limit = limit
            )
        }

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val ts = (m["timestampMillis"] as? Number)?.toLong() ?: return@mapNotNull null
            val type = (m["type"] as? String)
                ?: (m["note"] as? String)
                ?: "UNKNOWN"
            val lat = (m["latitude"] as? Number)?.toDouble()
            val lon = (m["longitude"] as? Number)?.toDouble()
            val name = m["locationName"] as? String?

            AttendanceEntry(
                id = d.id,
                driverUid = uid,
                timestampMillis = ts,
                type = type,
                latitude = lat,
                longitude = lon,
                locationName = name
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getAttendancesByRange failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getChecklistsByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<ChecklistSummary>> = try {
        val where = mutableListOf<Pair<String, Any>>(
            "dateEpochDay>=" to startEpochDay,
            "dateEpochDay<=" to endEpochDay
        )
        driverUid?.let { where.add("driverUid" to it) }

        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "vehicle_checklists",
                where = where,
                orderBy = "-dateEpochDay",
                limit = limit
            )
        }

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return@mapNotNull null
            val submittedAt = (m["submittedAt"] as? Number)?.toLong()
                ?: (m["createdAt"] as? Number)?.toLong()
                ?: 0L
            val hasIssue = (m["hasIssue"] as? Boolean) ?: false
            val driverName = m["driverName"] as? String?

            ChecklistSummary(
                id = d.id,
                driverUid = uid,
                driverName = driverName,
                dateEpochDay = day,
                submittedAt = submittedAt,
                hasIssue = hasIssue,
                followUpStatus = ChecklistFollowUpStatus.fromCode(
                    m[ChecklistFollowUpFields.STATUS] as? String
                )
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getChecklistsByRange failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getSafetyByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<SafetySummary>> = try {
        val where = mutableListOf<Pair<String, Any>>(
            "dateEpochDay>=" to startEpochDay,
            "dateEpochDay<=" to endEpochDay
        )
        driverUid?.let { where.add("driverUid" to it) }

        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "safety_declarations",
                where = where,
                orderBy = "-dateEpochDay",
                limit = limit
            )
        }

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return@mapNotNull null
            val submittedAt = (m["submittedAt"] as? Number)?.toLong()
                ?: (m["createdAt"] as? Number)?.toLong()
                ?: 0L
            val fitToWork = (m["fitToWork"] as? Boolean) ?: false
            val confirmed = (m["confirmed"] as? Boolean) ?: false
            val driverName = m["driverName"] as? String?

            SafetySummary(
                id = d.id,
                driverUid = uid,
                driverName = driverName,
                dateEpochDay = day,
                submittedAt = submittedAt,
                fitToWork = fitToWork,
                confirmed = confirmed
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getSafetyByRange failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getWorkdaysByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int
    ): AppResult<List<WorkDay>> = try {
        val where = mutableListOf<Pair<String, Any>>(
            "dateEpochDay>=" to startEpochDay,
            "dateEpochDay<=" to endEpochDay
        )
        driverUid?.let { where.add("driverUid" to it) }
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "workdays",
                where = where,
                orderBy = "-dateEpochDay",
                limit = limit
            )
        }
        val attendanceDailyByDocId = getAttendanceDailyClocksByRange(
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            driverUid = driverUid,
            limit = limit
        )

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return@mapNotNull null
            val attendanceDailyClock = attendanceDailyByDocId["${uid}_$day"]
            val clockIn = resolveClockMillis(
                (m["clockInMillis"] as? Number)?.toLong(),
                attendanceDailyClock?.clockInMillis
            )
            val clockOut = resolveClockMillis(
                (m["clockOutMillis"] as? Number)?.toLong(),
                attendanceDailyClock?.clockOutMillis
            )
            val (total, overtime) = resolveComputedMinutes(
                totalMinutes = (m["totalMinutes"] as? Number)?.toInt() ?: 0,
                overtimeMinutes = (m["overtimeMinutes"] as? Number)?.toInt() ?: 0,
                clockInMillis = clockIn,
                clockOutMillis = clockOut
            )
            val driverName = m["driverName"] as? String?
            val approved = m["approvedByExpat"] as? Boolean?
            val rejectionNote = m["rejectionNote"] as? String?
            val adjustedBy = m["adjustedBy"] as? String?
            val adjustedAt = (m["adjustedAt"] as? Number)?.toLong()
            val adjustedReason = m["adjustedReason"] as? String?
            val hasChecklist = m["hasChecklist"] as? Boolean?
            val checklistSubmittedAt = (m["checklistSubmittedAt"] as? Number)?.toLong()
            val hasSafety = m["hasSafetyDeclaration"] as? Boolean?
            val safetySubmittedAt = (m["safetySubmittedAt"] as? Number)?.toLong()
            val approverUid = m["approverUid"] as? String?
            val approverName = m["approverName"] as? String?
            val status = m["status"] as? String?
            val approvedByUid = m["approvedByUid"] as? String?
            val approvedAtMillis = (m["approvedAtMillis"] as? Number)?.toLong()
            val approvalNote = m["approvalNote"] as? String?
            val rejectedByUid = m["rejectedByUid"] as? String?
            val rejectedAtMillis = (m["rejectedAtMillis"] as? Number)?.toLong()
            val rejectReason = m["rejectReason"] as? String?
            val plannedStart = (m["plannedStartMillis"] as? Number)?.toLong()
            val plannedEnd = (m["plannedEndMillis"] as? Number)?.toLong()
            val planSourceRaw = m["planSource"] as? String?
            val deviationFlagsRaw = (m["deviationFlags"] as? List<*>)?.mapNotNull { it as? String }
            val reviewHold = m["reviewHold"] as? Boolean
            val normalized = normalizeApprovalStatus(
                statusRaw = status,
                approvedByExpat = approved,
                approvedByUid = approvedByUid,
                rejectedByUid = rejectedByUid,
                rejectedAtMillis = rejectedAtMillis,
                rejectReason = rejectReason,
                rejectionNote = rejectionNote
            )

            WorkDay(
                id = d.id,
                driverUid = uid,
                dateEpochDay = day,
                clockInMillis = clockIn,
                clockOutMillis = clockOut,
                totalMinutes = total,
                overtimeMinutes = overtime,
                driverName = driverName,
                approvedByExpat = normalized.approvedByExpat,
                rejectionNote = rejectionNote,
                adjustedBy = adjustedBy,
                adjustedAt = adjustedAt,
                adjustedReason = adjustedReason,
                hasChecklist = hasChecklist,
                checklistSubmittedAt = checklistSubmittedAt,
                hasSafetyDeclaration = hasSafety,
                safetySubmittedAt = safetySubmittedAt,
                approverUid = approverUid,
                approverName = approverName,
                status = normalized.status,
                approvedByUid = approvedByUid,
                approvedAtMillis = approvedAtMillis,
                approvalNote = approvalNote,
                rejectedByUid = rejectedByUid,
                rejectedAtMillis = rejectedAtMillis,
                rejectReason = rejectReason,
                plannedStartMillis = plannedStart,
                plannedEndMillis = plannedEnd,
                planSource = PlanSource.fromRaw(planSourceRaw),
                deviationFlags = parseDeviationFlags(deviationFlagsRaw),
                reviewHold = reviewHold ?: false
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getWorkdaysByRange failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getLocations(limit: Int): AppResult<List<Location>> = try {
        val docs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.query(
                collection = "locations",
                where = emptyList(),
                orderBy = "-updatedAtMillis",
                limit = limit
            )
        }

        val list = docs.mapNotNull { d ->
            val m = d.data ?: return@mapNotNull null
            val uid = m["driverUid"] as? String ?: return@mapNotNull null
            val lat = (m["latitude"] as? Number)?.toDouble()
            val lon = (m["longitude"] as? Number)?.toDouble()
            val speedMps = (m["speedMps"] as? Number)?.toDouble()
            val speedKph = (m["speedKph"] as? Number)?.toDouble()
            val resolvedMps = speedMps ?: speedMpsFromKph(speedKph)
            val resolvedKph = speedKph ?: speedKphFromMps(speedMps)
            val ts = (m["updatedAtMillis"] as? Number)?.toLong() ?: 0L
            val name = m["locationName"] as? String?

            Location(
                driverUid = uid,
                latitude = lat,
                longitude = lon,
                speedMps = resolvedMps,
                speedKph = resolvedKph,
                updatedAtMillis = ts,
                locationName = name
            )
        }
        AppResult.success(list)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getLocations failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getAttendanceById(id: String): AppResult<AttendanceEntry?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("attendances", id)
        } ?: return AppResult.success(null)
        val uid = m["driverUid"] as? String ?: return AppResult.success(null)
        val ts = (m["timestampMillis"] as? Number)?.toLong() ?: return AppResult.success(null)
        val type = (m["type"] as? String)
            ?: (m["note"] as? String)
            ?: "UNKNOWN"
        val lat = (m["latitude"] as? Number)?.toDouble()
        val lon = (m["longitude"] as? Number)?.toDouble()
        val name = m["locationName"] as? String?

        AppResult.success(
            AttendanceEntry(
                id = id,
                driverUid = uid,
                timestampMillis = ts,
                type = type,
                latitude = lat,
                longitude = lon,
                locationName = name
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getAttendanceById failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getChecklistDetail(id: String): AppResult<ChecklistDetail?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("vehicle_checklists", id)
        } ?: return AppResult.success(null)
        val uid = m["driverUid"] as? String ?: return AppResult.success(null)
        val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return AppResult.success(null)
        val submittedAt = (m["submittedAt"] as? Number)?.toLong()
            ?: (m["createdAt"] as? Number)?.toLong()
            ?: 0L
        val driverName = m["driverName"] as? String?
        val driverEmail = m["driverEmail"] as? String?

        val checklist = Checklist(
            vehicleId = m["vehicleId"] as? String,
            odometerKm = (m["odometerKm"] as? Number)?.toInt(),
            fuelLevel = m["fuelLevel"] as? String,
            tiresFrontLeftOk = m["tiresFrontLeftOk"] as? Boolean,
            tiresFrontRightOk = m["tiresFrontRightOk"] as? Boolean,
            tiresRearLeftOk = m["tiresRearLeftOk"] as? Boolean,
            tiresRearRightOk = m["tiresRearRightOk"] as? Boolean,
            tirePressureNote = m["tirePressureNote"] as? String,
            brakesOk = m["brakesOk"] as? Boolean,
            lightsOk = m["lightsOk"] as? Boolean,
            // Dua field ini DITAMBAHKAN PR #225 ke model dan ke `firestore.rules`, tapi
            // pemetaan detail admin tidak pernah ikut diperbarui — jadi dua dari 20 hasil
            // pemeriksaan tidak pernah tampil di layar detail. Diperbaiki di sini karena
            // PR ini justru soal MENINDAKLANJUTI temuan checklist: menutup temuan tanpa
            // melihat dua pemeriksaan adalah persis kegagalan yang mau dicegah.
            turnSignalBrakeLightsOk = m["turnSignalBrakeLightsOk"] as? Boolean,
            washerFluidOk = m["washerFluidOk"] as? Boolean,
            hornOk = m["hornOk"] as? Boolean,
            wipersOk = m["wipersOk"] as? Boolean,
            mirrorsOk = m["mirrorsOk"] as? Boolean,
            seatbeltOk = m["seatbeltOk"] as? Boolean,
            engineOilOk = m["engineOilOk"] as? Boolean,
            coolantOk = m["coolantOk"] as? Boolean,
            batteryOk = m["batteryOk"] as? Boolean,
            cleanlinessOk = m["cleanlinessOk"] as? Boolean,
            documentsOk = m["documentsOk"] as? Boolean,
            fireExtinguisherOk = m["fireExtinguisherOk"] as? Boolean,
            firstAidOk = m["firstAidOk"] as? Boolean,
            warningTriangleOk = m["warningTriangleOk"] as? Boolean,
            notes = m["notes"] as? String,
            hasIssue = (m["hasIssue"] as? Boolean) ?: false
        )

        AppResult.success(
            ChecklistDetail(
                id = id,
                driverUid = uid,
                driverName = driverName,
                driverEmail = driverEmail,
                dateEpochDay = day,
                submittedAt = submittedAt,
                checklist = checklist,
                followUp = mapChecklistFollowUp(m)
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getChecklistDetail failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun updateChecklistFollowUp(
        id: String,
        followUp: ChecklistFollowUp,
    ): AppResult<Unit> = try {
        // Update PARSIAL, dan `merge = true`: dokumen ini milik driver yang
        // mengirimkannya. Penulisan penuh berarti satu bug pemetaan bisa menghapus 20
        // hasil pemeriksaan yang tidak boleh disentuh siapa pun setelah dikirim — dan
        // `firestore.rules` memang mengunci `submittedAt`/`createdAt`/identitas driver.
        val payload = buildChecklistFollowUpPayload(followUp) +
            mapOf("updatedAt" to (followUp.updatedAtMillis ?: 0L))
        DMLog.i(TAG, "updateChecklistFollowUp keys=${payload.keys}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument("vehicle_checklists/$id", payload, merge = true)
        }
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "updateChecklistFollowUp failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun recordAdminWorkdayDecision(
        workdayId: String,
        step: WorkdayDecisionStep,
    ): AppResult<Unit> = try {
        // MAP BERSARANG + `merge = true`, bukan penulisan `decisionTrail` utuh.
        //
        // `SetOptions.merge()` menggabungkan map bersarang alih-alih menggantinya, jadi
        // `decisionTrail.supervisor` — langkah 1, yang beku menurut `firestore.rules` —
        // lestari. Menulis `decisionTrail` sebagai satu nilai utuh akan menghapusnya, dan
        // rules akan menolak write-nya; perilaku merge itu diverifikasi di emulator lewat
        // `firestore.workdays.rules.test.js` ("merge bersarang mempertahankan langkah 1").
        val payload = mapOf(
            WorkdayDecisionTrailFields.TRAIL to mapOf(
                WorkdayDecisionTrailFields.ADMIN to buildWorkdayDecisionStepPayload(step)
            ),
            "updatedAt" to (step.atMillis ?: 0L),
        )
        DMLog.i(TAG, "recordAdminWorkdayDecision decision=${step.decision.code}")
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
            fs.setDocument("workdays/$workdayId", payload, merge = true)
        }
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "recordAdminWorkdayDecision failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getSafetyDetail(id: String): AppResult<SafetyDetail?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("safety_declarations", id)
        } ?: return AppResult.success(null)
        val uid = m["driverUid"] as? String ?: return AppResult.success(null)
        val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return AppResult.success(null)
        val submittedAt = (m["submittedAt"] as? Number)?.toLong()
            ?: (m["createdAt"] as? Number)?.toLong()
            ?: 0L
        val driverName = m["driverName"] as? String?
        val driverEmail = m["driverEmail"] as? String?

        val declaration = SafetyDeclaration(
            fitToWork = (m["fitToWork"] as? Boolean) ?: false,
            sleepHours = (m["sleepHours"] as? Number)?.toInt(),
            noAlcoholOrDrugs = (m["noAlcoholOrDrugs"] as? Boolean) ?: false,
            noSevereStress = (m["noSevereStress"] as? Boolean) ?: false,
            willFollowSpeedLimit = (m["willFollowSpeedLimit"] as? Boolean) ?: false,
            willFollowRestBreaks = (m["willFollowRestBreaks"] as? Boolean) ?: false,
            willNotUsePhoneWhileDriving = (m["willNotUsePhoneWhileDriving"] as? Boolean) ?: false,
            willWearSeatbelt = (m["willWearSeatbelt"] as? Boolean) ?: false,
            hazardAwareness = m["hazardAwareness"] as? String,
            weatherCondition = m["weatherCondition"] as? String,
            routeRiskNote = m["routeRiskNote"] as? String,
            incidentReport = m["incidentReport"] as? String,
            confirmed = (m["confirmed"] as? Boolean) ?: false
        )

        AppResult.success(
            SafetyDetail(
                id = id,
                driverUid = uid,
                driverName = driverName,
                driverEmail = driverEmail,
                dateEpochDay = day,
                submittedAt = submittedAt,
                declaration = declaration
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getSafetyDetail failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getWorkdayById(id: String): AppResult<WorkDay?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("workdays", id)
        } ?: return AppResult.success(null)
        val uid = m["driverUid"] as? String ?: return AppResult.success(null)
        val day = (m["dateEpochDay"] as? Number)?.toInt() ?: return AppResult.success(null)
        val clockIn = (m["clockInMillis"] as? Number)?.toLong() ?: 0L
        val clockOut = (m["clockOutMillis"] as? Number)?.toLong() ?: 0L
        val total = (m["totalMinutes"] as? Number)?.toInt() ?: 0
        val overtime = (m["overtimeMinutes"] as? Number)?.toInt() ?: 0
        val driverName = m["driverName"] as? String?
        val approved = m["approvedByExpat"] as? Boolean?
        val rejectionNote = m["rejectionNote"] as? String?
        val adjustedBy = m["adjustedBy"] as? String?
        val adjustedAt = (m["adjustedAt"] as? Number)?.toLong()
        val adjustedReason = m["adjustedReason"] as? String?
        val hasChecklist = m["hasChecklist"] as? Boolean?
        val checklistSubmittedAt = (m["checklistSubmittedAt"] as? Number)?.toLong()
        val hasSafety = m["hasSafetyDeclaration"] as? Boolean?
        val safetySubmittedAt = (m["safetySubmittedAt"] as? Number)?.toLong()
        val approverUid = m["approverUid"] as? String?
        val approverName = m["approverName"] as? String?
        val status = m["status"] as? String?
        val approvedByUid = m["approvedByUid"] as? String?
        val approvedAtMillis = (m["approvedAtMillis"] as? Number)?.toLong()
        val approvalNote = m["approvalNote"] as? String?
        val rejectedByUid = m["rejectedByUid"] as? String?
        val rejectedAtMillis = (m["rejectedAtMillis"] as? Number)?.toLong()
        val rejectReason = m["rejectReason"] as? String?
        val normalized = normalizeApprovalStatus(
            statusRaw = status,
            approvedByExpat = approved,
            approvedByUid = approvedByUid,
            rejectedByUid = rejectedByUid,
            rejectedAtMillis = rejectedAtMillis,
            rejectReason = rejectReason,
            rejectionNote = rejectionNote
        )

        AppResult.success(
            WorkDay(
                id = id,
                driverUid = uid,
                dateEpochDay = day,
                clockInMillis = clockIn,
                clockOutMillis = clockOut,
                totalMinutes = total,
                overtimeMinutes = overtime,
                driverName = driverName,
                approvedByExpat = normalized.approvedByExpat,
                rejectionNote = rejectionNote,
                adjustedBy = adjustedBy,
                adjustedAt = adjustedAt,
                adjustedReason = adjustedReason,
                hasChecklist = hasChecklist,
                checklistSubmittedAt = checklistSubmittedAt,
                hasSafetyDeclaration = hasSafety,
                safetySubmittedAt = safetySubmittedAt,
                approverUid = approverUid,
                approverName = approverName,
                status = normalized.status,
                approvedByUid = approvedByUid,
                approvedAtMillis = approvedAtMillis,
                approvalNote = approvalNote,
                rejectedByUid = rejectedByUid,
                rejectedAtMillis = rejectedAtMillis,
                rejectReason = rejectReason,
                decisionTrail = mapWorkdayDecisionTrail(m["decisionTrail"] as? Map<*, *>)
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getWorkdayById failed: ${t.message}", t)
        AppResult.failure(t)
    }

    override suspend fun getLocationByUid(uid: String): AppResult<Location?> = try {
        val m = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            fs.getDocumentMap("locations", uid)
        } ?: return AppResult.success(null)
        val lat = (m["latitude"] as? Number)?.toDouble()
        val lon = (m["longitude"] as? Number)?.toDouble()
        val speedMps = (m["speedMps"] as? Number)?.toDouble()
        val speedKph = (m["speedKph"] as? Number)?.toDouble()
        val resolvedMps = speedMps ?: speedMpsFromKph(speedKph)
        val resolvedKph = speedKph ?: speedKphFromMps(speedMps)
        val ts = (m["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        val name = m["locationName"] as? String?
        val driverUid = (m["driverUid"] as? String) ?: uid
        AppResult.success(
            Location(
                driverUid = driverUid,
                latitude = lat,
                longitude = lon,
                speedMps = resolvedMps,
                speedKph = resolvedKph,
                updatedAtMillis = ts,
                locationName = name
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.e(TAG, "getLocationByUid failed: ${t.message}", t)
        AppResult.failure(t)
    }
}
