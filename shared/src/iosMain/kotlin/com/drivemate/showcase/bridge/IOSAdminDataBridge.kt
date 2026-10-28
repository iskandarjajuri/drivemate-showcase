package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.admin.countAdminOverview
import com.drivemate.showcase.domain.admin.dayOverDayTrendPercent
import com.drivemate.showcase.domain.admin.AdminActivityEvent
import com.drivemate.showcase.domain.admin.AdminActivityType
import com.drivemate.showcase.domain.admin.adminActivityTypeFromAttendance
import com.drivemate.showcase.domain.admin.buildAdminActivityFeed
import com.drivemate.showcase.domain.admin.computeAdminLiveMetrics
import com.drivemate.showcase.domain.admin.AdminAttentionCounts
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.CsvWriter
import com.drivemate.showcase.domain.usecase.admin.GetAdminAttendanceDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdaysUseCase
import com.drivemate.showcase.domain.admin.AdminComplianceCards
import com.drivemate.showcase.domain.admin.AdminComplianceState
import com.drivemate.showcase.domain.admin.ComplianceRatio
import com.drivemate.showcase.domain.admin.computeAdminComplianceCards
import com.drivemate.showcase.domain.admin.AdminException
import com.drivemate.showcase.domain.admin.AdminExceptionInputs
import com.drivemate.showcase.domain.admin.PendingWorkday
import com.drivemate.showcase.domain.admin.buildAdminExceptionList
import com.drivemate.showcase.domain.calendar.DayCalendarKind
import com.drivemate.showcase.domain.calendar.resolveDayCalendarKind
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.checklist.ChecklistFollowUpStatus
import com.drivemate.showcase.domain.checklist.ChecklistIssueCandidate
import com.drivemate.showcase.domain.checklist.outstandingChecklistIssues
import com.drivemate.showcase.domain.usecase.admin.GetDriverSopStatusesUseCase
import com.drivemate.showcase.domain.usecase.admin.RecordAdminWorkdayDecisionUseCase
import com.drivemate.showcase.domain.usecase.admin.UpdateChecklistFollowUpUseCase
import com.drivemate.showcase.domain.workday.WorkdayDecision
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetAttendanceByRangeUseCase
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSAdminDataBridge(
    private val scope: CoroutineScope,
    private val getAttendances: GetAttendanceByRangeUseCase,
    private val getChecklists: GetAdminChecklistsUseCase,
    private val getSafety: GetAdminSafetyUseCase,
    private val getWorkdays: GetAdminWorkdaysUseCase,
    private val getLocations: GetAdminLocationsUseCase,
    private val getAttendanceDetail: GetAdminAttendanceDetailUseCase,
    private val getChecklistDetail: GetAdminChecklistDetailUseCase,
    private val getSafetyDetail: GetAdminSafetyDetailUseCase,
    private val getWorkdayDetail: GetAdminWorkdayDetailUseCase,
    private val getLocationDetail: GetAdminLocationDetailUseCase,
    private val getSopStatuses: GetDriverSopStatusesUseCase,
    private val getAllDriverNames: GetAllDriverDisplayNamesUseCase,
    private val getWorkCalendarYear: GetWorkCalendarYearUseCase,
    private val recordDecision: RecordAdminWorkdayDecisionUseCase,
    private val updateChecklistFollowUp: UpdateChecklistFollowUpUseCase
) {
    private companion object {
        const val DEFAULT_LIMIT = 200

        /**
         * Batas khusus export, jauh di atas batas tampilan.
         *
         * Memakai DEFAULT_LIMIT untuk export berarti file yang dihasilkan diam-diam terpotong
         * di baris ke-200 — dan CSV yang terpotong TIDAK terlihat rusak: penerimanya membuka
         * file yang tampak lengkap dan mengambil keputusan dari data yang tidak utuh. Itu
         * lebih buruk daripada tidak ada tombol export sama sekali.
         */
        const val EXPORT_LIMIT = 5_000
    }

    fun loadAttendances(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminAttendanceSnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getAttendances(startEpochDay, endEpochDay, null, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadChecklists(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminChecklistSummarySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getChecklists(startEpochDay, endEpochDay, null, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadSafety(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminSafetySummarySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getSafety(startEpochDay, endEpochDay, null, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadWorkdays(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getWorkdays(startEpochDay, endEpochDay, null, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadLocations(
        completion: (List<IOSAdminLocationSnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getLocations(DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadAttendancesForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminAttendanceSnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getAttendances(startEpochDay, endEpochDay, driverUid, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    /**
     * Pindahkan status tindak lanjut temuan checklist (Blok 4.1) — mesin status dievaluasi
     * [UpdateChecklistFollowUpUseCase] commonMain, SAMA dengan Android.
     *
     * @param completion (outcomeCode, errorDetail): "saved" / "invalid" + nama
     *   `ChecklistFollowUpError` / "write_failed" + pesan.
     */
    fun saveChecklistFollowUp(
        checklistId: String,
        targetCode: String,
        note: String?,
        actorUid: String,
        nowMillis: Long,
        completion: (String?, String?) -> Unit
    ) {
        scope.launch {
            val target = ChecklistFollowUpStatus.fromCode(targetCode)
            val detail = when (val res = getChecklistDetail(checklistId)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion("write_failed", res.throwable.message ?: "Failed to load checklist.")
                    return@launch
                }
            }
            if (detail == null) {
                completion("write_failed", "Checklist not found.")
                return@launch
            }
            when (
                val outcome = updateChecklistFollowUp(
                    checklistId, detail.followUp, target, null, note, actorUid, nowMillis
                )
            ) {
                is UpdateChecklistFollowUpUseCase.Outcome.Saved -> completion("saved", null)
                is UpdateChecklistFollowUpUseCase.Outcome.Invalid ->
                    completion("invalid", outcome.rejected.error.name)
                is UpdateChecklistFollowUpUseCase.Outcome.WriteFailed ->
                    completion("write_failed", outcome.throwable.message)
            }
        }
    }

    fun loadChecklistsForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminChecklistSummarySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getChecklists(startEpochDay, endEpochDay, driverUid, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadSafetyForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminSafetySummarySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getSafety(startEpochDay, endEpochDay, driverUid, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosAdminSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    /**
     * Catat langkah 2 jejak keputusan (Blok 4.2) — validasi SAMA dengan Android lewat
     * [RecordAdminWorkdayDecisionUseCase] commonMain, jadi penolakan yang bisa dijelaskan
     * dalam bahasa manusia identik lintas platform (rules memeriksa hal yang sama di server).
     *
     * @param completion (outcomeCode, errorDetail): "saved" / "invalid" + nama
     *   `WorkdayAdminDecisionError` / "write_failed" + pesan.
     */
    fun recordAdminWorkdayDecision(
        workdayId: String,
        targetCode: String,
        note: String?,
        actorUid: String,
        nowMillis: Long,
        completion: (String?, String?) -> Unit
    ) {
        scope.launch {
            val target = WorkdayDecision.fromCode(targetCode)
            if (target == null) {
                completion("invalid", "UNKNOWN_TARGET")
                return@launch
            }
            val workday = when (val res = getWorkdayDetail(workdayId)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion("write_failed", res.throwable.message ?: "Failed to load workday.")
                    return@launch
                }
            }
            if (workday == null) {
                completion("write_failed", "Workday not found.")
                return@launch
            }
            when (val outcome = recordDecision(workday, target, note, actorUid, nowMillis)) {
                is RecordAdminWorkdayDecisionUseCase.Outcome.Saved -> completion("saved", null)
                is RecordAdminWorkdayDecisionUseCase.Outcome.Invalid ->
                    completion("invalid", outcome.rejected.error.name)
                is RecordAdminWorkdayDecisionUseCase.Outcome.WriteFailed ->
                    completion("write_failed", outcome.throwable.message)
            }
        }
    }

    fun loadWorkdaysForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getWorkdays(startEpochDay, endEpochDay, driverUid, DEFAULT_LIMIT)) {
                is AppResult.Success -> completion(result.data.map { it.toIosSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message)
            }
        }
    }

    fun loadAttendanceById(
        id: String,
        completion: (IOSAdminAttendanceSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getAttendanceDetail(id)) {
                is AppResult.Success -> completion(result.data?.toIosAdminSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun loadChecklistDetail(
        id: String,
        completion: (IOSAdminChecklistDetailSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getChecklistDetail(id)) {
                is AppResult.Success -> completion(result.data?.toIosAdminDetailSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun loadSafetyDetail(
        id: String,
        completion: (IOSAdminSafetyDetailSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getSafetyDetail(id)) {
                is AppResult.Success -> completion(result.data?.toIosAdminDetailSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun loadWorkdayById(
        id: String,
        completion: (IOSAdminWorkdayDetailSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getWorkdayDetail(id)) {
                is AppResult.Success -> completion(result.data?.toIosAdminDetailSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    fun loadLocationByUid(
        uid: String,
        completion: (IOSAdminLocationSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getLocationDetail(uid)) {
                is AppResult.Success -> completion(result.data?.toIosAdminSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message)
            }
        }
    }

    // MARK: Export CSV (padanan ExportCsvAction Android)

    /**
     * Rakit CSV absensi untuk rentang yang sedang dilihat admin.
     *
     * Memakai `CsvWriter.attendanceToCsv` — fungsi yang SAMA dipakai Android, jadi kolom,
     * format tanggal Asia/Jakarta, dan **masking UID driver** identik. Merakit CSV di Swift
     * akan menggandakan aturan escaping dan masking PII di tempat yang tidak teruji.
     *
     * `nameResolver` sengaja TIDAK diteruskan: default-nya memask UID, dan itu perilaku yang
     * benar untuk file yang akan dibagikan keluar.
     */
    fun exportAttendanceCsv(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (String?, String?) -> Unit
    ) {
        scope.launch {
            when (val res = getAttendances(startEpochDay, endEpochDay, null, EXPORT_LIMIT)) {
                is AppResult.Success -> completion(CsvWriter.attendanceToCsv(res.data), null)
                is AppResult.Failure ->
                    completion(null, res.throwable.message ?: "Failed to export attendance.")
            }
        }
    }

    /** CSV workday untuk admin. Memask UID driver DAN approver. */
    fun exportWorkdaysCsv(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (String?, String?) -> Unit
    ) {
        scope.launch {
            when (val res = getWorkdays(startEpochDay, endEpochDay, null, EXPORT_LIMIT)) {
                is AppResult.Success -> completion(CsvWriter.workDaysToAdminCsv(res.data), null)
                is AppResult.Failure ->
                    completion(null, res.throwable.message ?: "Failed to export workdays.")
            }
        }
    }

    // MARK: Metrik live Admin Home

    /**
     * Hitung metrik "perlu perhatian" dari lokasi yang sedang diamati.
     *
     * Lokasi diberikan pemanggil (Swift sudah memegangnya lewat observer real-time) alih-alih
     * dibaca ulang di sini: membaca ulang berarti dua sumber kebenaran untuk hal yang sama,
     * dan angka di kartu bisa berbeda dari titik yang tampak di peta pada layar yang sama.
     */
    fun computeLiveMetrics(
        driverUids: List<String>,
        latitudes: List<Double>,
        longitudes: List<Double>,
        updatedAtMillis: List<Long>,
        openWorkdayCount: Int,
        checklistMissingToday: Int,
        safetyMissingToday: Int,
        sopPendingCount: Int
    ): IOSAdminLiveMetricsSnapshot {
        val n = minOf(driverUids.size, latitudes.size, longitudes.size, updatedAtMillis.size)
        val locations = (0 until n).map { i ->
            Location(
                driverUid = driverUids[i],
                latitude = latitudes[i],
                longitude = longitudes[i],
                updatedAtMillis = updatedAtMillis[i]
            )
        }
        val metrics = computeAdminLiveMetrics(
            locations = locations,
            attention = AdminAttentionCounts(
                openWorkdayCount = openWorkdayCount,
                checklistMissingToday = checklistMissingToday,
                safetyMissingToday = safetyMissingToday,
                sopPendingCount = sopPendingCount
            ),
            nowMillis = TimeUtil.nowEpochMillis()
        )
        return IOSAdminLiveMetricsSnapshot(
            trackedDriversCount = metrics.trackedDriversCount,
            staleDriversCount = metrics.staleDriversCount,
            openWorkdayCount = metrics.openWorkdayCount,
            needsAttentionCount = metrics.needsAttentionCount
        )
    }

    /**
     * Rakit satu tabel CSV dari header + baris.
     *
     * Memakai `CsvWriter.tableToCsv` — escaping (koma, kutip, newline) dan padding baris
     * pendek terjadi di Kotlin yang sudah punya 18 test. Merakitnya di Swift berarti
     * menduplikasi aturan escaping di tempat yang tidak teruji, dan salahnya senyap: berkas
     * tetap terbuka di Excel, hanya isinya yang salah kolom.
     *
     * ISI-nya dibentuk pemanggil karena judul dan header adalah teks terlokalkan — sama
     * seperti Android, yang membangun section dari UI state dengan `context.getString`.
     */
    fun csvTable(headers: List<String>, rows: List<List<String>>): String =
        CsvWriter.tableToCsv(headers, rows)

    /** Escape satu sel — dipakai untuk judul section. */
    fun csvEscape(value: String): String = CsvWriter.escapeCsvCell(value)


    /**
     * Hitungan aktivitas hari ini + tren terhadap kemarin.
     *
     * Data diberikan pemanggil (Swift sudah memegangnya) supaya kartu tidak pernah bercerita
     * beda dari daftar di layar yang sama.
     */
    /**
     * Kartu kepatuhan hari ini — padanan `AdminHomeViewModel.buildComplianceCards()` Android
     * (PRD §7.A1, SSOT desain §2.3).
     *
     * ## Nol aritmetika di sini, dan itu kunci penerimaannya
     * Seluruh perhitungan didelegasikan ke [computeAdminComplianceCards] commonMain — fungsi
     * yang **sama** dipakai Android. Menulis rumus kedua di sini berarti dua rumus yang akan
     * mendrift, dan drift-nya berupa angka kepatuhan yang berbeda antar platform untuk hari yang
     * sama. Pola yang sama dipakai [IOSMonthlyRecapBridge].
     *
     * Yang penting dari fungsi commonMain itu: pembilangnya **di-interseksi** dengan yang
     * terjadwal, tidak dihitung apa adanya. Kalau driver yang sedang cuti tetap mengirim
     * checklist, ia tidak boleh membuat pembilang melebihi penyebut — "13/12" adalah angka yang
     * merusak kepercayaan pada seluruh kartu. Itulah sebabnya method ini menerima **himpunan
     * UID**, bukan hitungan: hitungan tidak bisa di-interseksi.
     *
     * ## Nol bentuk query Firestore baru
     * Ketiga bacaan yang dilakukan di sini sudah punya implementasi iOS nyata:
     * `getDriverSopStatuses`, `getAllDriverDisplayNames`, dan `getYear` kalender. Yang datang
     * dari Swift hanya data yang **sudah dipegang layar** (tap-in/checklist/safety hari ini) —
     * sengaja, supaya kartu tidak pernah bercerita beda dari daftar di layar yang sama. Alasan
     * yang sama ditulis di [computeOverview].
     *
     * ## Urutan guard-nya MENIRU Android persis, dan itu bukan gaya
     * `AdminHomeViewModel:539-543`: kalender gagal dimuat -> `ERROR`; roster masih kosong ->
     * `LOADING`. Membalik keduanya membuat kegagalan baca terlihat seperti "belum ada driver",
     * yaitu tepat kesalahan yang keempat keadaan itu ada untuk mencegah.
     *
     * @param todayEpochDay hari ini menurut Asia/Jakarta, diputuskan pemanggil — bukan dibaca
     *   dari jam sistem di sini, supaya jalur ini tetap murni dan sepakat dengan layar lain.
     */
    fun computeAdminCompliance(
        attendanceInUids: List<String>,
        checklistTodayUids: List<String>,
        safetyTodayUids: List<String>,
        todayEpochDay: Int,
        completion: (IOSAdminComplianceSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            val calendarYear = LocalDate.fromEpochDays(todayEpochDay).year
            val calendar = when (val res = getWorkCalendarYear(calendarYear)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(
                        AdminComplianceCards(AdminComplianceState.ERROR).toIosSnapshot(),
                        res.throwable.message ?: "Failed to load work calendar."
                    )
                    return@launch
                }
            }
            if (calendar == null) {
                // Kalender tahun ini belum dipublikasikan. Itu BUKAN "hari ini bukan hari
                // kerja" — kita tidak tahu, dan menebak `NO_SCHEDULE_TODAY` akan menampilkan
                // "tidak ada jadwal hari ini" pada hari kerja biasa.
                completion(AdminComplianceCards(AdminComplianceState.ERROR).toIosSnapshot(), null)
                return@launch
            }

            val names = when (val res = getAllDriverNames()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(
                        AdminComplianceCards(AdminComplianceState.ERROR).toIosSnapshot(),
                        res.throwable.message ?: "Failed to load driver roster."
                    )
                    return@launch
                }
            }
            if (names.isEmpty()) {
                completion(AdminComplianceCards(AdminComplianceState.LOADING).toIosSnapshot(), null)
                return@launch
            }

            val sopAccepted = when (val res = getSopStatuses()) {
                is AppResult.Success -> res.data.filter { it.sopAccepted }.map { it.uid }.toSet()
                is AppResult.Failure -> {
                    completion(
                        AdminComplianceCards(AdminComplianceState.ERROR).toIosSnapshot(),
                        res.throwable.message ?: "Failed to load SOP statuses."
                    )
                    return@launch
                }
            }

            val kind = resolveDayCalendarKind(calendar, todayEpochDay)
            val cards = computeAdminComplianceCards(
                isCompanyWorkday = kind == DayCalendarKind.SCHEDULED_WORKDAY,
                activeDriverUids = names.keys,
                attendanceInUids = attendanceInUids.toSet(),
                checklistSubmittedUids = checklistTodayUids.toSet(),
                safetySubmittedUids = safetyTodayUids.toSet(),
                sopAcceptedUids = sopAccepted
            )
            val spec = resolveWorkSpecForDate(calendar, todayEpochDay)
            val scheduleStartMillis = spec?.let { ws ->
                val hh = (ws.startMinutes / 60).toString().padStart(2, '0')
                val mm = (ws.startMinutes % 60).toString().padStart(2, '0')
                TimeUtil.parseHm("$hh:$mm", todayEpochDay.toLong(), IOSDriverTimezone)
            } ?: 0L
            completion(
                cards.toIosSnapshot(
                    isScheduledWorkday = kind == DayCalendarKind.SCHEDULED_WORKDAY,
                    scheduleStartMillis = scheduleStartMillis
                ),
                null
            )
        }
    }

    /**
     * Daftar pengecualian "Perlu tindakan" hari ini — padanan
     * `AdminHomeViewModel.computeExceptions()` Android (PRD §7.A2, SSOT desain §2.2).
     *
     * ## Nol rumus di sini, dan itu kunci penerimaannya
     * Seluruh perhitungan didelegasikan ke [buildAdminExceptionList] commonMain — fungsi yang
     * **sama** dipakai Android, beserta pembantunya ([outstandingChecklistIssues],
     * [recordedOvertimeMinutes], [resolveWorkSpecForDate]). Menulis rumus kedua di sini
     * berarti dua daftar yang bisa berbeda pendapat soal apa yang butuh tindakan — jenis
     * drift yang membuat admin mempercayai layar yang salah.
     *
     * ## Nol bentuk query Firestore baru
     * Kalender, nama roster, dan status SOP dibaca lewat use case yang sudah punya
     * implementasi iOS nyata. Yang datang dari Swift hanya data yang **sudah dipegang
     * layar** (kehadiran/checklist/safety/workday) — alasan yang sama ditulis di
     * [computeAdminCompliance] dan [computeOverview].
     *
     * ## Urutan guard-nya MENIRU Android persis
     * `AdminHomeViewModel.computeExceptions`: kalender gagal dimuat -> `ERROR`; roster masih
     * kosong -> `LOADING`. Membalik keduanya membuat kegagalan baca terlihat seperti "tidak
     * ada yang perlu ditindak", yaitu tepat kesalahan yang daftar ini ada untuk mencegah.
     *
     * @param attendanceInUids UID yang SUDAH tap-in hari ini, difilter pemanggil — kontrak
     *   yang sama dengan `computeAdminCompliance.attendanceInUids`.
     * @param todayEpochDay hari ini menurut Asia/Jakarta, diputuskan pemanggil.
     * @param nowMillis waktu sekarang, disuntikkan — fungsi tetap murni tanpa baca jam sistem.
     */
    fun computeExceptionList(
        attendanceInUids: List<String>,
        checklists: List<IOSAdminChecklistSummarySnapshot>,
        safety: List<IOSAdminSafetySummarySnapshot>,
        workdaysWindow: List<IOSDriverWorkdaySnapshot>,
        todayEpochDay: Int,
        nowMillis: Long,
        completion: (IOSAdminExceptionListSnapshot?, String?) -> Unit
    ) {
        scope.launch {
            val calendarYear = LocalDate.fromEpochDays(todayEpochDay).year
            val calendar = when (val res = getWorkCalendarYear(calendarYear)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(
                        IOSAdminExceptionListSnapshot("ERROR", emptyList()),
                        res.throwable.message ?: "Failed to load work calendar."
                    )
                    return@launch
                }
            }
            // `calendar` non-null oleh tipe (`AppResult.Success` dari `getWorkCalendarYear`).
            // Android memeriksa null karena `calendarThisYear`-nya nullable (StateFlow yang
            // belum termuat) — di sini keadaan itu sudah terwakili oleh `AppResult`.

            val names = when (val res = getAllDriverNames()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(
                        IOSAdminExceptionListSnapshot("ERROR", emptyList()),
                        res.throwable.message ?: "Failed to load driver roster."
                    )
                    return@launch
                }
            }
            if (names.isEmpty()) {
                completion(IOSAdminExceptionListSnapshot("LOADING", emptyList()), null)
                return@launch
            }

            val sopStatuses = when (val res = getSopStatuses()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    completion(
                        IOSAdminExceptionListSnapshot("ERROR", emptyList()),
                        res.throwable.message ?: "Failed to load SOP statuses."
                    )
                    return@launch
                }
            }

            // Irisan pekan berjalan, dipakai tiga kali di bawah — sama seperti Android:
            // disiapkan sekali supaya ketiga peta lembur tidak bisa diam-diam memakai
            // jendela yang berbeda satu sama lain.
            val workdayWeek = workdaysWindow.filter {
                it.dateEpochDay in (todayEpochDay - 6)..todayEpochDay
            }
            val spec = resolveWorkSpecForDate(calendar, todayEpochDay)

            val exceptions = buildAdminExceptionList(
                inputs = AdminExceptionInputs(
                    scheduledDriverUids = names.keys,
                    driverNames = names,
                    attendanceInUids = attendanceInUids.toSet(),
                    checklistIssues = outstandingChecklistIssues(
                        checklists.map {
                            ChecklistIssueCandidate(
                                id = it.id,
                                driverUid = it.driverUid,
                                dateEpochDay = it.dateEpochDay,
                                submittedAt = it.submittedAtMillis,
                                hasIssue = it.hasIssue,
                                followUpStatus = ChecklistFollowUpStatus.fromCode(it.followUpStatusRaw)
                            )
                        },
                        todayEpochDay
                    ),
                    // Tidak fit ATAU belum dikonfirmasi: keduanya bukan deklarasi yang sah.
                    safetyNotFit = safety
                        .filter { it.dateEpochDay == todayEpochDay && !(it.fitToWork && it.confirmed) }
                        .associate { it.driverUid to it.id },
                    sopNotAcceptedUids = sopStatuses.filter { !it.sopAccepted }.map { it.uid }.toSet(),
                    pendingWorkdays = workdaysWindow
                        .filter { it.statusRaw == "pending" }
                        .map { PendingWorkday(it.id, it.driverUid, it.dateEpochDay) },
                    // Lembur MENTAH (`overtimeMinutes` dokumen workday), bukan yang terbayar.
                    // Snapshot iOS tidak membawa `WorkDay` utuh, jadi `coerceAtLeast(0)` di sini
                    // adalah cermin satu baris dari `recordedOvertimeMinutes` commonMain.
                    dailyOvertimeByDriver = workdayWeek
                        .filter { it.dateEpochDay == todayEpochDay }
                        .groupBy { it.driverUid }
                        .mapValues { (_, v) -> v.sumOf { wd -> wd.overtimeMinutes.coerceAtLeast(0) } },
                    weeklyOvertimeByDriver = workdayWeek
                        .groupBy { it.driverUid }
                        .mapValues { (_, v) -> v.sumOf { wd -> wd.overtimeMinutes.coerceAtLeast(0) } },
                    overtimeRecordIdByDriver = workdayWeek
                        .groupBy { it.driverUid }
                        .mapValues { (_, v) -> v.maxByOrNull { it.dateEpochDay }?.id.orEmpty() },
                    // null bila hari ini bukan hari kerja -> pemicu "belum absen" tidak berlaku.
                    scheduleStartMillis = spec?.let { ws ->
                        val hh = (ws.startMinutes / 60).toString().padStart(2, '0')
                        val mm = (ws.startMinutes % 60).toString().padStart(2, '0')
                        TimeUtil.parseHm("$hh:$mm", todayEpochDay.toLong(), IOSDriverTimezone)
                    }
                ),
                todayEpochDay = todayEpochDay,
                nowMillis = nowMillis
            )
            completion(
                IOSAdminExceptionListSnapshot("READY", exceptions.map { it.toIosSnapshot() }),
                null
            )
        }
    }

    fun computeOverview(
        checklistDates: List<Int>,
        safetyDates: List<Int>,
        attendanceDates: List<Int>,
        attendanceTypes: List<String>,
        todayEpochDay: Int,
        yesterdayEpochDay: Int
    ): IOSAdminOverviewSnapshot {
        val today = countAdminOverview(
            checklistDates, safetyDates, attendanceDates, attendanceTypes, todayEpochDay
        )
        val yesterday = countAdminOverview(
            checklistDates, safetyDates, attendanceDates, attendanceTypes, yesterdayEpochDay
        )
        return IOSAdminOverviewSnapshot(
            checklistSubmitted = today.checklistSubmitted,
            safetySubmitted = today.safetySubmitted,
            attendanceIn = today.attendanceIn,
            attendanceOut = today.attendanceOut,
            checklistTrendPercent = dayOverDayTrendPercent(
                today.checklistSubmitted, yesterday.checklistSubmitted
            ),
            safetyTrendPercent = dayOverDayTrendPercent(
                today.safetySubmitted, yesterday.safetySubmitted
            ),
            attendanceTrendPercent = dayOverDayTrendPercent(
                today.attendanceIn, yesterday.attendanceIn
            )
        )
    }

    /**
     * Rakit activity feed dari data admin yang sedang dipegang pemanggil.
     *
     * List sejajar, bukan list of struct: konsisten dengan `computeLiveMetrics` dan
     * `computeOverview` di kelas ini, dan struct Kotlin ber-nest melewati batas ObjC menjadi
     * tipe pembungkus yang canggung dipakai di Swift.
     */
    fun buildActivityFeed(
        attendanceIds: List<String>,
        attendanceDriverUids: List<String>,
        attendanceTypes: List<String>,
        attendanceMillis: List<Long>,
        checklistIds: List<String>,
        checklistDriverUids: List<String>,
        checklistMillis: List<Long>,
        safetyIds: List<String>,
        safetyDriverUids: List<String>,
        safetyMillis: List<Long>
    ): List<IOSAdminActivitySnapshot> {
        val events = mutableListOf<AdminActivityEvent>()

        val nAtt = minOf(attendanceIds.size, attendanceDriverUids.size, attendanceTypes.size, attendanceMillis.size)
        for (i in 0 until nAtt) {
            // Tipe tak dikenal DILEWATI, bukan ditebak: menampilkannya sebagai tap-in
            // akan mengarang kejadian yang tidak pernah terjadi.
            val type = adminActivityTypeFromAttendance(attendanceTypes[i]) ?: continue
            events += AdminActivityEvent(
                type = type,
                driverUid = attendanceDriverUids[i],
                recordId = attendanceIds[i],
                timestampMillis = attendanceMillis[i]
            )
        }

        val nChk = minOf(checklistIds.size, checklistDriverUids.size, checklistMillis.size)
        for (i in 0 until nChk) {
            events += AdminActivityEvent(
                type = AdminActivityType.CHECKLIST_SUBMITTED,
                driverUid = checklistDriverUids[i],
                recordId = checklistIds[i],
                timestampMillis = checklistMillis[i]
            )
        }

        val nSaf = minOf(safetyIds.size, safetyDriverUids.size, safetyMillis.size)
        for (i in 0 until nSaf) {
            events += AdminActivityEvent(
                type = AdminActivityType.SAFETY_SUBMITTED,
                driverUid = safetyDriverUids[i],
                recordId = safetyIds[i],
                timestampMillis = safetyMillis[i]
            )
        }

        return buildAdminActivityFeed(events).map {
            IOSAdminActivitySnapshot(
                typeRaw = it.type.name,
                driverUid = it.driverUid,
                recordId = it.recordId,
                timeLabel = TimeUtil.formatHm(it.timestampMillis, IOSDriverTimezone),
                dateLabel = TimeUtil.formatYmdFromEpochDay(
                    TimeUtil.epochDayOf(it.timestampMillis, IOSDriverTimezone).toInt(),
                    IOSDriverTimezone
                ),
                timestampMillis = it.timestampMillis
            )
        }
    }
}

private fun ComplianceRatio.toIosSnapshot(): IOSComplianceRatioSnapshot =
    IOSComplianceRatioSnapshot(
        done = done,
        scheduled = scheduled,
        percentOrNull = percent,
        outstanding = outstanding
    )

/**
 * Konversi ke snapshot Swift. Nol perhitungan baru — `percent`, `outstanding`, dan
 * `totalOutstanding` semuanya properti turunan `AdminComplianceCards`/`ComplianceRatio`
 * commonMain, jadi Swift dan Android membaca angka yang berasal dari rumus yang sama.
 *
 * `scheduledDriverCount` diambil dari `attendance.scheduled` alih-alih dihitung ulang: keempat
 * rasio memakai penyebut yang identik, dan mengambilnya dari salah satunya membuat "penyebutnya
 * sama" menjadi sifat struktural, bukan sesuatu yang harus dijaga dengan disiplin.
 */
private fun AdminComplianceCards.toIosSnapshot(
    isScheduledWorkday: Boolean = false,
    scheduleStartMillis: Long = 0L
): IOSAdminComplianceSnapshot =
    IOSAdminComplianceSnapshot(
        stateRaw = state.name,
        attendance = attendance.toIosSnapshot(),
        checklist = checklist.toIosSnapshot(),
        safety = safety.toIosSnapshot(),
        sop = sop.toIosSnapshot(),
        scheduledDriverCount = attendance.scheduled,
        totalOutstanding = totalOutstanding,
        isScheduledWorkday = isScheduledWorkday,
        scheduleStartMillis = scheduleStartMillis
    )

/**
 * Konversi ke snapshot Swift. Nol perhitungan baru — `kindRaw` adalah nama enum, dan
 * urutan kegentingan sudah dikunci oleh urutan `AdminExceptionKind` commonMain: daftar
 * keluar dari [buildAdminExceptionList] sudah terurut, jadi Swift hanya perlu merender.
 */
private fun AdminException.toIosSnapshot(): IOSAdminExceptionSnapshot =
    IOSAdminExceptionSnapshot(
        kindRaw = kind.name,
        driverUid = driverUid,
        driverName = driverName,
        recordId = recordId,
        occurredAtMillis = occurredAtMillis ?: 0L,
        magnitude = magnitude,
        limitMinutes = limitMinutes,
        scopeRaw = scope?.name
    )
