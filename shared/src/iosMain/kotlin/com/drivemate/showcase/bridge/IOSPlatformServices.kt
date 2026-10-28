package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.util.AlreadySubmittedException

data class IOSAuthUserPayload(
    val uid: String,
    val email: String?
)

data class IOSAnnouncementPayload(
    val id: String,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val createdByName: String?,
    val createdByEmail: String?
)

data class IOSWorkdayPayload(
    val id: String,
    val driverUid: String,
    val dateEpochDay: Int,
    val clockInMillis: Long,
    val clockOutMillis: Long,
    val totalMinutes: Int,
    val overtimeMinutes: Int,
    val driverName: String?,
    val status: String?,
    val approvedByExpat: Boolean?,
    val approvedByUid: String?,
    val rejectedByUid: String?,
    val rejectedAtMillis: Long?,
    val rejectReason: String?,
    val rejectionNote: String?,
    val hasChecklist: Boolean?,
    val hasSafetyDeclaration: Boolean?,
    // Planned window dibutuhkan Expat untuk prefill dialog edit rencana kerja.
    // Sumbernya field `plannedStartMillis`/`plannedEndMillis` di dokumen `workdays`,
    // ditulis oleh jalur generate workday (Fase 1c).
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?
)

data class IOSAttendanceDailyPayload(
    val driverUid: String,
    val epochDay: Long,
    val clockInMillis: Long?,
    val clockOutMillis: Long?,
    val updatedAtMillis: Long
)

data class IOSAttendanceEventPayload(
    val driverUid: String,
    val timestampMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val note: String?,
    val locationName: String?
)

data class IOSLocationPayload(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAtMs: Long,
    val statusRaw: String,
    val displayName: String
)

data class IOSChecklistPayload(
    val hasIssue: Boolean,
    val vehicleId: String?,
    val odometerKm: Int?,
    val fuelLevel: String?,
    val tiresFrontLeftOk: Boolean?,
    val tiresFrontRightOk: Boolean?,
    val tiresRearLeftOk: Boolean?,
    val tiresRearRightOk: Boolean?,
    val tirePressureNote: String?,
    val brakesOk: Boolean?,
    val lightsOk: Boolean?,
    val turnSignalBrakeLightsOk: Boolean?,
    val hornOk: Boolean?,
    val wipersOk: Boolean?,
    val mirrorsOk: Boolean?,
    val seatbeltOk: Boolean?,
    val engineOilOk: Boolean?,
    val coolantOk: Boolean?,
    val batteryOk: Boolean?,
    val washerFluidOk: Boolean?,
    val cleanlinessOk: Boolean?,
    val documentsOk: Boolean?,
    val fireExtinguisherOk: Boolean?,
    val firstAidOk: Boolean?,
    val warningTriangleOk: Boolean?,
    val notes: String?
)

data class IOSWorkCalendarDayPayload(
    val epochDay: Long,
    val isWorkday: Boolean,
    val holidayName: String?
)

interface IOSListenerRegistration {
    fun remove()
}

interface IOSAuthPlatformBridge {
    fun currentUser(): IOSAuthUserPayload?
    fun observeAuthState(onChange: (IOSAuthUserPayload?) -> Unit): IOSListenerRegistration
    fun signIn(email: String, password: String, completion: (String?) -> Unit)
    fun signOut(completion: (String?) -> Unit)
    fun sendPasswordReset(email: String, completion: (String?) -> Unit)
    fun fetchUserRole(uid: String, completion: (String?, String?) -> Unit)
}

interface IOSWorkdayPlatformBridge {
    fun fetchDriverWorkdays(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int,
        completion: (List<IOSWorkdayPayload>?, String?) -> Unit
    )
    fun fetchWorkdayById(
        workdayId: String,
        completion: (IOSWorkdayPayload?, String?) -> Unit
    )
    fun approveWorkday(
        workdayId: String,
        note: String?,
        completion: (String?) -> Unit
    )
    fun rejectWorkday(
        workdayId: String,
        reason: String,
        completion: (String?) -> Unit
    )

    // Bulk decisions need the *raw* document shape, not the typed IOSWorkdayPayload:
    // the Android precheck classifies a field as missing vs wrong-type, and rejects
    // documents carrying keys outside WORKDAY_ALLOWED_KEYS. A typed payload has
    // already collapsed both distinctions, so it cannot drive that precheck.
    fun fetchWorkdayDecisionFields(
        workdayId: String,
        completion: (IOSWorkdayDecisionFields?, String?) -> Unit
    )

    fun applyBulkWorkdayDecision(
        workdayId: String,
        approve: Boolean,
        rejectReason: String?,
        backfillDateEpochDay: Long?,
        backfillTotalMinutes: Long?,
        completion: (String?, Boolean) -> Unit
    )

    /**
     * Admin override write. Every decision (role check, status normalization,
     * statusChanged / previousFinalized / rejectReasonChanged) is made on the Kotlin
     * side so it stays in lockstep with Android; this call only assembles and writes
     * the resulting payload.
     *
     * `actorUid` and `nowMillis` are passed in rather than resolved here so the whole
     * payload shares one timestamp and one actor — the same ones the role check ran
     * against.
     */
    fun applyAdminOverrideDecision(
        workdayId: String,
        targetStatus: String,
        actorUid: String,
        nowMillis: Long,
        rejectReason: String?,
        statusChanged: Boolean,
        overrideReason: String?,
        overrideFromStatus: String?,
        completion: (String?) -> Unit
    )

