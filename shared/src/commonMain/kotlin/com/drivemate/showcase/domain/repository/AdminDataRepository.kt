package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.ChecklistDetail
import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.SafetyDetail
import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.checklist.ChecklistFollowUp
import com.drivemate.showcase.domain.workday.WorkdayDecisionStep

interface AdminDataRepository {
    suspend fun getAttendancesByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<AttendanceEntry>>

    suspend fun getChecklistsByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<ChecklistSummary>>

    suspend fun getSafetyByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<SafetySummary>>

    suspend fun getWorkdaysByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<WorkDay>>

    suspend fun getLocations(limit: Int = 200): AppResult<List<Location>>

    suspend fun getAttendanceById(id: String): AppResult<AttendanceEntry?>

    suspend fun getChecklistDetail(id: String): AppResult<ChecklistDetail?>

    suspend fun getSafetyDetail(id: String): AppResult<SafetyDetail?>

    suspend fun getWorkdayById(id: String): AppResult<WorkDay?>

    suspend fun getLocationByUid(uid: String): AppResult<Location?>

    /**
     * Tulis keadaan tindak lanjut temuan checklist (Blok 4.1) — **admin saja**
     * (`firestore.rules` `validChecklistUpdate` = `isAdmin()`).
     *
     * Update PARSIAL: hanya field `followUp*` plus `updatedAt`. Bukan penulisan penuh —
     * dokumen ini milik driver yang mengirimkannya, dan menulis ulang seluruhnya berarti
     * satu bug di pemetaan bisa menghapus 20 hasil pemeriksaan yang tidak boleh disentuh
     * siapa pun setelah dikirim.
     *
     * [followUp] harus berasal dari `evaluateChecklistFollowUp` — jangan menyusunnya
     * sendiri di pemanggil; mesin statusnya ada di sana.
     */
    suspend fun updateChecklistFollowUp(
        id: String,
        followUp: ChecklistFollowUp,
    ): AppResult<Unit>

    /**
     * Mencatat **langkah 2** jejak keputusan satu hari kerja: keputusan admin (Blok 4.2).
     *
     * Menulis HANYA path bertitik `decisionTrail.admin`, jadi langkah 1 (peninjauan
     * supervisor) lestari. `update` pada map bersarang di Firestore mengganti map itu
     * seluruhnya — menulis `decisionTrail` utuh akan menghapus langkah 1, dan
     * `firestore.rules` menolaknya.
     *
     * [step] harus berasal dari `evaluateAdminWorkdayDecision`; urutan dan syaratnya ada
     * di sana, bukan di pemanggil.
     *
     * Tidak menyentuh `status` sama sekali. Ketidaksepakatan admin dengan supervisor lewat
     * jalur lain — `WorkDayRepository.adminOverrideDecision` — yang menuntut alasan
     * override dan memang mengubah keputusan dokumennya.
     */
    suspend fun recordAdminWorkdayDecision(
        workdayId: String,
        step: WorkdayDecisionStep,
    ): AppResult<Unit>
}
