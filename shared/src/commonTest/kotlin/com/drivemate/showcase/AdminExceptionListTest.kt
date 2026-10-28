package com.drivemate.showcase

import com.drivemate.showcase.domain.admin.AdminExceptionInputs
import com.drivemate.showcase.domain.admin.AdminExceptionKind
import com.drivemate.showcase.domain.admin.NOT_CLOCKED_IN_GRACE_MINUTES
import com.drivemate.showcase.domain.admin.PendingWorkday
import com.drivemate.showcase.domain.admin.WORKDAY_PENDING_MAX_DAYS
import com.drivemate.showcase.domain.admin.buildAdminExceptionList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.drivemate.showcase.domain.admin.OvertimeLimitScope
import com.drivemate.showcase.domain.admin.OvertimeLimits

/** Blok 2.2 — daftar pengecualian yang bisa ditindak (A2 pada PRD §7). */
class AdminExceptionListTest {

    private val today = 20_703
    private val scheduleStart = (today * 24L * 60L + 7 * 60 + 30 - 7 * 60) * 60_000L
    private val names = mapOf("d1" to "Budi", "d2" to "Cahya", "d3" to "Dewi")

    @Test
    fun emptyInputsGiveEmptyList() {
        // Daftar kosong adalah hasil yang SAH dan sering. UI-nya menampilkan
        // "Semua beres hari ini"; fungsi ini cukup mengembalikan kosong.
        val out = buildAdminExceptionList(AdminExceptionInputs(), today, scheduleStart)
        assertTrue(out.isEmpty())
    }