    /**
     * Tulis langkah 2 jejak keputusan (Blok 4.2) — padanan write Android di
     * `FirebaseAdminDataRepository.recordAdminWorkdayDecision`: map BERSARANG + merge,
     * supaya `decisionTrail.supervisor` (langkah 1, beku menurut rules) lestari.
     *
     * Payload dirakit PENUH di Kotlin (`buildWorkdayDecisionStepPayload` + nesting
     * `decisionTrail.admin` + `updatedAt`); Swift hanya menulis map yang diberikan dengan
     * merge — nol nama field Firestore dieja ulang di Swift.
     */
    fun recordAdminWorkdayDecision(
        workdayId: String,
        payload: Map<String, Any>,
        completion: (String?) -> Unit
    )

    // ─── Workday generation reads/writes ─────────────────────────────────────
    //
    // Generation derives a workday from raw `attendances`, the driver's optional
    // `workday_plans` entry, and any workday already stored for that day. Each is a
    // distinct collection and query shape, so they cannot ride on the existing
    // decision-field read.

    fun fetchAttendanceEventsInRange(
        driverUid: String,
        startMillis: Long,
        endMillis: Long,
        completion: (List<IOSAttendanceEventRecord>?, String?) -> Unit
    )

    fun fetchWorkdayPlan(
        driverUid: String,
        dateEpochDay: Int,
        completion: (IOSWorkdayPlanFields?, String?) -> Unit
    )

    fun fetchExistingWorkdaysForDay(
        driverUid: String,
        dateEpochDay: Int,
        completion: (List<IOSExistingWorkdayFields>?, String?) -> Unit
    )

    /**
     * Set-with-merge upsert of a generated workday.
     *
     * Every branch decision (whether the computation changed, whether approval state
     * must be reset, whether this is a create) is made on the Kotlin side to stay in
     * lockstep with Android; the flags below only select which keys get written.
     * A null `plannedStartMillis`/`plannedEndMillis` means "delete that field",
     * matching Android's `?: FieldValue.delete()`.
     */
    fun upsertGeneratedWorkday(
        documentId: String,
        driverUid: String,
        dateEpochDay: Int,
        clockInMillis: Long,
        clockOutMillis: Long,
        totalMinutes: Int,
        overtimeMinutes: Int,
        plannedStartMillis: Long?,
        plannedEndMillis: Long?,
        planSource: String,
        deviationFlags: List<String>,
        reviewHold: Boolean,
        updatedAtMillis: Long,
        createdAtMillis: Long?,
        setStatusPending: Boolean,
        clearApprovalFields: Boolean,
        writeSystemClockCorrection: Boolean,
        completion: (String?) -> Unit
    )

    /**
     * Set-with-merge upsert of a `workday_plans` document.
     *
     * `createdAtMillis`/`createdByUid` are non-null only when the document does not
     * exist yet — Android stamps creation metadata once and never rewrites it on a
     * subsequent edit. The existence check itself reuses [fetchWorkdayPlan].
     */
    fun upsertWorkdayPlanDocument(
        documentId: String,
        driverUid: String,
        dateEpochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String,
        createdAtMillis: Long?,
        createdByUid: String?,
        completion: (String?) -> Unit
    )
}

data class IOSAttendanceEventRecord(
    val timestampMillis: Long,
    val type: String?,
    val note: String?
)

/**
 * Only existence and the planned window are modelled: Android builds a full
 * `WorkdayPlan` here but reads nothing beyond `plannedStartMillis`/`plannedEndMillis`
 * and whether the plan exists at all.
 */
data class IOSWorkdayPlanFields(
    val exists: Boolean,
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?
)

data class IOSExistingWorkdayFields(
    val documentId: String,
    val clockInMillis: Long?,
    val clockOutMillis: Long?,
    val totalMinutes: Long?,
    val overtimeMinutes: Long?,
    val status: String?,
    val approvedByExpat: Boolean?,
    val approvedByUid: String?,
    val rejectedByUid: String?,
    val rejectedAtMillis: Long?,
    val rejectReason: String?,
    val rejectionNote: String?
)

/**
 * Raw `workdays/{id}` shape needed by the bulk approval precheck.
 *
 * Each of the three precheck-relevant fields is reported twice on purpose:
 * `*Present` says the key exists in the document at all, while the typed value is
 * non-null only when it also has the expected type. That pair is what lets the
 * Kotlin side reproduce Android's `missing` vs `wrongType` split exactly.
 */
data class IOSWorkdayDecisionFields(
    val exists: Boolean,
    val allKeys: List<String>,
    val driverUidPresent: Boolean,
    val driverUid: String?,
    val dateEpochDayPresent: Boolean,
    val dateEpochDay: Long?,
    val totalMinutesPresent: Boolean,
    val totalMinutes: Long?,
    // Admin override needs the prior decision state to derive previousStatus,
    // previousFinalized, and rejectReasonChanged. Same document and same
    // server-sourced read as the bulk precheck, so these ride along here rather
    // than in a second near-identical read method.
    val status: String?,
    val rejectReason: String?,
    val approvedByUid: String?,
    val rejectedByUid: String?
)

interface IOSAnnouncementPlatformBridge {
    fun observeAnnouncements(
        limit: Int,
        onChange: (List<IOSAnnouncementPayload>?, String?) -> Unit
    ): IOSListenerRegistration

    fun createAnnouncement(
        title: String,
        body: String,
        completion: (String?) -> Unit
    )

    fun updateAnnouncement(
        id: String,
        title: String,
        body: String,
        completion: (String?) -> Unit
    )

