package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.di.ensureIosKoinStarted
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.data.repository.IOSFirebaseAuthRepository
import com.drivemate.showcase.data.repository.IOSLocationRepository
import com.drivemate.showcase.domain.repository.PushTokenRepository
import com.drivemate.showcase.domain.usecase.admin.GetAdminAttendanceDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdaysUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetAttendanceByRangeUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetDriverAttendanceHistoryUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetTodayAttendanceUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapInUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapOutUseCase
import com.drivemate.showcase.domain.usecase.auth.LoginUseCase
import com.drivemate.showcase.domain.usecase.auth.LogoutUseCase
import com.drivemate.showcase.domain.usecase.auth.ObserveUserSessionUseCase
import com.drivemate.showcase.domain.usecase.broadcast.DeleteAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import com.drivemate.showcase.domain.usecase.broadcast.SendAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.UpdateAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.admin.GetDriverSopStatusesUseCase
import com.drivemate.showcase.domain.usecase.admin.RecordAdminWorkdayDecisionUseCase
import com.drivemate.showcase.domain.usecase.admin.UpdateChecklistFollowUpUseCase
import com.drivemate.showcase.domain.usecase.calendar.UpsertWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.report.AdminOverrideWorkdayDecisionUseCase
import com.drivemate.showcase.domain.usecase.report.ApproveWorkDayUseCase
import com.drivemate.showcase.domain.usecase.report.BulkApproveWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.report.BulkRejectWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.safety.GetSpeedLimitConfigUseCase
import com.drivemate.showcase.domain.usecase.safety.GetSpeedViolationsUseCase
import com.drivemate.showcase.domain.usecase.account.GetAccountDeletionRequestUseCase
import com.drivemate.showcase.domain.usecase.account.RequestAccountDeletionUseCase
import com.drivemate.showcase.domain.usecase.report.GetMonthlyDriverRecapUseCase
import com.drivemate.showcase.domain.usecase.safety.ReportSpeedViolationUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkdaySummaryUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.report.RejectWorkDayUseCase
import com.drivemate.showcase.domain.usecase.workday.GetDriverTodayScheduleUseCase
import com.drivemate.showcase.domain.usecase.workday.UpsertWorkdayPlanUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitSafetyDeclarationUseCase
import com.drivemate.showcase.domain.usecase.tracking.ObserveTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.UpdateTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.chat.ObserveChatRoomUseCase
import com.drivemate.showcase.domain.usecase.chat.SendMessageUseCase
import com.drivemate.showcase.domain.usecase.user.AcceptDriverSopUseCase
import com.drivemate.showcase.domain.usecase.assignment.GetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.SetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.UpsertDailyOverrideUseCase
import com.drivemate.showcase.domain.usecase.assignment.GetOverridesForDateUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import com.drivemate.showcase.domain.usecase.user.GetChatContactsUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class IOSFoundationBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentSession: UserSession? = null

    private val sessionBridge: IOSAuthSessionBridge
    private val trackingBridge: IOSTrackingBridge
    private val announcementBridge: IOSAnnouncementBridge
    private val driverWorkdayBridge: IOSDriverWorkdayBridge
    private val attendanceBridge: IOSAttendanceBridge
    private val checklistBridge: IOSChecklistBridge
    private val safetyBridge: IOSSafetyBridge
    private val userDirectoryBridge: IOSUserDirectoryBridge
    private val pushTokenBridge: IOSPushTokenBridge
    private val locationBridge: IOSLocationBridge
    private val workCalendarBridge: IOSWorkCalendarBridge
    private val workHistoryBridge: IOSWorkHistoryBridge
    private val workCalendarEditor: IOSWorkCalendarEditor
    private val speedMonitorBridge: IOSSpeedMonitorBridge
    private val speedViolationHistoryBridge: IOSSpeedViolationHistoryBridge
    private val monthlyRecapBridge: IOSMonthlyRecapBridge
    private val accountBridge: IOSAccountBridge
    private val chatBridge: IOSChatBridge
    private val adminDataBridge: IOSAdminDataBridge
    private val driverAssignmentBridge: IOSDriverAssignmentBridge

    init {
        val koin = ensureIosKoinStarted()

        sessionBridge = IOSAuthSessionBridge(
            scope = scope,
            authRepository = koin.get<IOSFirebaseAuthRepository>(),
            loginUseCase = koin.get<LoginUseCase>(),
            logoutUseCase = koin.get<LogoutUseCase>(),
            observeUserSession = koin.get<ObserveUserSessionUseCase>(),
            onSessionChanged = { session -> currentSession = session }
        )
        trackingBridge = IOSTrackingBridge(
            scope = scope,
            observeTrackingStatus = koin.get<ObserveTrackingStatusUseCase>(),
            updateTrackingStatus = koin.get<UpdateTrackingStatusUseCase>()
        )
        announcementBridge = IOSAnnouncementBridge(
            scope = scope,
            observeAnnouncements = koin.get<ObserveAnnouncementsUseCase>(),
            sendAnnouncement = koin.get<SendAnnouncementUseCase>(),
            updateAnnouncementUseCase = koin.get<UpdateAnnouncementUseCase>(),
            deleteAnnouncementUseCase = koin.get<DeleteAnnouncementUseCase>()
        )
        driverWorkdayBridge = IOSDriverWorkdayBridge(
            scope = scope,
            getWorkDaysByRange = koin.get<GetWorkDaysByRangeUseCase>(),
            getWorkdayDetail = koin.get<GetWorkdayDetailUseCase>(),
            approveWorkDay = koin.get<ApproveWorkDayUseCase>(),
            rejectWorkDay = koin.get<RejectWorkDayUseCase>(),
            upsertWorkdayPlan = koin.get<UpsertWorkdayPlanUseCase>(),
            adminOverrideWorkdayDecision = koin.get<AdminOverrideWorkdayDecisionUseCase>(),
            bulkApproveWorkDays = koin.get<BulkApproveWorkDaysUseCase>(),
            bulkRejectWorkDays = koin.get<BulkRejectWorkDaysUseCase>(),
            getDriverTodaySchedule = koin.get<GetDriverTodayScheduleUseCase>(),
            currentSession = { currentSession }
        )
        attendanceBridge = IOSAttendanceBridge(
            scope = scope,
            tapIn = koin.get<TapInUseCase>(),
            tapOut = koin.get<TapOutUseCase>(),
            getTodayAttendance = koin.get<GetTodayAttendanceUseCase>(),
            getAttendanceHistory = koin.get<GetDriverAttendanceHistoryUseCase>(),
            currentSession = { currentSession }
        )
        checklistBridge = IOSChecklistBridge(
            scope = scope,
            getChecklistStatus = koin.get<GetChecklistStatusUseCase>(),
            submitChecklistUseCase = koin.get<SubmitChecklistUseCase>(),
            currentSession = { currentSession }
        )
        safetyBridge = IOSSafetyBridge(
            scope = scope,
            getSafetyStatus = koin.get<GetSafetyDeclarationStatusUseCase>(),
            submitSafetyDeclarationUseCase = koin.get<SubmitSafetyDeclarationUseCase>(),
            currentSession = { currentSession }
        )
        userDirectoryBridge = IOSUserDirectoryBridge(
            scope = scope,
            getAllDriverDisplayNames = koin.get<GetAllDriverDisplayNamesUseCase>(),
            getManagedDrivers = koin.get<GetManagedDriversUseCase>(),
            getDriverSopStatus = koin.get<GetDriverSopStatusUseCase>(),
            acceptDriverSop = koin.get<AcceptDriverSopUseCase>(),
            getWorkdaySummary = koin.get<GetWorkdaySummaryUseCase>(),
            currentSession = { currentSession }
        )
        // Token FCM privat (`fcm_tokens`). PushTokenRepository didaftarkan BARU di
        // AppleSharedModule (Android mendaftarkannya di AndroidSharedModule) — tanpa itu
        // koin.get ini lolos compile dan verify-ios.sh, lalu melempar
        // NoDefinitionFoundException di SPLASH.
        pushTokenBridge = IOSPushTokenBridge(
            scope = scope,
            pushTokenRepository = koin.get<PushTokenRepository>(),
            currentSession = { currentSession }
        )
        locationBridge = IOSLocationBridge(
            repository = koin.get<IOSLocationRepository>(),
            scope = scope
        )
        workCalendarBridge = IOSWorkCalendarBridge(
            getCalendarUseCase = GetWorkCalendarYearUseCase(koin.get()),
            scope = scope
        )
        // Penyuntingan kalender. Use case dikonstruksi dari repository yang sama —
        // nol definisi Koin baru.
        // R3 monitor kecepatan. Kedua use case sudah terdaftar di commonModule:102-103 —
        // nol definisi Koin baru.
        speedMonitorBridge = IOSSpeedMonitorBridge(
            scope = scope,
            getSpeedLimitConfig = koin.get<GetSpeedLimitConfigUseCase>(),
            reportSpeedViolation = koin.get<ReportSpeedViolationUseCase>(),
            currentSession = { currentSession }
        )
        // Riwayat pelanggaran kecepatan (C1, Blok 3.3 / 6.5). Kedua use case sudah
        // terdaftar — GetSpeedViolationsUseCase di commonModule:109 dan
        // GetAllDriverDisplayNamesUseCase di commonModule:136, yang sudah dipakai
        // userDirectoryBridge dan driverAssignmentBridge di file ini. NOL definisi Koin
        // baru: definisi yang hilang tetap lolos compile dan verify-ios.sh, lalu melempar
        // NoDefinitionFoundException di SPLASH.
        speedViolationHistoryBridge = IOSSpeedViolationHistoryBridge(
            scope = scope,
            getViolations = koin.get<GetSpeedViolationsUseCase>(),
            getAllDriverDisplayNames = koin.get<GetAllDriverDisplayNamesUseCase>()
        )
        // Rekap bulanan per driver (Blok 4.3 / 6.5). Use case-nya sudah terdaftar di
        // commonModule:98 dan ketiga repository yang dibutuhkannya sudah NYATA di iOS
        // (IOSAdminDataRepository.getWorkdaysByRange, IOSWorkCalendarRepository.getYear,
        // IOSUserDirectoryRepository.getAllDriverDisplayNames) — jadi nol definisi Koin
        // baru DAN nol method bridge Swift baru.
        monthlyRecapBridge = IOSMonthlyRecapBridge(
            scope = scope,
            getMonthlyRecap = koin.get<GetMonthlyDriverRecapUseCase>()
        )
        // Penghapusan akun di dalam aplikasi (Blok 5, gerbang Apple 5.1.1(v)). Kedua use
        // case-nya DIDAFTARKAN BARU di AppleSharedModule bersama repository iOS-nya — sampai
        // perubahan ini, iOS nol berkas untuk jalur ini walau Android sudah punya. Repository
        // iOS-nya memakai `userDirectoryBridge` yang SUDAH terpasang, jadi signature
        // `installIOSPlatformBridges` tidak berubah dan tidak ada rantai cast baru.
        accountBridge = IOSAccountBridge(
            scope = scope,
            getRequest = koin.get<GetAccountDeletionRequestUseCase>(),
            requestDeletion = koin.get<RequestAccountDeletionUseCase>()
        )
        workCalendarEditor = IOSWorkCalendarEditor(
            scope = scope,
            getCalendarUseCase = GetWorkCalendarYearUseCase(koin.get()),
            upsertCalendarUseCase = UpsertWorkCalendarYearUseCase(koin.get())
        )
        // Riwayat Kerja (Agenda View). Keduanya sudah terdaftar dan sudah dipakai bridge
        // lain di file ini: GetWorkDaysByRangeUseCase oleh driverWorkdayBridge, dan
        // WorkCalendarRepository lewat pola GetWorkCalendarYearUseCase(koin.get()) di atas.
        // Tidak ada definisi Koin BARU — definisi yang hilang tetap lolos compile dan
        // verify-ios.sh, lalu melempar NoDefinitionFoundException di SPLASH.
        workHistoryBridge = IOSWorkHistoryBridge(
            scope = scope,
            getWorkDaysByRange = koin.get<GetWorkDaysByRangeUseCase>(),
            getWorkCalendarYear = GetWorkCalendarYearUseCase(koin.get()),
            currentSession = { currentSession }
        )
        // Penugasan expat-driver (R0). Semua use case-nya sudah terdaftar di
        // commonModule:137-142 — nol definisi Koin baru.
        driverAssignmentBridge = IOSDriverAssignmentBridge(
            scope = scope,
            getPermanentManager = koin.get<GetPermanentManagerUseCase>(),
            setPermanentManager = koin.get<SetPermanentManagerUseCase>(),
            upsertDailyOverride = koin.get<UpsertDailyOverrideUseCase>(),
            getOverridesForDate = koin.get<GetOverridesForDateUseCase>(),
            getAllDriverDisplayNames = koin.get<GetAllDriverDisplayNamesUseCase>(),
            getChatContacts = koin.get<GetChatContactsUseCase>(),
            currentSession = { currentSession }
        )
        chatBridge = IOSChatBridge(
            scope = scope,
            observeChatRoom = koin.get<ObserveChatRoomUseCase>(),
            sendMessage = koin.get<SendMessageUseCase>(),
            getChatContacts = koin.get<GetChatContactsUseCase>(),
            currentSession = { currentSession }
        )
        adminDataBridge = IOSAdminDataBridge(
            scope = scope,
            getAttendances = koin.get<GetAttendanceByRangeUseCase>(),
            getChecklists = koin.get<GetAdminChecklistsUseCase>(),
            getSafety = koin.get<GetAdminSafetyUseCase>(),
            getWorkdays = koin.get<GetAdminWorkdaysUseCase>(),
            getLocations = koin.get<GetAdminLocationsUseCase>(),
            getAttendanceDetail = koin.get<GetAdminAttendanceDetailUseCase>(),
            getChecklistDetail = koin.get<GetAdminChecklistDetailUseCase>(),
            getSafetyDetail = koin.get<GetAdminSafetyDetailUseCase>(),
            getWorkdayDetail = koin.get<GetAdminWorkdayDetailUseCase>(),
            getLocationDetail = koin.get<GetAdminLocationDetailUseCase>(),
            // Ketiganya untuk kartu kepatuhan §2.3. `GetDriverSopStatusesUseCase` dan
            // `GetAllDriverDisplayNamesUseCase` SUDAH terdaftar di commonModule (baris 126 dan
            // 145), jadi `koin.get` aman. `GetWorkCalendarYearUseCase` TIDAK terdaftar — ia
            // dibangun dari repository-nya, pola yang sama dipakai workHistoryBridge di atas.
            // Memakai `koin.get<GetWorkCalendarYearUseCase>()` di sini akan lolos compile DAN
            // lolos verify-ios.sh, lalu melempar NoDefinitionFoundException di SPLASH.
            getSopStatuses = koin.get<GetDriverSopStatusesUseCase>(),
            getAllDriverNames = koin.get<GetAllDriverDisplayNamesUseCase>(),
            getWorkCalendarYear = GetWorkCalendarYearUseCase(koin.get()),
            recordDecision = koin.get<RecordAdminWorkdayDecisionUseCase>(),
            updateChecklistFollowUp = koin.get<UpdateChecklistFollowUpUseCase>()
        )
    }

    fun startObserving(
        sessionChanged: (IOSSessionSnapshot?) -> Unit,
        roleResolutionChanged: (IOSRoleResolutionSnapshot) -> Unit,
        trackingChanged: (IOSTrackingSnapshot) -> Unit
    ) {
        sessionBridge.startObserving(sessionChanged, roleResolutionChanged)
        trackingBridge.startObserving(trackingChanged)
    }

    fun startObservingAnnouncements(
        announcementsChanged: (List<IOSAnnouncementSnapshot>, String?) -> Unit
    ) {
        announcementBridge.startObserving(announcementsChanged)
    }

    fun stopObservingAnnouncements() {
        announcementBridge.stopObserving()
    }

    fun stopObserving() {
        sessionBridge.stopObserving()
        trackingBridge.stopObserving()
    }

    fun login(
        email: String,
        password: String,
        completion: (String?) -> Unit
    ) {
        sessionBridge.login(email, password, completion)
    }

    fun logout(completion: (String?) -> Unit) {
        sessionBridge.logout(completion)
    }

    fun retryRoleResolution() {
        sessionBridge.retryRoleResolution()
    }

    fun setHideLocationWhenOffDuty(enabled: Boolean) {
        trackingBridge.setHideLocationWhenOffDuty(enabled)
    }

    /**
     * Teruskan ke [IOSAdminDataBridge.computeAdminCompliance]. Nol logika di sini — file ini
     * composite root, bukan tempat aturan.
     */
    fun computeAdminCompliance(
        attendanceInUids: List<String>,
        checklistTodayUids: List<String>,
        safetyTodayUids: List<String>,
        todayEpochDay: Int,
        completion: (IOSAdminComplianceSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.computeAdminCompliance(
            attendanceInUids, checklistTodayUids, safetyTodayUids, todayEpochDay, completion
        )
    }

    /**
     * Teruskan ke [IOSAdminDataBridge.computeExceptionList]. Nol logika di sini — file ini
     * composite root, bukan tempat aturan.
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
        adminDataBridge.computeExceptionList(
            attendanceInUids, checklists, safety, workdaysWindow,
            todayEpochDay, nowMillis, completion
        )
    }

    fun loadDriverWorkdays(
        targetDriverUid: String?,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        driverWorkdayBridge.loadDriverWorkdays(targetDriverUid, completion)
    }

    fun fetchWorkdayDetail(
        workdayId: String,
        completion: (IOSDriverWorkdaySnapshot?, String?) -> Unit
    ) {
        driverWorkdayBridge.fetchWorkdayDetail(workdayId, completion)
    }

    fun approveWorkday(
        workdayId: String,
        note: String?,
        completion: (Boolean, String?) -> Unit
    ) {
        driverWorkdayBridge.approveWorkday(workdayId, note, completion)
    }

    fun rejectWorkday(
        workdayId: String,
        reason: String,
        completion: (Boolean, String?) -> Unit
    ) {
        driverWorkdayBridge.rejectWorkday(workdayId, reason, completion)
    }

    fun adminOverrideWorkdayDecision(
        workdayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String,
        completion: (Boolean, String?) -> Unit
    ) {
        driverWorkdayBridge.adminOverrideWorkdayDecision(
            workdayId,
            targetStatus,
            rejectReason,
            overrideReason,
            completion
        )
    }

    fun bulkApproveWorkdays(
        workdayIds: List<String>,
        completion: (IOSBulkActionResultSnapshot?, String?) -> Unit
    ) {
        driverWorkdayBridge.bulkApproveWorkdays(workdayIds, completion)
    }

    fun bulkRejectWorkdays(
        workdayIds: List<String>,
        note: String,
        completion: (IOSBulkActionResultSnapshot?, String?) -> Unit
    ) {
        driverWorkdayBridge.bulkRejectWorkdays(workdayIds, note, completion)
    }

    fun loadTodaySchedule(completion: (IOSScheduleSnapshot?) -> Unit) {
        driverWorkdayBridge.loadTodaySchedule(completion)
    }

    fun loadAttendanceReferenceWindow(completion: (IOSAttendanceReferenceWindowSnapshot) -> Unit) {
        driverWorkdayBridge.loadAttendanceReferenceWindow(completion)
    }

    fun saveWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        completion: (Boolean, String?) -> Unit
    ) {
        driverWorkdayBridge.saveWorkdayPlan(
            driverUid,
            epochDay,
            plannedStartMillis,
            plannedEndMillis,
            completion
        )
    }

    fun createAnnouncement(
        title: String,
        body: String,
        audienceRaw: String,
        completion: (Boolean, String?) -> Unit
    ) {
        announcementBridge.createAnnouncement(title, body, audienceRaw, completion)
    }

    fun updateAnnouncement(
        id: String,
        title: String,
        body: String,
        completion: (Boolean, String?) -> Unit
    ) {
        announcementBridge.updateAnnouncement(id, title, body, completion)
    }

    fun deleteAnnouncement(
        id: String,
        completion: (Boolean, String?) -> Unit
    ) {
        announcementBridge.deleteAnnouncement(id, completion)
    }

    fun tapIn(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        attendanceBridge.tapIn(completion)
    }

    fun tapOut(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        attendanceBridge.tapOut(completion)
    }

    fun loadTodayAttendance(completion: (IOSAttendanceSnapshot?, String?) -> Unit) {
        attendanceBridge.loadTodayAttendance(completion)
    }

    /**
     * Riwayat absensi driver.
     *
     * **Nol pemanggil Swift sejak 2026-09-02**, saat `DriverHistoryView` diganti Agenda View
     * yang membaca `workdays`, bukan `attendances`. Android juga tidak lagi merender daftar
     * absensi mentah di mana pun, jadi ini bukan gap parity.
     *
     * Sengaja DIPERTAHANKAN, bukan dihapus: jalurnya utuh dan teruji, dan layar riwayat
     * absensi bisa saja diminta lagi. Catatan ini ada supaya pembaca berikutnya tahu ini
     * API yang tersedia, bukan sesuatu yang sudah ter-wire ke UI.
     */
    fun loadAttendanceHistory(completion: (List<IOSAttendanceSnapshot>, String?) -> Unit) {
        attendanceBridge.loadAttendanceHistory(completion)
    }

    fun loadTodayChecklist(completion: (IOSChecklistSnapshot?) -> Unit) {
        checklistBridge.loadTodayChecklist(completion)
    }

    fun submitChecklist(
        answers: Map<String, Boolean>,
        vehicleId: String?,
        odometerKm: Int?,
        fuelLevel: String?,
        tirePressureNote: String?,
        notes: String?,
        completion: (Boolean, Boolean, String?) -> Unit
    ) {
        checklistBridge.submitChecklist(
            answers,
            vehicleId,
            odometerKm,
            fuelLevel,
            tirePressureNote,
            notes,
            completion
        )
    }

    fun loadTodaySafetyDeclaration(completion: (IOSSafetySnapshot?) -> Unit) {
        safetyBridge.loadTodaySafetyDeclaration(completion)
    }

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
        safetyBridge.submitSafetyDeclaration(
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
            incidentReport = incidentReport,
            completion = completion
        )
    }

    fun loadDriverList(completion: (List<IOSDriverProfileSnapshot>) -> Unit) {
        userDirectoryBridge.loadDriverList(completion)
    }

    fun loadSopStatus(completion: (IOSSopStatusSnapshot?) -> Unit) {
        userDirectoryBridge.loadSopStatus(completion)
    }

    fun loadSopStatusForDriver(uid: String, completion: (IOSSopStatusSnapshot?) -> Unit) {
        userDirectoryBridge.loadSopStatusForDriver(uid, completion)
    }

    fun startObservingAllDriverLocations(
        onChange: (List<IOSDriverLocationSnapshot>) -> Unit
    ) {
        locationBridge.startObservingAllDriverLocations(onChange)
    }

    fun startObservingDriverLocation(
        driverUid: String,
        onChange: (IOSDriverLocationSnapshot?) -> Unit
    ) {
        locationBridge.startObservingDriverLocation(driverUid, onChange)
    }

    fun stopObservingLocations() {
        locationBridge.stopObservingLocations()
    }

    fun acceptSop(completion: (Boolean, String?) -> Unit) {
        userDirectoryBridge.acceptSop(completion)
    }

    // MARK: Penugasan expat-driver (R0)

    fun assignmentTodayEpochDay(): Int = driverAssignmentBridge.todayEpochDay()

    fun loadAssignmentOptions(completion: (IOSAssignmentOptionsSnapshot?, String?) -> Unit) {
        driverAssignmentBridge.loadOptions(completion)
    }

    fun loadPermanentManager(driverUid: String, completion: (String?, String?) -> Unit) {
        driverAssignmentBridge.loadPermanentManager(driverUid, completion)
    }

    fun savePermanentManager(
        driverUid: String,
        expatUid: String,
        completion: (Boolean, String?) -> Unit
    ) {
        driverAssignmentBridge.savePermanentManager(driverUid, expatUid, completion)
    }

    fun loadAssignmentOverrides(
        epochDay: Int,
        completion: (List<IOSDriverAssignmentOverrideSnapshot>?, String?) -> Unit
    ) {
        driverAssignmentBridge.loadOverrides(epochDay, completion)
    }

    fun createAssignmentOverride(
        driverUid: String,
        expatUid: String,
        reasonRaw: String,
        epochDay: Int,
        completion: (Boolean, String?) -> Unit
    ) {
        driverAssignmentBridge.createOverride(driverUid, expatUid, reasonRaw, epochDay, completion)
    }

    /** Metrik "perlu perhatian" Admin Home, dihitung fungsi commonMain yang sama dipakai Android. */
    fun computeAdminLiveMetrics(
        driverUids: List<String>,
        latitudes: List<Double>,
        longitudes: List<Double>,
        updatedAtMillis: List<Long>,
        openWorkdayCount: Int,
        checklistMissingToday: Int,
        safetyMissingToday: Int,
        sopPendingCount: Int
    ): IOSAdminLiveMetricsSnapshot = adminDataBridge.computeLiveMetrics(
        driverUids, latitudes, longitudes, updatedAtMillis,
        openWorkdayCount, checklistMissingToday, safetyMissingToday, sopPendingCount
    )

    // MARK: R3 monitor batas kecepatan

    /** Muat `app_config/safety`. Panggil sekali saat mulai melacak. */
    fun loadSpeedLimitConfig(completion: (Boolean) -> Unit) =
        speedMonitorBridge.loadConfig(completion)

    /** Lupakan cooldown/histeresis dan config — saat driver berhenti bertugas. */
    fun resetSpeedMonitor() = speedMonitorBridge.reset()

    /**
     * Evaluasi satu sampel kecepatan: mengembalikan status banner, dan menulis
     * `speed_violations` bila detektor bilang layak dinotifikasi.
     */
    fun evaluateSpeedSample(
        speedKph: Double?,
        latitude: Double?,
        longitude: Double?,
        completion: (IOSSpeedStatusSnapshot) -> Unit
    ) = speedMonitorBridge.evaluateSample(speedKph, latitude, longitude, completion)

    // MARK: Publikasi lokasi live driver

    fun publishDriverLocation(
        driverUid: String,
        latitude: Double,
        longitude: Double,
        speedMps: Double?,
        headingDeg: Double?,
        accuracyM: Double?,
        completion: (Boolean, String?) -> Unit
    ) = locationBridge.publishDriverLocation(
        driverUid, latitude, longitude, speedMps, headingDeg, accuracyM, completion
    )

    fun clearDriverLocation(completion: (Boolean, String?) -> Unit) =
        locationBridge.clearDriverLocation(completion)

    // MARK: Export CSV

    /** Rakit satu tabel CSV (escaping di Kotlin yang sudah teruji). */
    fun csvTable(headers: List<String>, rows: List<List<String>>): String =
        adminDataBridge.csvTable(headers, rows)

    /** Escape satu sel — untuk judul section. */
    fun csvEscape(value: String): String = adminDataBridge.csvEscape(value)

    fun exportAttendanceCsv(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (String?, String?) -> Unit
    ) = adminDataBridge.exportAttendanceCsv(startEpochDay, endEpochDay, completion)

    fun exportWorkdaysCsv(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (String?, String?) -> Unit
    ) = adminDataBridge.exportWorkdaysCsv(startEpochDay, endEpochDay, completion)

    /** Activity feed Admin Home — 10 kejadian terbaru, urut waktu. */
    fun buildAdminActivityFeed(
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
    ): List<IOSAdminActivitySnapshot> = adminDataBridge.buildActivityFeed(
        attendanceIds, attendanceDriverUids, attendanceTypes, attendanceMillis,
        checklistIds, checklistDriverUids, checklistMillis,
        safetyIds, safetyDriverUids, safetyMillis
    )

    /** Aktivitas hari ini + tren terhadap kemarin (Admin Home). */
    fun computeAdminOverview(
        checklistDates: List<Int>,
        safetyDates: List<Int>,
        attendanceDates: List<Int>,
        attendanceTypes: List<String>,
        todayEpochDay: Int,
        yesterdayEpochDay: Int
    ): IOSAdminOverviewSnapshot = adminDataBridge.computeOverview(
        checklistDates, safetyDates, attendanceDates, attendanceTypes,
        todayEpochDay, yesterdayEpochDay
    )

    /** Cari driver (Admin Home). Atas roster yang sudah dimuat, bukan query per ketikan. */
    fun searchDriverRoster(
        query: String,
        recordIds: List<String>,
        completion: (List<IOSAssignmentPersonSnapshot>, List<String>) -> Unit
    ) = userDirectoryBridge.searchDriverRoster(query, recordIds, completion)

    /** Driver kelolaan + ringkasan review (Expat Home). */
    fun loadManagedDriverSummaries(
        completion: (List<IOSManagedDriverSummarySnapshot>, IOSExpatSummaryHeaderSnapshot?, String?) -> Unit
    ) = userDirectoryBridge.loadManagedDriverSummaries(completion)

    /**
     * Daftarkan token FCM untuk sesi yang sedang login ke `fcm_tokens`. Non-fatal by design —
     * lihat KDoc [IOSPushTokenBridge]. Signature tetap, jadi pemanggil di `IOSAppState.swift`
     * tidak berubah.
     */
    fun registerFcmToken(token: String, completion: (Boolean, String?) -> Unit) {
        pushTokenBridge.registerFcmToken(token, completion)
    }

    /**
     * Cabut dokumen token FCM ([docId] = sha256 hex token, dihitung Swift). Panggil sebelum
     * `signOut` — lihat KDoc [IOSPushTokenBridge.unregisterFcmToken].
     */
    fun unregisterFcmToken(docId: String, completion: (Boolean, String?) -> Unit) {
        pushTokenBridge.unregisterFcmToken(docId, completion)
    }

    fun loadWorkCalendar(
        year: Int,
        completion: (IOSWorkCalendarSnapshot?) -> Unit
    ) {
        workCalendarBridge.loadWorkCalendar(year, completion)
    }

    // MARK: Penyuntingan Work Calendar (padanan AdminWorkCalendarViewModel Android)

    fun loadWorkCalendarEditor(
        year: Int,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) = workCalendarEditor.load(year, completion)

    fun discardWorkCalendarDraft(): IOSWorkCalendarEditorSnapshot? = workCalendarEditor.discardDraft()

    fun applyWorkCalendarOverride(
        dateEpochDay: Int,
        kindRaw: String,
        customStartMinutes: Int,
        customEndMinutes: Int,
        customBreakMinutes: Int,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) = workCalendarEditor.applyOverride(
        dateEpochDay, kindRaw, customStartMinutes, customEndMinutes, customBreakMinutes, completion
    )

    fun clearWorkCalendarOverride(
        dateEpochDay: Int,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) = workCalendarEditor.clearOverride(dateEpochDay, completion)

    fun setWorkCalendarNote(
        dateEpochDay: Int,
        note: String?,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) = workCalendarEditor.setNote(dateEpochDay, note, completion)

    /** Ubah jam kerja default (weekday / Jumat). Validasi tetap terjadi saat publish. */
    fun updateWorkCalendarDefaultRule(
        isFriday: Boolean,
        startMinutes: Int,
        endMinutes: Int,
        breakMinutes: Int,
        completion: (IOSWorkCalendarEditorSnapshot?, String?) -> Unit
    ) = workCalendarEditor.updateDefaultRule(
        isFriday, startMinutes, endMinutes, breakMinutes, completion
    )

    fun publishWorkCalendar(
        auditNote: String,
        completion: (Boolean, String?) -> Unit
    ) = workCalendarEditor.publish(auditNote, completion)

    /**
     * Riwayat Kerja — satu bulan Agenda View. Padanan iOS untuk PR #235.
     *
     * Sengaja TIDAK memakai [loadWorkCalendar]: `IOSWorkCalendarSnapshot` membuang
     * override libur perusahaan yang `note`-nya kosong, yang di layar ini akan tampil
     * sebagai akhir pekan biasa. Lihat KDoc [IOSWorkHistoryBridge].
     */
    fun loadWorkHistoryMonth(
        year: Int,
        month: Int,
        completion: (IOSWorkHistoryMonthSnapshot?, String?) -> Unit
    ) {
        workHistoryBridge.loadMonth(year, month, completion)
    }

    /** Bulan berjalan menurut Asia/Jakarta — state awal layar Riwayat Kerja. */
    fun currentWorkHistoryMonth(): IOSWorkHistoryMonthRef = workHistoryBridge.currentMonth()

    /**
     * Riwayat pelanggaran batas kecepatan — admin MEMBACA (Blok 6.5).
     *
     * Jendela dan batas dokumennya diputuskan Kotlin (`GetSpeedViolationsUseCase.DEFAULT_*`),
     * bukan diteruskan dari Swift: keduanya angka produk yang harus sama di kedua platform,
     * dan parameter di sini hanya akan menjadi tempat keduanya bisa berbeda.
     */
    fun loadSpeedViolations(
        completion: (IOSSpeedViolationHistorySnapshot?, String?) -> Unit
    ) = speedViolationHistoryBridge.load(completion)

    /**
     * Rekap bulanan per driver — admin MEMBACA (Blok 6.5).
     *
     * `todayEpochDay` dan `parseHm` diputuskan di Kotlin pada Asia/Jakarta, bukan
     * diteruskan dari Swift: keduanya menentukan hari mana yang "belum tiba", dan layar ini
     * harus sepakat dengan Riwayat Kerja driver untuk bulan yang sama.
     */
    fun loadMonthlyRecap(
        year: Int,
        month: Int,
        completion: (IOSMonthlyRecapSnapshot?, String?) -> Unit
    ) = monthlyRecapBridge.loadMonth(year, month, completion)

    /** Bulan berjalan menurut Asia/Jakarta — state awal layar Rekap Bulanan. */
    fun currentRecapMonth(): IOSWorkHistoryMonthRef = monthlyRecapBridge.currentMonth()

    /**
     * CSV dari rekap yang SEDANG tampil; `null` bila belum ada rekap yang berhasil dimuat.
     *
     * Perakitan barisnya tetap di commonMain — hanya labelnya yang datang dari Swift, karena
     * `commonMain` tidak punya akses `Localizable.strings`.
     */
    fun buildMonthlyRecapCsv(labels: IOSMonthlyRecapCsvLabels): String? =
        monthlyRecapBridge.buildCsv(labels)

    /**
     * Muat status permintaan penghapusan akun milik pengguna yang sedang login (Blok 5).
     *
     * `null` di dalam snapshot berarti belum pernah ada permintaan — keadaan normal, bukan
     * galat. Galat datang lewat parameter kedua.
     */
    fun loadAccountDeletionRequest(
        completion: (IOSAccountDeletionStateSnapshot?, String?) -> Unit
    ) = accountBridge.load(completion)

    /**
     * Kirim permintaan penghapusan akun.
     *
     * Tidak menerima uid: use case-nya mengambilnya dari sesi, supaya Swift tidak pernah bisa
     * menyusun permintaan atas nama orang lain. Tidak menghapus apa pun — lihat KDoc
     * `AccountDeletionRepository`.
     */
    fun submitAccountDeletionRequest(
        reason: String?,
        completion: (IOSAccountDeletionSubmitResult) -> Unit
    ) = accountBridge.submit(reason, completion)

    /**
     * Teruskan ke [IOSAdminDataBridge.recordAdminWorkdayDecision]. Nol logika di sini.
     */
    fun recordAdminWorkdayDecision(
        workdayId: String,
        targetCode: String,
        note: String?,
        actorUid: String,
        nowMillis: Long,
        completion: (String?, String?) -> Unit
    ) {
        adminDataBridge.recordAdminWorkdayDecision(
            workdayId, targetCode, note, actorUid, nowMillis, completion
        )
    }

    /**
     * Teruskan ke [IOSAdminDataBridge.saveChecklistFollowUp]. Nol logika di sini.
     */
    fun saveChecklistFollowUp(
        checklistId: String,
        targetCode: String,
        note: String?,
        actorUid: String,
        nowMillis: Long,
        completion: (String?, String?) -> Unit
    ) {
        adminDataBridge.saveChecklistFollowUp(
            checklistId, targetCode, note, actorUid, nowMillis, completion
        )
    }

    fun loadAdminAttendances(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminAttendanceSnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadAttendances(startEpochDay, endEpochDay, completion)
    }

    fun loadAdminChecklists(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminChecklistSummarySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadChecklists(startEpochDay, endEpochDay, completion)
    }

    fun loadAdminSafety(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSAdminSafetySummarySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadSafety(startEpochDay, endEpochDay, completion)
    }

    fun loadAdminWorkdays(
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadWorkdays(startEpochDay, endEpochDay, completion)
    }

    fun loadAdminLocations(
        completion: (List<IOSAdminLocationSnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadLocations(completion)
    }

    fun loadAdminAttendancesForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminAttendanceSnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadAttendancesForDriver(startEpochDay, endEpochDay, driverUid, completion)
    }

    fun loadAdminChecklistsForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminChecklistSummarySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadChecklistsForDriver(startEpochDay, endEpochDay, driverUid, completion)
    }

    fun loadAdminSafetyForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSAdminSafetySummarySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadSafetyForDriver(startEpochDay, endEpochDay, driverUid, completion)
    }

    fun loadAdminWorkdaysForDriver(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        adminDataBridge.loadWorkdaysForDriver(startEpochDay, endEpochDay, driverUid, completion)
    }

    fun loadAdminAttendanceById(
        id: String,
        completion: (IOSAdminAttendanceSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.loadAttendanceById(id, completion)
    }

    fun loadAdminChecklistDetail(
        id: String,
        completion: (IOSAdminChecklistDetailSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.loadChecklistDetail(id, completion)
    }

    fun loadAdminSafetyDetail(
        id: String,
        completion: (IOSAdminSafetyDetailSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.loadSafetyDetail(id, completion)
    }

    fun loadAdminWorkdayById(
        id: String,
        completion: (IOSAdminWorkdayDetailSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.loadWorkdayById(id, completion)
    }

    fun loadAdminLocationByUid(
        uid: String,
        completion: (IOSAdminLocationSnapshot?, String?) -> Unit
    ) {
        adminDataBridge.loadLocationByUid(uid, completion)
    }

    /** [onChange] now reports listener failures instead of swallowing them as an empty list. */
    fun startObservingChatThread(
        partnerUid: String,
        onChange: (List<IOSChatMessageSnapshot>?, String?) -> Unit
    ) {
        chatBridge.startObservingChatThread(partnerUid, onChange)
    }

    fun stopObservingChatThread() {
        chatBridge.stopObservingChatThread()
    }

    fun sendChatMessage(
        partnerUid: String,
        text: String,
        completion: (Boolean, String?) -> Unit
    ) {
        chatBridge.sendMessage(partnerUid, text, completion)
    }

    /**
     * Replaces `loadChatThreadList`, which always returned an empty list (there is no
     * thread index in Firestore) and left the chat screens sourcing contacts from the
     * driver roster. Contacts are now role-correct — see [IOSChatBridge.loadChatContacts].
     */
    fun loadChatContacts(completion: (List<IOSChatContactSnapshot>?, String?) -> Unit) {
        chatBridge.loadChatContacts(completion)
    }

    fun dispose() {
        stopObserving()
        announcementBridge.stopObserving()
        locationBridge.stopObservingLocations()
        chatBridge.stopObservingChatThread()
        scope.cancel()
    }
}
