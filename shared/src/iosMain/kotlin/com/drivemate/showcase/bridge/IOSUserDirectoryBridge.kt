package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.admin.matchRecordIds
import com.drivemate.showcase.domain.admin.searchDrivers
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.user.AcceptDriverSopUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkdaySummaryUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSUserDirectoryBridge(
    private val scope: CoroutineScope,
    private val getAllDriverDisplayNames: GetAllDriverDisplayNamesUseCase,
    private val getManagedDrivers: GetManagedDriversUseCase,
    private val getDriverSopStatus: GetDriverSopStatusUseCase,
    private val acceptDriverSop: AcceptDriverSopUseCase,
    private val getWorkdaySummary: GetWorkdaySummaryUseCase,
    private val currentSession: () -> UserSession?
) {
    // Pendaftaran token FCM pindah ke IOSPushTokenBridge (`fcm_tokens`); jalur legacy
    // `users.fcmToken` tidak lagi dipanggil dari sini.

    /**
     * Driver kelolaan expat BESERTA ringkasan reviewnya — padanan `ExpatHomeViewModel` Android.
     *
     * Jendelanya rolling 3 bulan, sama persis dengan Android (`:71`), supaya angka pending di
     * kedua platform menghitung rentang yang sama. Kalau berbeda, dua expat yang melihat driver
     * yang sama akan melihat tunggakan yang berbeda.
     *
     * Kegagalan ringkasan SATU driver tidak menjatuhkan daftar — driver itu tampil dengan
     * angka nol, sama seperti Android (`summary?.pendingCount ?: 0`). Kehilangan seluruh daftar
     * karena satu dokumen bermasalah jauh lebih buruk daripada satu baris yang angkanya kosong.
     */
    fun loadManagedDriverSummaries(
        completion: (List<IOSManagedDriverSummarySnapshot>, IOSExpatSummaryHeaderSnapshot?, String?) -> Unit
    ) {
        val session = currentSession()
        val roleStr = session?.role?.toFirestore()
        if (session == null || roleStr != "expat") {
            completion(emptyList(), null, null)
            return
        }
        val window = TimeUtil.rollingThreeMonthWindowEpochDays(zone = IOSDriverTimezone)
        scope.launch {
            val profiles = when (val res = getManagedDrivers(session.uid)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(emptyList(), null, res.throwable.message ?: "Failed to load drivers.")
                    return@launch
                }
            }
            val summaries = profiles.map { profile ->
                val summary = when (
                    val res = getWorkdaySummary(profile.uid, window.first, window.last)
                ) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                }
                IOSManagedDriverSummarySnapshot(
                    uid = profile.uid,
                    displayName = profile.name?.takeIf { it.isNotBlank() }
                        ?: profile.email
                        ?: profile.uid,
                    email = profile.email,
                    totalDays = summary?.totalDays ?: 0,
                    approvedCount = summary?.approvedCount ?: 0,
                    rejectedCount = summary?.rejectedCount ?: 0,
                    pendingCount = summary?.pendingCount ?: 0,
                    lastWorkdayLabel = summary?.lastEpochDay?.let {
                        TimeUtil.formatYmdFromEpochDay(it, IOSDriverTimezone)
                    }
                )
            // Alfabetis, MENGIKUTI Android (`ExpatHomeViewModel:122`). Catatan di
            // SCREENPARITY yang menyebut Android mengurutkan pending-desc tidak akurat —
            // dicek langsung di kode.
            }.sortedBy { it.displayName.lowercase() }

            completion(
                summaries,
                IOSExpatSummaryHeaderSnapshot(
                    totalDrivers = summaries.size,
                    pendingTotal = summaries.sumOf { it.pendingCount },
                    lastRefreshLabel = TimeUtil.formatHm(TimeUtil.nowEpochMillis(), IOSDriverTimezone)
                ),
                null
            )
        }
    }

    fun loadDriverList(completion: (List<IOSDriverProfileSnapshot>) -> Unit) {
        val session = currentSession()
        val roleStr = session?.role?.toFirestore()
        scope.launch {
            // Expat sees only managed drivers (managerUid == expatUid); admin/driver see all.
            val snapshots = if (session != null && roleStr == "expat") {
                when (val result = getManagedDrivers(session.uid)) {
                    is AppResult.Success -> result.data.map { profile ->
                        IOSDriverProfileSnapshot(
                            uid = profile.uid,
                            displayName = profile.name?.takeIf { it.isNotBlank() }
                                ?: profile.email
                                ?: profile.uid,
                            statusRaw = "active"
                        )
                    }
                    is AppResult.Failure -> emptyList()
                }
            } else {
                when (val result = getAllDriverDisplayNames()) {
                    is AppResult.Success -> result.data.map { (uid, name) ->
                        IOSDriverProfileSnapshot(
                            uid = uid,
                            displayName = name,
                            statusRaw = "active"
                        )
                    }
                    is AppResult.Failure -> emptyList()
                }
            }.sortedBy { it.displayName }
            completion(snapshots)
        }
    }

    fun loadSopStatus(completion: (IOSSopStatusSnapshot?) -> Unit) {
        val session = currentSession() ?: run { completion(null); return }
        loadSopStatusForDriver(session.uid, completion)
    }

    fun loadSopStatusForDriver(uid: String, completion: (IOSSopStatusSnapshot?) -> Unit) {
        scope.launch {
            when (val result = getDriverSopStatus(uid)) {
                is AppResult.Success -> {
                    val sop = result.data
                    val acceptedDateLabel = sop?.sopAcceptedAtMillis
                        ?.takeIf { it > 0L }
                        ?.let { millis ->
                            val epochDay = TimeUtil.epochDayOf(millis, IOSDriverTimezone).toInt()
                            TimeUtil.formatYmdFromEpochDay(epochDay, IOSDriverTimezone)
                        }
                    completion(
                        IOSSopStatusSnapshot(
                            requiresAcceptance = sop?.sopAccepted != true,
                            acceptedDateLabel = acceptedDateLabel
                        )
                    )
                }
                is AppResult.Failure -> completion(null)
            }
        }
    }

    /**
     * Records SOP acceptance for the signed-in driver.
     *
     * Takes no version argument: there is no SOP version in the domain model, and
     * the acceptance record is `sopAccepted` + `sopAcceptedAtMillis` only. The
     * parameter that used to be here was always passed a hardcoded "1.0" and was
     * never read.
     */
    fun acceptSop(completion: (Boolean, String?) -> Unit) {
        val session = currentSession() ?: run { completion(false, null); return }
        val nowMillis = TimeUtil.nowEpochMillis()
        scope.launch {
            when (val result = acceptDriverSop(session.uid, nowMillis)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    /**
     * Cari driver berdasarkan nama atau UID.
     *
     * Sinkron dan bekerja atas roster yang SUDAH dimuat, bukan query Firestore per ketikan:
     * mengetik 6 huruf berarti 6 query, dan admin akan melihat hasil yang berkedip-kedip
     * saat balasan datang tidak berurutan. Android juga mencari atas snapshot lokalnya.
     */
    fun searchDriverRoster(
        query: String,
        recordIds: List<String>,
        completion: (List<IOSAssignmentPersonSnapshot>, List<String>) -> Unit
    ) {
        val session = currentSession()
        if (session == null || session.role?.toFirestore() != "admin") {
            completion(emptyList(), emptyList())
            return
        }
        scope.launch {
            val names = when (val res = getAllDriverDisplayNames()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> emptyMap()
            }
            completion(
                searchDrivers(query, names).map {
                    IOSAssignmentPersonSnapshot(uid = it.uid, displayName = it.displayName)
                },
                // Pencocokan ID atas record yang SUDAH dimuat. Admin yang mem-paste ID
                // hampir selalu menyalinnya dari layar yang sedang dibukanya, jadi
                // recordnya sudah ada — query Firestore baru per ketikan akan menembak
                // berkali-kali untuk kasus yang sudah tertutup.
                matchRecordIds(query, recordIds)
            )
        }
    }
}