    fun deleteAnnouncement(
        id: String,
        completion: (String?) -> Unit
    )
}

interface IOSAttendancePlatformBridge {
    fun checkIn(
        driverUid: String,
        timestampMillis: Long,
        note: String?,
        latitude: Double?,
        longitude: Double?,
        locationName: String?,
        completion: (String?) -> Unit
    )

    fun checkOut(
        timestampMillis: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        completion: (String?) -> Unit
    )

    fun upsertClockIn(
        docId: String,
        driverUid: String,
        epochDay: Long,
        nowMillis: Long,
        completion: (IOSAttendanceDailyPayload?, String?) -> Unit
    )

    fun upsertClockOut(
        docId: String,
        driverUid: String,
        epochDay: Long,
        nowMillis: Long,
        completion: (IOSAttendanceDailyPayload?, String?) -> Unit
    )

    fun getTodayAttendance(
        docId: String,
        driverUid: String,
        epochDay: Long,
        completion: (IOSAttendanceDailyPayload?, String?) -> Unit
    )

    fun getAttendancesByRange(
        driverUid: String,
        startMillis: Long,
        endMillisExclusive: Long,
        completion: (List<IOSAttendanceEventPayload>?, String?) -> Unit
    )

    fun getToday(
        driverUid: String,
        completion: (IOSAttendanceEventPayload?, String?) -> Unit
    )
}

interface IOSLocationPlatformBridge {
    fun observeAllDriverLocations(onChange: (List<IOSLocationPayload>) -> Unit): () -> Unit
    fun observeDriverLocation(driverUid: String, onChange: (IOSLocationPayload?) -> Unit): () -> Unit

    /**
     * Tulis `locations/{uid}`.
     *
     * [fields] datang sudah jadi dari `buildLocationWritePayload` di commonMain — Swift TIDAK
     * boleh menambah atau membuang kunci. `firestore.rules` memvalidasi daftar kunci dengan
     * `hasOnlyAllowed`, jadi satu kunci tambahan membuat seluruh tulisan ditolak
     * PERMISSION_DENIED, yang terbaca seperti masalah izin alih-alih masalah bentuk dokumen.
     */
    fun writeDriverLocation(
        path: String,
        fields: Map<String, Any>,
        completion: (String?) -> Unit
    )

    /** Hapus `locations/{uid}` — driver berhenti berbagi lokasi. */
    fun deleteDriverLocation(path: String, completion: (String?) -> Unit)

    /** UID user yang sedang login, atau null. Dipakai untuk memenuhi syarat rules. */
    fun currentAuthUid(): String?
}

interface IOSChecklistPlatformBridge {
    fun hasChecklist(
        docId: String,
        completion: (Boolean, String?) -> Unit
    )

    /**
     * [completion] carries the real [AlreadySubmittedException] instance for the
     * duplicate-submission case instead of a bare error-message string — Swift
     * constructs it directly (Kotlin/Native exposes a public initializer for any
     * public commonMain class) so the type survives the boundary. [String] stays
     * for every other, non-distinguished failure. Exactly one of the two
     * parameters is non-null on failure; both are null on success.
     */
    fun submitChecklist(
        payload: IOSChecklistPayload,
        completion: (AlreadySubmittedException?, String?) -> Unit
    )
}

interface IOSWorkCalendarPlatformBridge {
    fun getWorkCalendar(
        year: Int,
        completion: (List<IOSWorkCalendarDayPayload>?, String?) -> Unit
    )

    /**
     * Set-with-merge upsert of a `work_calendars/{year}` document.
     *
     * Every parameter mirrors a field FirestoreWorkCalendarRepository.toDto()/toMap()
     * (androidMain) writes — same document shape, so Android's getYear can read a
     * calendar an iOS admin edits, and vice versa. Note `weekendDefault` has no
     * corresponding field on [IOSWorkCalendarPlatformBridge] because Android itself
     * never derives it from the domain model either — it hardcodes the constant
     * "OFF" in every write, so the Swift implementation does the same.
     */
    fun upsertWorkCalendar(
        year: Int,
        timeZoneId: String,
        updatedAtMillis: Long,
        weekdayStartMin: Int,
        weekdayEndMin: Int,
        weekdayBreakMin: Int,
        fridayStartMin: Int,
        fridayEndMin: Int,
        fridayBreakMin: Int,
        overrides: List<IOSWorkCalendarOverrideWritePayload>,
        completion: (String?) -> Unit
    )
}

/**
 * One `overrides[]` entry as Android's FirestoreWorkdayOverrideDto.toMap() writes it.
 * `hasWorkSpec` selects whether the workSpec* fields are meaningful — only a CUSTOM
 * override carries a rule override on Android; HOLIDAY/WORKDAY_SWAP write `workSpec:
 * null`. The three workSpec fields are still always present (not optional) to avoid
 * extra Kotlin/Swift nullable-boxing for a value only read when hasWorkSpec is true.
 */
data class IOSWorkCalendarOverrideWritePayload(
    val dateEpochDay: Long,
    val kind: String,
    val hasWorkSpec: Boolean,
    val workSpecStartMin: Int,
    val workSpecEndMin: Int,
    val workSpecBreakMin: Int,
    val note: String?,
    val updatedAtMillis: Long
)

data class IOSAdminAttendancePayload(
    val id: String,
    val driverUid: String,
    val timestampMillis: Long,
    val type: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?
)