    @Test
    fun severityOrderFollowsEnumDeclaration() {
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                scheduledDriverUids = setOf("d1", "d2", "d3"),
                driverNames = names,
                attendanceInUids = setOf("d1", "d2"),          // d3 belum absen
                checklistIssues = mapOf("d1" to "cl1"),
                safetyNotFit = mapOf("d2" to "sd1"),
                sopNotAcceptedUids = setOf("d1"),
                scheduleStartMillis = scheduleStart,
            ),
            todayEpochDay = today,
            nowMillis = scheduleStart + 60 * 60_000L,           // satu jam setelah jadwal
        )
        assertEquals(
            listOf(
                AdminExceptionKind.SAFETY_NOT_FIT,
                AdminExceptionKind.CHECKLIST_ISSUE,
                AdminExceptionKind.NOT_CLOCKED_IN,
                AdminExceptionKind.SOP_NOT_ACCEPTED,
            ),
            out.map { it.kind },
        )
    }

    @Test
    fun notClockedInIsSilentDuringGracePeriod() {
        // Tanpa penjaga ini daftar akan penuh setiap pagi pukul 07:31 dengan orang yang
        // sedang di jalan, dan admin belajar mengabaikan daftarnya.
        val inputs = AdminExceptionInputs(
            scheduledDriverUids = setOf("d1"),
            driverNames = names,
            attendanceInUids = emptySet(),
            scheduleStartMillis = scheduleStart,
        )
        val diamSelama = buildAdminExceptionList(
            inputs, today, scheduleStart + (NOT_CLOCKED_IN_GRACE_MINUTES - 1) * 60_000L,
        )
        assertTrue(diamSelama.isEmpty(), "masih dalam masa tenggang")

        val setelahnya = buildAdminExceptionList(
            inputs, today, scheduleStart + NOT_CLOCKED_IN_GRACE_MINUTES * 60_000L,
        )
        assertEquals(1, setelahnya.size)
        assertEquals(AdminExceptionKind.NOT_CLOCKED_IN, setelahnya[0].kind)
    }

    @Test
    fun notClockedInDoesNotApplyOnNonWorkday() {
        // `scheduleStartMillis = null` berarti hari ini bukan hari kerja. Pemicu ini lalu
        // TIDAK berlaku sama sekali — bukan berlaku dengan ambang default. Kalau tidak,
        // setiap hari libur akan melaporkan seluruh armada "belum absen".
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                scheduledDriverUids = setOf("d1", "d2", "d3"),
                driverNames = names,
                scheduleStartMillis = null,
            ),
            today, scheduleStart + 12 * 60 * 60_000L,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun pendingWorkdayOnlyAfterThreshold() {
        val inputs = { daysAgo: Int ->
            AdminExceptionInputs(
                driverNames = names,
                pendingWorkdays = listOf(PendingWorkday("w1", "d1", today - daysAgo)),
            )
        }
        assertTrue(
            buildAdminExceptionList(inputs(WORKDAY_PENDING_MAX_DAYS), today, scheduleStart).isEmpty(),
            "tepat di ambang belum 'terlalu lama'",
        )
        val lewat = buildAdminExceptionList(inputs(WORKDAY_PENDING_MAX_DAYS + 1), today, scheduleStart)
        assertEquals(1, lewat.size)
        assertEquals(AdminExceptionKind.WORKDAY_PENDING_TOO_LONG, lewat[0].kind)
        assertEquals(WORKDAY_PENDING_MAX_DAYS + 1, lewat[0].magnitude)
    }

    @Test
    fun everyRowCarriesDriverNameNotUid() {
        // PRD §7.A2 menuntut nama. Alasannya bukan kosmetik: admin tidak mengenali UID, jadi
        // baris ber-UID memaksa satu langkah pencarian pada layar yang ada untuk menghapusnya.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                safetyNotFit = mapOf("d2" to "sd1"),
                checklistIssues = mapOf("d1" to "cl1"),
            ),
            today, scheduleStart,
        )
        assertEquals(listOf("Cahya", "Budi"), out.map { it.driverName })
    }

    @Test
    fun unknownNameStaysNullNeverFallsBackToUid() {
        // Nama belum termuat -> null, supaya UI bisa menampilkan penanda. Mengembalikan UID
        // di sini akan membuat UID muncul di layar lewat pintu belakang.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(driverNames = emptyMap(), safetyNotFit = mapOf("dX" to "sd1")),
            today, scheduleStart,
        )
        assertEquals(null, out[0].driverName)
        assertEquals("dX", out[0].driverUid)
    }

    @Test
    fun rowsWithoutTimestampSortAfterTimedOnes() {
        // "Tidak diketahui kapan" bukan "baru saja". Menaruhnya di atas akan mendorong
        // kejadian nyata ke bawah daftar.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                scheduledDriverUids = setOf("d1", "d2"),
                driverNames = names,
                attendanceInUids = emptySet(),
                sopNotAcceptedUids = setOf("d3"),
                scheduleStartMillis = scheduleStart,
            ),
            today, scheduleStart + 60 * 60_000L,
        )
        val timed = out.indexOfFirst { it.occurredAtMillis != null }
        val untimed = out.indexOfFirst { it.occurredAtMillis == null }
        assertTrue(timed < untimed, "berwaktu harus di atas yang tanpa waktu")
    }

    @Test
    fun severityEnumOrderIsTheOnlyPriorityTable() {
        // Kalau ada tabel prioritas kedua di UI, keduanya bisa berbeda pendapat. Urutan
        // deklarasi enum inilah satu-satunya sumber, dan test ini memakunya.
        assertEquals(
            listOf(
                "SAFETY_NOT_FIT", "OVERTIME_VIOLATION", "SPEED_VIOLATION", "CHECKLIST_ISSUE",
                "OVERTIME_WARNING", "NOT_CLOCKED_IN", "WORKDAY_PENDING_TOO_LONG",
                "SOP_NOT_ACCEPTED",
            ),
            AdminExceptionKind.entries.map { it.name },
        )
    }

    // MARK: - Pemicu batas lembur (B1), dihidupkan Blok 3.1

    @Test
    fun overtimeViolationOutranksEverythingButNotFit() {
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                checklistIssues = mapOf("d1" to "cl1"),
                safetyNotFit = mapOf("d2" to "sd1"),
                dailyOvertimeByDriver = mapOf("d3" to 5 * 60),   // 300 mnt > 240
            ),
            today, scheduleStart,
        )
        assertEquals(
            listOf(
                AdminExceptionKind.SAFETY_NOT_FIT,
                AdminExceptionKind.OVERTIME_VIOLATION,
                AdminExceptionKind.CHECKLIST_ISSUE,
            ),
            out.map { it.kind },
        )
    }

    @Test
    fun overtimeRowCarriesBothActualAndLimit() {
        // PRD §7.B1: "keduanya menyebut angka dan batasnya, bukan cuma label warna".
        val row = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("d1" to 3 * 60 + 10),
            ),
            today, scheduleStart,
        ).single()
        assertEquals(AdminExceptionKind.OVERTIME_WARNING, row.kind)
        assertEquals(190, row.magnitude)
        assertEquals(240, row.limitMinutes)
        assertEquals(OvertimeLimitScope.DAILY, row.scope)
    }

    @Test
    fun okOvertimeProducesNoRow() {
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("d1" to 60),
                weeklyOvertimeByDriver = mapOf("d1" to 120),
            ),
            today, scheduleStart,
        )
        assertTrue(out.isEmpty(), "lembur di bawah ambang bukan pengecualian")
    }

    @Test
    fun dailyAndWeeklyBothSurfaceForSameDriver() {
        // Menyembunyikan salah satunya akan menyembunyikan pelanggaran yang berbeda jenisnya.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("d1" to 5 * 60),      // pelanggaran harian
                weeklyOvertimeByDriver = mapOf("d1" to 14 * 60),    // peringatan pekanan
            ),
            today, scheduleStart,
        )
        assertEquals(2, out.size)
        assertEquals(AdminExceptionKind.OVERTIME_VIOLATION, out[0].kind)
        assertEquals(OvertimeLimitScope.DAILY, out[0].scope)
        assertEquals(AdminExceptionKind.OVERTIME_WARNING, out[1].kind)
        assertEquals(OvertimeLimitScope.WEEKLY, out[1].scope)
    }

    @Test
    fun stricterCompanyLimitTriggersEarlier() {
        // Batas 2 jam: lembur 2j10m yang tadinya OK menjadi pelanggaran.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("d1" to 130),
                overtimeLimits = OvertimeLimits(dailyMaxMinutes = 120),
            ),
            today, scheduleStart,
        )
        assertEquals(AdminExceptionKind.OVERTIME_VIOLATION, out.first().kind)
        assertEquals(120, out.first().limitMinutes)
    }

    @Test
    fun overtimeOfUnknownDriverIsNotSurfaced() {
        // Jendela lembur membentang tujuh hari, jadi ia bisa memuat driver yang sudah
        // dinonaktifkan. Tanpa penyaring ini ia muncul sebagai baris "driver tidak dikenal"
        // dengan angka pelanggaran yang tidak bisa ditindak siapa pun.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("dNonaktif" to 300),
                weeklyOvertimeByDriver = mapOf("dNonaktif" to 1500),
            ),
            today, scheduleStart,
        )
        assertTrue(out.isEmpty(), "driver di luar daftar nama tidak boleh memunculkan baris")
    }

    @Test
    fun knownDriverStillSurfacesWhenNotScheduledToday() {
        // Kebalikan dari test di atas, dan ini yang membuat penyaringnya BUKAN "terjadwal
        // hari ini": d1 tidak ada di `scheduledDriverUids`, tapi lembur pekanannya melampaui
        // batas. Justru di hari liburnya admin masih bisa mengubah jadwal besok.
        val row = buildAdminExceptionList(
            AdminExceptionInputs(
                scheduledDriverUids = setOf("d2"),
                driverNames = names,
                attendanceInUids = setOf("d2"),
                weeklyOvertimeByDriver = mapOf("d1" to 19 * 60),
            ),
            today, scheduleStart,
        ).single()
        assertEquals(AdminExceptionKind.OVERTIME_VIOLATION, row.kind)
        assertEquals("d1", row.driverUid)
        assertEquals(OvertimeLimitScope.WEEKLY, row.scope)
    }

    @Test
    fun overtimeRowsCarryRecordIdForBothScopes() {
        // A2: satu ketukan sampai ke record. Kedua cakupan memakai catatan yang sama —
        // hari kerja terakhir dalam jendela — karena keduanya menjawab "lembur mana ini?".
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                dailyOvertimeByDriver = mapOf("d1" to 250),
                weeklyOvertimeByDriver = mapOf("d1" to 1100),
                overtimeRecordIdByDriver = mapOf("d1" to "wd-terakhir"),
            ),
            today, scheduleStart,
        )
        assertEquals(2, out.size)
        assertTrue(out.all { it.recordId == "wd-terakhir" })
        assertEquals(
            setOf(OvertimeLimitScope.DAILY, OvertimeLimitScope.WEEKLY),
            out.map { it.scope }.toSet(),
        )
    }

    @Test
    fun oneDriverCanAppearInSeveralRows() {
        // Satu orang bisa punya beberapa masalah sekaligus, dan menggabungkannya jadi satu
        // baris akan menyembunyikan salah satunya.
        val out = buildAdminExceptionList(
            AdminExceptionInputs(
                driverNames = names,
                safetyNotFit = mapOf("d1" to "sd1"),
                checklistIssues = mapOf("d1" to "cl1"),
                sopNotAcceptedUids = setOf("d1"),
            ),
            today, scheduleStart,
        )
        assertEquals(3, out.size)
        assertTrue(out.all { it.driverUid == "d1" })
    }
}
