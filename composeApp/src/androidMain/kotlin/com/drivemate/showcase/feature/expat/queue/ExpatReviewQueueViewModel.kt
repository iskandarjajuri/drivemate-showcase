package com.drivemate.showcase.feature.expat.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi
import com.drivemate.showcase.util.UiErrorLogger
import com.drivemate.showcase.util.safeDriverLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** Filter status pada antrean Tinjau. */
enum class ExpatQueueFilter { PENDING, ALL, REJECTED }

/** Satu baris antrean: satu hari kerja milik satu driver. */
data class ExpatQueueItem(
    val workdayId: String,
    val driverUid: String,
    val driverName: String,
    val dateEpochDay: Int,
    val status: ApprovalStatus
)

data class ExpatQueueUiState(
    val loading: Boolean = false,
    /** Daftar driver gagal dimuat: layar penuh galat dengan "Coba lagi". */
    val fatalError: Boolean = false,
    /** Sebagian driver gagal dimuat: daftar mungkin tidak lengkap, layar tetap tampil. */
    val partialError: Boolean = false,
    val filter: ExpatQueueFilter = ExpatQueueFilter.PENDING,
    val allItems: List<ExpatQueueItem> = emptyList()
) {
    val visibleItems: List<ExpatQueueItem> get() = filterExpatQueue(allItems, filter)
    val counts: Map<ExpatQueueFilter, Int> get() = countExpatQueue(allItems)
}

/** Fungsi MURNI: terapkan filter. Pending: tertua dulu; lainnya: terbaru dulu. */
fun filterExpatQueue(items: List<ExpatQueueItem>, filter: ExpatQueueFilter): List<ExpatQueueItem> = when (filter) {
    ExpatQueueFilter.PENDING -> items
        .filter { it.status == ApprovalStatus.PENDING }
        .sortedWith(compareBy<ExpatQueueItem> { it.dateEpochDay }.thenBy { it.driverName.lowercase() })
    ExpatQueueFilter.REJECTED -> items
        .filter { it.status == ApprovalStatus.REJECTED }
        .sortedWith(compareByDescending<ExpatQueueItem> { it.dateEpochDay }.thenBy { it.driverName.lowercase() })
    ExpatQueueFilter.ALL -> items
        .sortedWith(compareByDescending<ExpatQueueItem> { it.dateEpochDay }.thenBy { it.driverName.lowercase() })
}

/** Fungsi MURNI: jumlah per filter untuk label chip. */
fun countExpatQueue(items: List<ExpatQueueItem>): Map<ExpatQueueFilter, Int> = mapOf(
    ExpatQueueFilter.PENDING to items.count { it.status == ApprovalStatus.PENDING },
    ExpatQueueFilter.ALL to items.size,
    ExpatQueueFilter.REJECTED to items.count { it.status == ApprovalStatus.REJECTED }
)

/** Fungsi MURNI: pencarian nama driver (tanpa membedakan huruf besar/kecil); kueri kosong = semua. */
fun filterExpatDrivers(drivers: List<ManagedDriverSummaryUi>, query: String): List<ManagedDriverSummaryUi> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return drivers
    return drivers.filter { it.displayName.lowercase().contains(q) }
}

/**
 * Antrean Tinjau. Investigasi sumber data: daftar hari kerja menunggu tidak punya use case khusus; yang ada
 * [GetManagedDriversUseCase] (driver kelolaan) + [GetWorkDaysByRangeUseCase] (hari kerja per driver pada jendela
 * 3 bulan bergulir, sama dengan Beranda). Keduanya dipakai ulang; tidak ada use case baru dan UI tidak
 * menyentuh Firestore. Satu driver gagal hanya menandai [ExpatQueueUiState.partialError].
 */
class ExpatReviewQueueViewModel(
    private val getManagedDrivers: GetManagedDriversUseCase,
    private val getRange: GetWorkDaysByRangeUseCase,
    private val authRepo: AuthRepository
) : ViewModel() {

    private companion object { const val TAG = "DM/ExpatQueueVM" }

    private val _state = MutableStateFlow(ExpatQueueUiState())
    val state: StateFlow<ExpatQueueUiState> = _state.asStateFlow()

    fun setFilter(filter: ExpatQueueFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, fatalError = false, partialError = false) }
        val window = TimeUtil.rollingThreeMonthWindowEpochDays(TimeZone.of("Asia/Jakarta"))
        viewModelScope.launch {
            val authUid = (authRepo.currentUser() as? AppResult.Success)?.data?.uid ?: "-"
            when (val drivers = getManagedDrivers(authUid)) {
                is AppResult.Failure -> {
                    UiErrorLogger.e(TAG, "drivers fetch failed", drivers.throwable)
                    _state.update { it.copy(loading = false, fatalError = true) }
                }
                is AppResult.Success -> {
                    val results = coroutineScope {
                        drivers.data.map { profile ->
                            async {
                                val name = safeDriverLabel(profile.name, profile.email)
                                profile.uid to when (val res = getRange(profile.uid, window.first, window.last)) {
                                    is AppResult.Success -> res.data.map { wd ->
                                        ExpatQueueItem(
                                            workdayId = wd.id,
                                            driverUid = profile.uid,
                                            driverName = name,
                                            dateEpochDay = wd.dateEpochDay,
                                            status = wd.approvalStatus()
                                        )
                                    }
                                    is AppResult.Failure -> {
                                        UiErrorLogger.e(TAG, "workdays fetch failed", res.throwable)
                                        null
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    val failed = results.count { it.second == null }
                    _state.update {
                        it.copy(
                            loading = false,
                            // Semua sumber gagal = tidak ada data sama sekali: perlakukan sebagai galat penuh.
                            fatalError = results.isNotEmpty() && failed == results.size,
                            partialError = failed in 1 until results.size,
                            allItems = results.flatMap { r -> r.second.orEmpty() }
                        )
                    }
                }
            }
        }
    }
}