data class IOSAdminChecklistSummaryPayload(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val hasIssue: Boolean,
    /**
     * Kode mentah `followUpStatus` dari dokumen Firestore ("new"/"reviewed"/"closed"),
     * null bila field tidak ada (dokumen lama). Pemetaan ke enum ada di Kotlin lewat
     * `ChecklistFollowUpStatus.fromCode` — Swift tidak boleh memutuskan sendiri.
     */
    val followUpStatusRaw: String?
)

data class IOSAdminSafetySummaryPayload(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val fitToWork: Boolean,
    val confirmed: Boolean
)

data class IOSAdminWorkdayPayload(
    val id: String,
    val driverUid: String,
    val dateEpochDay: Int,
    val clockInMillis: Long,
    val clockOutMillis: Long,
    val totalMinutes: Int,
    val overtimeMinutes: Int,
    val driverName: String?,
    val status: String?,
    val approvedByExpat: Boolean?,
    val approvedByUid: String?,
    val approvedAtMillis: Long?,
    val approvalNote: String?,
    val rejectedByUid: String?,
    val rejectedAtMillis: Long?,
    val rejectReason: String?,
    val rejectionNote: String?,
    val adjustedBy: String?,
    val adjustedAt: Long?,
    val adjustedReason: String?,
    val hasChecklist: Boolean?,
    val checklistSubmittedAt: Long?,
    val hasSafetyDeclaration: Boolean?,
    val safetySubmittedAt: Long?,
    val approverUid: String?,
    val approverName: String?,
    val plannedStartMillis: Long?,
    /**
     * Map mentah `decisionTrail` dari dokumen (Blok 4.2). Diteruskan MENTAH ke Kotlin dan
     * dipetakan lewat `mapWorkdayDecisionTrail` commonMain — Swift tidak boleh memutuskan
     * sendiri mana yang langkah sah.
     */
    val decisionTrail: Map<*, *>?,
    val plannedEndMillis: Long?,
    val planSourceRaw: String?,
    val deviationFlagsRaw: List<String>?,
    val reviewHold: Boolean?
)

data class IOSAdminLocationPayload(
    val driverUid: String,
    val latitude: Double?,
    val longitude: Double?,
    val speedMps: Double?,
    val speedKph: Double?,
    val updatedAtMillis: Long,
    val locationName: String?
)

data class IOSAdminChecklistDetailPayload(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val hasIssue: Boolean,
    val vehicleId: String?,
    val odometerKm: Int?,
    val fuelLevel: String?,
    val tiresFrontLeftOk: Boolean?,
    val tiresFrontRightOk: Boolean?,
    val tiresRearLeftOk: Boolean?,
    val tiresRearRightOk: Boolean?,
    val tirePressureNote: String?,
    val brakesOk: Boolean?,
    val lightsOk: Boolean?,
    val hornOk: Boolean?,
    val wipersOk: Boolean?,
    val mirrorsOk: Boolean?,
    val seatbeltOk: Boolean?,
    val engineOilOk: Boolean?,
    val coolantOk: Boolean?,
    val batteryOk: Boolean?,
    val cleanlinessOk: Boolean?,
    val documentsOk: Boolean?,
    val fireExtinguisherOk: Boolean?,
    val firstAidOk: Boolean?,
    val warningTriangleOk: Boolean?,
    // Dua field yang ditambahkan PR #225 ke Checklist commonMain. Sudah lama ada di
    // write-path driver (PR #229) tapi read-path admin ini terlewat, sehingga admin iOS
    // melihat 18 item sementara Android merender 20 — senyap, bukan crash.
    val turnSignalBrakeLightsOk: Boolean?,
    val washerFluidOk: Boolean?,
    val notes: String?,
    /** Blok 4.1 — tindak lanjut temuan. Kode mentah "new"/"reviewed"/"closed". */
    val followUpStatusRaw: String?,
    val followUpOwnerUid: String?,
    val followUpNote: String?,
    val followUpUpdatedAtMillis: Long?
)

data class IOSAdminSafetyDetailPayload(
    val id: String,
    val driverUid: String,
    val driverName: String?,
    val driverEmail: String?,
    val dateEpochDay: Int,
    val submittedAt: Long,
    val fitToWork: Boolean,
    val sleepHours: Int?,
    val noAlcoholOrDrugs: Boolean,
    val noSevereStress: Boolean,
    val willFollowSpeedLimit: Boolean,
    val willFollowRestBreaks: Boolean,
    val willNotUsePhoneWhileDriving: Boolean,
    val willWearSeatbelt: Boolean,
    val hazardAwareness: String?,
    val weatherCondition: String?,
    val routeRiskNote: String?,
    val incidentReport: String?,
    val confirmed: Boolean
)

