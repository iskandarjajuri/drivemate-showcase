package com.drivemate.showcase.feature.expat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.report.GetWorkdaySummaryUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.util.safeDriverLabel
import com.drivemate.showcase.util.UiErrorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

data class ManagedDriverSummaryUi(
    val uid: String,
    val displayName: String,
    val email: String?,
    val totalDays: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val pendingCount: Int,
    val lastEpochDay: Int?
)

data class ExpatSummaryHeader(
    val totalDrivers: Int,
    val pendingTotal: Int,
    val lastRefreshMillis: Long
)

/** Jumlah baris maksimum pada kartu "Perlu perhatian" di beranda. */
const val EXPAT_HOME_ATTENTION_LIMIT = 3

/**
 * Data turunan beranda Expat. Semuanya dihitung dari daftar yang SUDAH dimuat
 * (tanpa panggilan jaringan/use case baru).
 */
data class ExpatHomeDerived(
    val reviewedTotal: Int = 0,
    /** Driver dengan pending terbanyak (seri: hari terakhir terbaru); null bila tak ada pending. */
    val topPendingDriver: ManagedDriverSummaryUi? = null,
    /** Driver yang masih punya pending, terurut menurun, dibatasi [EXPAT_HOME_ATTENTION_LIMIT]. */
    val attention: List<ManagedDriverSummaryUi> = emptyList()
)

/** Fungsi MURNI: urutan pending desc, lalu hari terakhir desc, lalu nama. */
fun deriveExpatHome(drivers: List<ManagedDriverSummaryUi>): ExpatHomeDerived {
    val pending = drivers
        .filter { it.pendingCount > 0 }
        .sortedWith(
            compareByDescending<ManagedDriverSummaryUi> { it.pendingCount }
                .thenByDescending { it.lastEpochDay ?: Int.MIN_VALUE }
                .thenBy { it.displayName.lowercase() }
        )
    return ExpatHomeDerived(
        reviewedTotal = drivers.sumOf { it.approvedCount + it.rejectedCount },
        topPendingDriver = pending.firstOrNull(),
        attention = pending.take(EXPAT_HOME_ATTENTION_LIMIT)
    )
}

