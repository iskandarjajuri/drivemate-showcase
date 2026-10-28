package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.assignment.GetOverridesForDateUseCase
import com.drivemate.showcase.domain.usecase.assignment.GetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.SetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.UpsertDailyOverrideUseCase
import com.drivemate.showcase.domain.usecase.user.GetChatContactsUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Penugasan expat untuk driver — padanan iOS untuk `AdminDriverAssignmentViewModel` Android.
 *
 * Mencerminkan ViewModel itu, termasuk keputusan yang mudah terlewat kalau ditulis ulang:
 *  - daftar expat diambil lewat `GetChatContactsUseCase(setOf(Role.EXPAT))`, **bukan** dari
 *    roster driver — sumber yang sama dengan Android, jadi kedua platform menawarkan
 *    himpunan orang yang sama;
 *  - `assignedBy` diisi dari sesi yang sedang login, karena `firestore.rules` menuntut
 *    `request.resource.data.assignedBy == request.auth.uid`. Kalau UI yang mengisinya,
 *    nilai salah akan lolos sampai Firestore dan gagal sebagai PERMISSION_DENIED.
 */
internal class IOSDriverAssignmentBridge(
    private val scope: CoroutineScope,
    private val getPermanentManager: GetPermanentManagerUseCase,
    private val setPermanentManager: SetPermanentManagerUseCase,
    private val upsertDailyOverride: UpsertDailyOverrideUseCase,
    private val getOverridesForDate: GetOverridesForDateUseCase,
    private val getAllDriverDisplayNames: GetAllDriverDisplayNamesUseCase,
    private val getChatContacts: GetChatContactsUseCase,
    private val currentSession: () -> UserSession?
) {

    /** Hari ini menurut Asia/Jakarta — state awal pemilih tanggal. */
    fun todayEpochDay(): Int = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt()

    /**
     * Muat daftar driver + expat sekaligus.
     *
     * Satu panggilan, bukan dua, supaya layar tidak pernah berada di keadaan setengah
     * terisi — Android juga memuat keduanya berurutan lalu menerbitkan satu state.
     */
    fun loadOptions(completion: (IOSAssignmentOptionsSnapshot?, String?) -> Unit) {
        val session = currentSession()
        if (session == null || session.role?.toFirestore() != "admin") {
            completion(null, null)
            return
        }
        scope.launch {
            val drivers = when (val res = getAllDriverDisplayNames()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(null, res.throwable.message ?: "Failed to load drivers.")
                    return@launch
                }
            }
            val expats = when (val res = getChatContacts(setOf(Role.EXPAT))) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(null, res.throwable.message ?: "Failed to load expats.")
                    return@launch
                }
            }
            completion(
                IOSAssignmentOptionsSnapshot(
                    drivers = drivers
                        .map { (uid, name) -> IOSAssignmentPersonSnapshot(uid = uid, displayName = name) }
                        .sortedBy { it.displayName.lowercase() },
                    expats = expats
                        .map { IOSAssignmentPersonSnapshot(uid = it.uid, displayName = it.displayName) }
                        .sortedBy { it.displayName.lowercase() }
                ),
                null
            )
        }
    }

    fun loadPermanentManager(driverUid: String, completion: (String?, String?) -> Unit) {
        scope.launch {
            when (val res = getPermanentManager(driverUid)) {
                is AppResult.Success -> completion(res.data, null)
                is AppResult.Failure -> completion(null, res.throwable.message ?: "Failed to load manager.")
            }
        }
    }

    fun savePermanentManager(
        driverUid: String,
        expatUid: String,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val res = setPermanentManager(driverUid, expatUid)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, res.throwable.message ?: "Failed to save manager.")
            }
        }
    }

    fun loadOverrides(
        epochDay: Int,
        completion: (List<IOSDriverAssignmentOverrideSnapshot>?, String?) -> Unit
    ) {
        scope.launch {
            when (val res = getOverridesForDate(epochDay)) {
                is AppResult.Success -> completion(
                    res.data.map {
                        IOSDriverAssignmentOverrideSnapshot(
                            driverUid = it.driverUid,
                            expatUid = it.expatUid,
                            assignedBy = it.assignedBy,
                            reasonRaw = it.reason.toRaw(),
                            epochDay = it.epochDay,
                            dateLabel = TimeUtil.formatYmdFromEpochDay(it.epochDay, IOSDriverTimezone)
                        )
                    },
                    null
                )
                is AppResult.Failure -> completion(null, res.throwable.message ?: "Failed to load assignments.")
            }
        }
    }

    /**
     * Buat/timpa override harian.
     *
     * `assignedBy` TIDAK diterima dari pemanggil — diambil dari sesi. Rules menuntut
     * `assignedBy == request.auth.uid`, jadi membiarkan UI mengisinya cuma menciptakan
     * satu cara lagi untuk gagal di Firestore, bukan fleksibilitas.
     */
    fun createOverride(
        driverUid: String,
        expatUid: String,
        reasonRaw: String,
        epochDay: Int,
        completion: (Boolean, String?) -> Unit
    ) {
        val session = currentSession()
        val assignedBy = session?.uid
        if (assignedBy.isNullOrBlank() || session.role?.toFirestore() != "admin") {
            completion(false, "Only a signed-in admin can assign.")
            return
        }
        if (driverUid == expatUid) {
            completion(false, "SAME_PERSON")
            return
        }
        val reason = AssignmentReason.fromRaw(reasonRaw)
        if (reason == null) {
            completion(false, "Unknown reason: $reasonRaw")
            return
        }
        scope.launch {
            when (val res = upsertDailyOverride(driverUid, expatUid, assignedBy, reason, epochDay)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, res.throwable.message ?: "Failed to save assignment.")
            }
        }
    }
}

data class IOSAssignmentPersonSnapshot(
    val uid: String,
    val displayName: String
)

data class IOSAssignmentOptionsSnapshot(
    val drivers: List<IOSAssignmentPersonSnapshot>,
    val expats: List<IOSAssignmentPersonSnapshot>
)

data class IOSDriverAssignmentOverrideSnapshot(
    val driverUid: String,
    val expatUid: String,
    val assignedBy: String,
    /** Nilai `AssignmentReason`: SICK / LEAVE / OVERTIME_REST / OTHER. */
    val reasonRaw: String,
    val epochDay: Int,
    /** Tanggal sudah diformat di Asia/Jakarta — jangan diformat ulang di Swift. */
    val dateLabel: String
)