interface IOSAdminDataPlatformBridge {
    fun getAdminAttendancesByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int,
        completion: (List<IOSAdminAttendancePayload>?, String?) -> Unit
    )

    fun getAdminChecklistsByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int,
        completion: (List<IOSAdminChecklistSummaryPayload>?, String?) -> Unit
    )

    fun getAdminSafetyByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int,
        completion: (List<IOSAdminSafetySummaryPayload>?, String?) -> Unit
    )

    fun getAdminWorkdaysByRange(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String?,
        limit: Int,
        completion: (List<IOSAdminWorkdayPayload>?, String?) -> Unit
    )

    /**
     * Tulis tindak lanjut temuan checklist (Blok 4.1) — padanan write Android di
     * `FirebaseAdminDataRepository.updateChecklistFollowUp`. Payload dirakit PENUH di
     * Kotlin (`buildChecklistFollowUpPayload` + `updatedAt`); Swift hanya menulis map
     * yang diberikan dengan merge — nol nama field Firestore dieja ulang di Swift.
     */
    fun applyChecklistFollowUp(
        checklistId: String,
        payload: Map<String, Any>,
        completion: (String?) -> Unit
    )

    fun getAdminLocations(
        limit: Int,
        completion: (List<IOSAdminLocationPayload>?, String?) -> Unit
    )

    fun getAdminAttendanceById(
        id: String,
        completion: (IOSAdminAttendancePayload?, String?) -> Unit
    )

    fun getAdminChecklistDetail(
        id: String,
        completion: (IOSAdminChecklistDetailPayload?, String?) -> Unit
    )

    fun getAdminSafetyDetail(
        id: String,
        completion: (IOSAdminSafetyDetailPayload?, String?) -> Unit
    )

    fun getAdminWorkdayById(
        id: String,
        completion: (IOSAdminWorkdayPayload?, String?) -> Unit
    )

    fun getAdminLocationByUid(
        uid: String,
        completion: (IOSAdminLocationPayload?, String?) -> Unit
    )
}

data class IOSSafetyPayload(
    val fitToWork: Boolean,
    val noAlcoholOrDrugs: Boolean,
    val noSevereStress: Boolean,
    val willFollowSpeedLimit: Boolean,
    val willFollowRestBreaks: Boolean,
    val willNotUsePhoneWhileDriving: Boolean,
    val willWearSeatbelt: Boolean,
    val confirmed: Boolean,
    val sleepHours: Int?,
    val hazardAwareness: String?,
    val weatherCondition: String?,
    val routeRiskNote: String?,
    val incidentReport: String?
)

interface IOSSafetyPlatformBridge {
    fun hasSafetyDeclaration(
        docId: String,
        completion: (Boolean, String?) -> Unit
    )

    fun submitSafetyDeclaration(
        payload: IOSSafetyPayload,
        completion: (String?) -> Unit
    )
}

data class IOSChatMessagePayload(
    val id: String,
    val threadId: String,
    val senderUid: String,
    val senderName: String?,
    val body: String,
    val createdAtMillis: Long
)

interface IOSChatPlatformBridge {
    fun observeMessages(
        threadId: String,
        onChange: (List<IOSChatMessagePayload>?, String?) -> Unit
    ): IOSListenerRegistration

    fun sendMessage(
        threadId: String,
        senderUid: String,
        senderName: String?,
        body: String,
        completion: (String?) -> Unit
    )
}

data class IOSUserDirectoryDriverPayload(
    val uid: String,
    val displayName: String,
    val email: String?,
    val vehicleNumber: String?,
    val sopAccepted: Boolean,
    val sopAcceptedAtMillis: Long?
)

/**
 * A chat counterparty. Carries [roleRaw] (the Firestore `type` value) so the
 * contact list can label the row — the driver-roster payload above cannot, which
 * is how iOS ended up offering role-incorrect contacts.
 */
data class IOSUserDirectoryContactPayload(
    val uid: String,
    val displayName: String,
    val email: String?,
    val roleRaw: String
)

interface IOSUserDirectoryPlatformBridge {
    fun getDriverList(completion: (List<IOSUserDirectoryDriverPayload>?, String?) -> Unit)
    fun getManagedDrivers(
        managerUid: String,
        completion: (List<IOSUserDirectoryDriverPayload>?, String?) -> Unit
    )

    /**
     * Queries `users` where `type` is in [rolesRaw] (lowercase Firestore values:
     * "admin" / "driver" / "expat"), mirroring Android's `getUsersByRoles`.
     *
     * This exists because `firestore.rules:194-203` (`validThreadParticipantPair`)
     * requires exactly one thread participant to be an admin. Building a chat
     * contact list from the *driver roster* — as iOS previously did for every role —
     * produces driver↔driver and expat↔driver threads that Firestore rejects.
     */
    fun getUsersByRoles(
        rolesRaw: List<String>,
        completion: (List<IOSUserDirectoryContactPayload>?, String?) -> Unit
    )

    fun getSopStatus(uid: String, completion: (Boolean, Long?, String?) -> Unit)
    fun acceptSop(uid: String, acceptedAtMillis: Long, completion: (String?) -> Unit)
    fun getUserDisplayName(uid: String, completion: (String?, String?) -> Unit)

    /**
     * Baca `deletion_requests/{uid}` MENTAH — gerbang toko aplikasi (Blok 5, PRD §20).
     *
     * ## Kenapa `Map` mentah, bukan payload bertipe
     * Bridge iOS lain memakai data class payload (`IOSUserDirectoryDriverPayload`, dst.), dan
     * di sini itu justru pilihan yang salah: `mapAccountDeletionRequest` di commonMain sudah
     * memegang pemetaan dokumen → model, lengkap dengan aturan fail-closed-nya (status asing
     * jadi `null`, bukan diam-diam `PENDING`). Payload bertipe memaksa pemetaan itu ditulis
     * ULANG di Swift, dan dua tempat yang bisa berbeda pendapat soal status mana yang sah
     * adalah bentuk drift termahal yang bisa dibuat di jalur penghapusan akun. Android
     * memanggil fungsi commonMain yang sama; iOS harus juga.
     *
     * ## Tiga keadaan, dua parameter
     * - `(map, null)` — dokumen ada.
     * - `(null, null)` — dokumen **tidak ada**. Ini keadaan normal, bukan galat: mayoritas
     *   pengguna tidak pernah meminta penghapusan akun.
     * - `(null, error)` — pembacaan gagal.
     *
     * Sengaja TIDAK memakai parameter `Boolean exists` terpisah: `Boolean` di dalam tipe
     * lambda dibungkus jadi `KotlinBoolean` saat menyeberang ke Swift, dan itu jebakan interop
     * yang sudah pernah memakan waktu di repo ini. Dua parameter nullable cukup, dan
     * `mapAccountDeletionRequest(null)` memang sudah mengembalikan `null`.
     */
    fun getAccountDeletionRequest(
        uid: String,
        completion: (Map<String, Any>?, String?) -> Unit
    )