class ExpatHomeViewModel(
    private val getManagedDrivers: GetManagedDriversUseCase,
    private val getWorkdaySummary: GetWorkdaySummaryUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val authRepo: AuthRepository
) : ViewModel() {

    private companion object { const val TAG = "DM/ExpatHomeVM" }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error

    private val _drivers = MutableStateFlow<List<ManagedDriverSummaryUi>>(emptyList())
    val drivers: StateFlow<List<ManagedDriverSummaryUi>> = _drivers

    private val _header = MutableStateFlow(ExpatSummaryHeader(0, 0, 0))
    val header: StateFlow<ExpatSummaryHeader> = _header

    private val _derived = MutableStateFlow(ExpatHomeDerived())
    val derived: StateFlow<ExpatHomeDerived> = _derived

    /** Nama untuk sapaan header; null sampai pengguna aktif terbaca. */
    private val _greetingName = MutableStateFlow<String?>(null)
    val greetingName: StateFlow<String?> = _greetingName

    fun refresh() {
        loadDrivers()
    }

    fun loadDrivers() {
        if (_loading.value) return
        _loading.value = true
        _error.value = null
        val zone = TimeZone.of("Asia/Jakarta")
        val window = TimeUtil.rollingThreeMonthWindowEpochDays(zone)
        val startEpochDay = window.first
        val endEpochDay = window.last
        val now = TimeUtil.nowEpochMillis()

        viewModelScope.launch {
            val (authUid, roleLabel) = resolveAuthContext()
            when (val res = getManagedDrivers(authUid)) {
                is AppResult.Success -> {
                    val baseProfiles = res.data.map { profile ->
                        val name = safeDriverLabel(profile.name, profile.email)
                        profile.copy(name = name)
                    }
                    val summaries = coroutineScope {
                        baseProfiles.map { profile ->
                            async {
                                val summaryRes = getWorkdaySummary(
                                    driverUid = profile.uid,
                                    startEpochDay = startEpochDay,
                                    endEpochDay = endEpochDay
                                )
                                val summary = when (summaryRes) {
                                    is AppResult.Success -> summaryRes.data
                                    is AppResult.Failure -> null
                                }
                                logSummaryFetch(
                                    role = roleLabel,
                                    startEpochDay = startEpochDay,
                                    endEpochDay = endEpochDay,
                                    success = summaryRes is AppResult.Success,
                                    errorClass = if (summaryRes is AppResult.Failure) {
                                        summaryRes.throwable::class.simpleName ?: "Throwable"
                                    } else {
                                        "none"
                                    }
                                )
                                ManagedDriverSummaryUi(
                                    uid = profile.uid,
                                    displayName = safeDriverLabel(profile.name, profile.email),
                                    email = profile.email,
                                    totalDays = summary?.totalDays ?: 0,
                                    approvedCount = summary?.approvedCount ?: 0,
                                    rejectedCount = summary?.rejectedCount ?: 0,
                                    pendingCount = summary?.pendingCount ?: 0,
                                    lastEpochDay = summary?.lastEpochDay
                                )
                            }
                        }.awaitAll()
                    }
                    _drivers.value = summaries.sortedBy { it.displayName }
                    _derived.value = deriveExpatHome(summaries)
                    val pendingTotal = summaries.sumOf { it.pendingCount }
                    _header.value = ExpatSummaryHeader(
                        totalDrivers = summaries.size,
                        pendingTotal = pendingTotal,
                        lastRefreshMillis = now
                    )
                    logReview(
                        event = "drivers_fetch",
                        role = roleLabel,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        count = _drivers.value.size,
                        success = true,
                        errorClass = "none"
                    )
                }
                is AppResult.Failure -> {
                    _error.value = R.string.expat_error_driver_list_failed
                    UiErrorLogger.e(TAG, "drivers fetch failed", res.throwable)
                    logReview(
                        event = "drivers_fetch",
                        role = roleLabel,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        count = 0,
                        success = false,
                        errorClass = res.throwable::class.simpleName ?: "Throwable",
                        reason = resolveReason(res.throwable.message)
                    )
                }
            }
            _loading.value = false
        }
    }

    private suspend fun resolveAuthContext(): Pair<String, String> {
        return when (val res = authRepo.currentUser()) {
            is AppResult.Success -> {
                val user = res.data
                if (user != null) resolveGreetingName(user.uid, user.email)
                val uid = user?.uid ?: "-"
                val role = user?.role?.toFirestore() ?: "-"
                uid to role
            }
            is AppResult.Failure -> "-" to "-"
        }
    }

    /**
     * Sapaan memakai nama pada data pengguna. Tidak memblokir pemuatan daftar: nama kosong/gagal dibaca jatuh ke
     * awalan email, dan keduanya kosong menghasilkan null (sapaan netral).
     */
    private fun resolveGreetingName(uid: String, email: String?) {
        if (_greetingName.value != null) return
        viewModelScope.launch {
            val stored = (getUserDisplayName(uid) as? AppResult.Success)?.data
            _greetingName.value = expatGreetingName(stored, email)
        }
    }

    private fun logReview(
        event: String,
        role: String,
        startEpochDay: Int,
        endEpochDay: Int,
        count: Int,
        success: Boolean,
        errorClass: String,
        reason: String? = null
    ) {
        val base =
            "[DRIVER_HISTORY] $event role=$role " +
                "rangeStartEpochDay=$startEpochDay rangeEndEpochDay=$endEpochDay " +
                "count=$count success=$success errorClass=$errorClass"
        if (success) {
            DMLog.i(TAG, base)
        } else {
            DMLog.e(TAG, "$base reason=${reason ?: "unknown"}")
        }
    }

    private fun logSummaryFetch(
        role: String,
        startEpochDay: Int,
        endEpochDay: Int,
        success: Boolean,
        errorClass: String
    ) {
        val base =
            "[DRIVER_HISTORY] summary_fetch role=$role " +
                "rangeStartEpochDay=$startEpochDay rangeEndEpochDay=$endEpochDay " +
                "success=$success errorClass=$errorClass"
        if (success) {
            DMLog.i(TAG, base)
        } else {
            DMLog.e(TAG, base)
        }
    }

    private fun resolveReason(message: String?): String {
        val raw = message?.uppercase() ?: return "unknown"
        return if (raw.contains("PERMISSION_DENIED")) "permission_denied" else "unknown"
    }
}