    /**
     * Tulis `deletion_requests/{uid}` — payload dibangun di commonMain
     * (`buildAccountDeletionRequestPayload`), BUKAN di Swift.
     *
     * **`merge = false` wajib**, mencermin `FirebaseAccountDeletionRepository` Android:
     * permintaan ulang setelah yang lama ditangani harus MENGGANTI dokumen. `merge = true`
     * meninggalkan `handledByUid`/`handledAtMillis` lama sehingga permintaan baru lahir
     * seolah sudah ditangani — bentuk yang juga ditolak rules, tapi ditolak dengan pesan izin
     * yang menyesatkan pembacanya.
     */
    fun submitAccountDeletionRequest(
        uid: String,
        fields: Map<String, Any>,
        completion: (String?) -> Unit
    )
}

/**
 * Penugasan expat untuk driver — padanan iOS untuk fitur R0 Android
 * (`AdminDriverAssignmentScreen`).
 *
 * Dua mekanisme berbeda, sengaja dipisah dan JANGAN digabung:
 *  - **Permanent manager** disimpan di `users/{driverUid}.managerUid`. Hanya admin yang
 *    boleh menulisnya (blok `users/{uid}` di `firestore.rules`).
 *  - **Daily override** adalah dokumen `driver_assignments/{driverUid}_{epochDay}`, berlaku
 *    satu hari penuh, dan MENANG atas permanent manager untuk hari itu.
 *
 * Resolusinya sendiri (`ResolveExpatForDriverUseCase`) ada di commonMain, bukan di rules
 * dan bukan di sini.
 */
interface IOSDriverAssignmentPlatformBridge {
    /** Tulis `users/{driverUid}.managerUid`. Admin-only menurut rules. */
    fun setPermanentManager(driverUid: String, expatUid: String, completion: (String?) -> Unit)

    /** Baca `users/{driverUid}.managerUid`. null berarti belum pernah dipasangkan. */
    fun getPermanentManager(driverUid: String, completion: (String?, String?) -> Unit)

    /**
     * Tulis satu override harian.
     *
     * `docId` dihitung di Kotlin dan diserahkan apa adanya — rules menuntut doc-id
     * KANONIK `{driverUid}_{epochDay}` (`isCanonicalAssignmentDocId`), jadi kalau Swift
     * merakit sendiri dan sedikit berbeda, tulisannya ditolak dengan PERMISSION_DENIED
     * yang terlihat seperti masalah izin, bukan masalah penamaan.
     *
     * **Kontrak untuk implementor Swift:** baca dokumennya dulu dan sertakan
     * `createdAt = createdAtMillis` HANYA bila belum ada, lalu tulis dengan merge. Itu
     * yang dilakukan Android; menyertakannya selalu akan menggeser waktu pembuatan setiap
     * kali override yang sama ditimpa dan menghapus jejak "sejak kapan penugasan ini ada".
     */
    fun upsertDailyOverride(
        docId: String,
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reasonRaw: String,
        epochDay: Int,
        createdAtMillis: Long,
        completion: (String?) -> Unit
    )

    fun getDailyOverride(
        docId: String,
        completion: (IOSDriverAssignmentPayload?, String?) -> Unit
    )

    /** Semua override pada satu hari lintas driver — untuk layar Admin. */
    fun getOverridesForDate(
        epochDay: Int,
        completion: (List<IOSDriverAssignmentPayload>?, String?) -> Unit
    )
}

data class IOSDriverAssignmentPayload(
    val driverUid: String,
    val expatUid: String,
    val assignedBy: String,
    val reasonRaw: String,
    val epochDay: Int,
    val createdAtMillis: Long?
)

/**
 * Satu dokumen `speed_violations` **apa adanya** dari Firestore, beserta id dokumennya.
 *
 * Setiap field nullable, termasuk [driverUid] — dan itu justru intinya. Sisi Swift
 * mengembalikan SATU payload per dokumen tanpa menyaring apa pun, karena
 * `SpeedViolationPage` menentukan "terpotong" dan "digugurkan" dari jumlah dokumen
 * MENTAH. Kalau Swift menggugurkan lebih dulu (pola `compactMap` di
 * `getAdminAttendancesByRange`), kedua angka itu salah tepat pada kasus paling
 * berisiko: jendela penuh DAN datanya berantakan.
 *
 * Penyaringan yang sah ada di `IOSSpeedViolationRepository.toRecordOrNull`, yang
 * mencerminkan `mapSpeedViolationRecordOrNull` di commonMain.
 */
data class IOSSpeedViolationDocPayload(
    val id: String,
    val driverUid: String?,
    val speedKph: Double?,
    val speedLimitKph: Double?,
    val occurredAtMillis: Long?,
    val latitude: Double?,
    val longitude: Double?
)

/**
 * R3 — konfigurasi batas kecepatan dan pelaporan pelanggaran.
 *
 * Dipisah dari bridge lokasi: yang satu membaca config global (`app_config/safety`,
 * admin-write), yang lain menulis event milik driver (`speed_violations`, append-only).
 * Menggabungkannya akan mengaburkan bahwa keduanya tunduk pada aturan rules yang berbeda.
 */
interface IOSSafetyPlatformConfigBridge {
    /**
     * Baca `app_config/safety`. `null` bila dokumennya belum ada — itu keadaan SAH,
     * bukan error, dan pemanggil yang memutuskan fail-closed (deteksi mati).
     */
    fun getSpeedLimitConfig(completion: (Double?, Boolean, Boolean, String?) -> Unit)

    /**
     * Tambah dokumen ke `speed_violations` (append-only, `addDocument`).
     *
     * [fields] datang sudah jadi dari commonMain; rules menuntut
     * `driverUid == request.auth.uid` dan menolak kunci di luar daftar yang diizinkan.
     */
    fun addSpeedViolation(fields: Map<String, Any>, completion: (String?) -> Unit)

    /**
     * Baca `speed_violations` sejak [sinceMillis], terbaru lebih dulu, maksimal [limit].
     *
     * Ditaruh di interface ini, bukan di interface baru: koleksinya SAMA dengan yang
     * ditulis [addSpeedViolation], dan `IOSPlatformBridgeRegistry` sudah punya rantai cast
     * tersendiri untuk `safetyConfigBridge`. Interface baru berarti rantai cast baru —
     * footgun R3 yang sudah menggigit dua kali (lihat komentar di `install`).
     *
     * Mengembalikan SATU payload per dokumen; Swift TIDAK menyaring. Alasannya di KDoc
     * [IOSSpeedViolationDocPayload].
     *
     * Satu penyaring field, dan field-nya SAMA dengan yang di-`orderBy`, jadi tidak butuh
     * indeks komposit. **Jangan menambah penyaring `driverUid` di sini** — indeksnya tidak
     * ada di production, jadi query-nya akan gagal di perangkat setelah lolos semua gate
     * di mesin ini. Lihat KDoc `SpeedViolationRepository.listRecentViolations`.
     */
    fun listSpeedViolations(
        sinceMillis: Long,
        limit: Int,
        completion: (List<IOSSpeedViolationDocPayload>?, String?) -> Unit
    )
}

/**
 * Token FCM privat iOS: dokumen `fcm_tokens/{sha256Hex(token)}` — padanan
 * `FirebasePushTokenRepository` Android. Menggantikan tulis legacy `users.fcmToken`
 * (sudah dicabut).
 *
 * **Bentuk dokumen dirakit Swift**, bukan dikirim sebagai map dari Kotlin: `updatedAt` harus
 * `FieldValue.serverTimestamp()` (rules: `updatedAt == request.time`) dan id dokumen dihitung
 * dengan CryptoKit (`sha256Hex` hanya ada di androidMain). Rules `validFcmTokenDoc` menolak
 * kunci di luar `uid`, `token`, `platform`, `appVersion`, `source`, `updatedAt`; Swift wajib
 * menulis `platform: "ios"` dan `source: "client"`, tidak lebih.
 *
 * Interface baru, tetapi di-wire lewat rantai cast di [IOSPlatformBridgeRegistry.install],
 * bukan parameter baru — signature [installIOSPlatformBridges] tidak berubah.
 */
interface IOSPushTokenPlatformBridge {
    /** Buat/perbarui `fcm_tokens/{sha256Hex(token)}` milik [uid]. [appVersion] sudah ≤ 32 karakter. */
    fun registerPushToken(
        uid: String,
        token: String,
        appVersion: String,
        completion: (String?) -> Unit
    )

    /** Hapus `fcm_tokens/{docId}`. Harus dipanggil SELAGI masih login (rules: pemilik dokumen). */
    fun unregisterPushToken(docId: String, completion: (String?) -> Unit)
}

object IOSPlatformBridgeRegistry {
    var authBridge: IOSAuthPlatformBridge? = null
        private set

    var workdayBridge: IOSWorkdayPlatformBridge? = null
        private set

    var announcementBridge: IOSAnnouncementPlatformBridge? = null
        private set

    var attendanceBridge: IOSAttendancePlatformBridge? = null
        private set

    var locationBridge: IOSLocationPlatformBridge? = null
        private set

    var checklistBridge: IOSChecklistPlatformBridge? = null
        private set

    var workCalendarBridge: IOSWorkCalendarPlatformBridge? = null
        private set

    var safetyBridge: IOSSafetyPlatformBridge? = null
        private set

    var userDirectoryBridge: IOSUserDirectoryPlatformBridge? = null
        private set

    var chatBridge: IOSChatPlatformBridge? = null
        private set

    var adminDataBridge: IOSAdminDataPlatformBridge? = null
        private set

    var driverAssignmentBridge: IOSDriverAssignmentPlatformBridge? = null
        private set

    var safetyConfigBridge: IOSSafetyPlatformConfigBridge? = null
        private set

    var pushTokenBridge: IOSPushTokenPlatformBridge? = null
        private set

    fun install(
        authBridge: IOSAuthPlatformBridge,
        workdayBridge: IOSWorkdayPlatformBridge,
        announcementBridge: IOSAnnouncementPlatformBridge,
        attendanceBridge: IOSAttendancePlatformBridge,
        checklistBridge: IOSChecklistPlatformBridge,
        safetyBridge: IOSSafetyPlatformBridge,
        userDirectoryBridge: IOSUserDirectoryPlatformBridge,
        chatBridge: IOSChatPlatformBridge
    ) {
        this.authBridge = authBridge
        this.workdayBridge = workdayBridge
        this.announcementBridge = announcementBridge
        this.attendanceBridge = attendanceBridge
        this.locationBridge = authBridge as? IOSLocationPlatformBridge
            ?: workdayBridge as? IOSLocationPlatformBridge
            ?: announcementBridge as? IOSLocationPlatformBridge
            ?: attendanceBridge as? IOSLocationPlatformBridge
            ?: checklistBridge as? IOSLocationPlatformBridge
            ?: safetyBridge as? IOSLocationPlatformBridge
            ?: userDirectoryBridge as? IOSLocationPlatformBridge
        this.checklistBridge = checklistBridge
        this.workCalendarBridge = authBridge as? IOSWorkCalendarPlatformBridge
            ?: workdayBridge as? IOSWorkCalendarPlatformBridge
            ?: announcementBridge as? IOSWorkCalendarPlatformBridge
            ?: attendanceBridge as? IOSWorkCalendarPlatformBridge
            ?: checklistBridge as? IOSWorkCalendarPlatformBridge
            ?: safetyBridge as? IOSWorkCalendarPlatformBridge
            ?: userDirectoryBridge as? IOSWorkCalendarPlatformBridge
        // Ditambahkan belakangan, jadi di-wire lewat cast seperti locationBridge dan
        // workCalendarBridge di atas — bukan sebagai parameter baru. Itu membuat
        // signature installIOSPlatformBridges (dan pemanggilnya di iOSApp.swift) tidak
        // berubah sama sekali; `IOSFirebasePlatformService` sudah satu objek yang
        // mengimplementasikan semua interface bridge.
        this.driverAssignmentBridge = authBridge as? IOSDriverAssignmentPlatformBridge
            ?: workdayBridge as? IOSDriverAssignmentPlatformBridge
            ?: announcementBridge as? IOSDriverAssignmentPlatformBridge
            ?: attendanceBridge as? IOSDriverAssignmentPlatformBridge
            ?: checklistBridge as? IOSDriverAssignmentPlatformBridge
            ?: safetyBridge as? IOSDriverAssignmentPlatformBridge
            ?: userDirectoryBridge as? IOSDriverAssignmentPlatformBridge
            ?: chatBridge as? IOSDriverAssignmentPlatformBridge
        // R3. Rantai TERPISAH — menyisipkannya di tengah rantai lain membuat tipe
        // gabungannya jadi Any? (sudah kena dua kali; ditangkap compile iOS di Linux).
        this.safetyConfigBridge = authBridge as? IOSSafetyPlatformConfigBridge
            ?: workdayBridge as? IOSSafetyPlatformConfigBridge
            ?: announcementBridge as? IOSSafetyPlatformConfigBridge
            ?: attendanceBridge as? IOSSafetyPlatformConfigBridge
            ?: checklistBridge as? IOSSafetyPlatformConfigBridge
            ?: safetyBridge as? IOSSafetyPlatformConfigBridge
            ?: userDirectoryBridge as? IOSSafetyPlatformConfigBridge
            ?: chatBridge as? IOSSafetyPlatformConfigBridge
        // Token FCM privat. Rantai TERPISAH, alasan sama dengan safetyConfigBridge di atas.
        // null sampai `IOSFirebasePlatformService` (Swift) mengimplementasikan interface ini;
        // IOSPushTokenRepository lalu gagal non-fatal, bukan crash.
        this.pushTokenBridge = authBridge as? IOSPushTokenPlatformBridge
            ?: workdayBridge as? IOSPushTokenPlatformBridge
            ?: announcementBridge as? IOSPushTokenPlatformBridge
            ?: attendanceBridge as? IOSPushTokenPlatformBridge
            ?: checklistBridge as? IOSPushTokenPlatformBridge
            ?: safetyBridge as? IOSPushTokenPlatformBridge
            ?: userDirectoryBridge as? IOSPushTokenPlatformBridge
            ?: chatBridge as? IOSPushTokenPlatformBridge
        this.safetyBridge = safetyBridge
        this.userDirectoryBridge = userDirectoryBridge
        this.chatBridge = chatBridge
        this.adminDataBridge = authBridge as? IOSAdminDataPlatformBridge
            ?: workdayBridge as? IOSAdminDataPlatformBridge
            ?: announcementBridge as? IOSAdminDataPlatformBridge
            ?: attendanceBridge as? IOSAdminDataPlatformBridge
            ?: checklistBridge as? IOSAdminDataPlatformBridge
            ?: safetyBridge as? IOSAdminDataPlatformBridge
            ?: userDirectoryBridge as? IOSAdminDataPlatformBridge
            ?: chatBridge as? IOSAdminDataPlatformBridge
    }
}

fun installIOSPlatformBridges(
    authBridge: IOSAuthPlatformBridge,
    workdayBridge: IOSWorkdayPlatformBridge,
    announcementBridge: IOSAnnouncementPlatformBridge,
    attendanceBridge: IOSAttendancePlatformBridge,
    checklistBridge: IOSChecklistPlatformBridge,
    safetyBridge: IOSSafetyPlatformBridge,
    userDirectoryBridge: IOSUserDirectoryPlatformBridge,
    chatBridge: IOSChatPlatformBridge
) {
    IOSPlatformBridgeRegistry.install(
        authBridge = authBridge,
        workdayBridge = workdayBridge,
        announcementBridge = announcementBridge,
        attendanceBridge = attendanceBridge,
        checklistBridge = checklistBridge,
        safetyBridge = safetyBridge,
        userDirectoryBridge = userDirectoryBridge,
        chatBridge = chatBridge
    )
}
