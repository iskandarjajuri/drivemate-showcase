package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkHistoryDay
import com.drivemate.showcase.domain.calendar.WorkHistoryMonthSummary
import com.drivemate.showcase.domain.calendar.buildWorkHistoryMonth
import com.drivemate.showcase.domain.calendar.shiftWorkHistoryMonth
import com.drivemate.showcase.domain.calendar.monthEpochDayRange
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.calendar.GetWorkCalendarYearUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

private const val TAG = "DM/HistoryAgenda"

/** State layar Riwayat Kerja (Agenda View). Satu bulan per kali tampil. */
data class WorkHistoryAgendaUiState(
    val loading: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val year: Int = 0,
    val month: Int = 0,
    val days: List<WorkHistoryDay> = emptyList(),
    val summary: WorkHistoryMonthSummary? = null,
    val todayEpochDay: Int = 0,
    /**
     * Sel kosong sebelum tanggal 1 pada grid Senin-dulu, dihitung di commonMain.
     * Dulu dihitung di composable; dipindah supaya iOS tidak menghitungnya sendiri di
     * Swift, di mana `Calendar.current.firstWeekday` default MINGGU dan akan menggeser
     * seluruh grid satu kolom tanpa terlihat salah.
     */
    val leadingBlankCount: Int = 0,
    val expandedEpochDay: Int? = null
)

/**
 * ViewModel Riwayat Kerja versi Agenda.
 *
 * Berbeda dari versi lama yang attendance-only dan memakai jendela rolling 3 bulan, ini
 * memuat SATU bulan dan menggabungkannya (JOIN) dengan Work Calendar supaya bisa membedakan
 * "libur" dari "tidak hadir". Seluruh aturan penggabungan ada di `shared/commonMain`
 * (`domain/calendar/WorkHistoryMonth.kt`) supaya iOS memakai ulang yang sama persis.
 */
class DriverWorkHistoryAgendaViewModel(
    private val getWorkDaysByRange: GetWorkDaysByRangeUseCase,
    private val authRepository: AuthRepository,
    workCalendarRepository: WorkCalendarRepository
) : ViewModel() {

    // Pola sama seperti AdminWorkCalendarViewModel: use case dikonstruksi dari repository,
    // jadi tidak perlu menyentuh modul Koin.
    private val getWorkCalendarYear = GetWorkCalendarYearUseCase(workCalendarRepository)

    private val zone = TimeZone.of("Asia/Jakarta")
    private val _state = MutableStateFlow(WorkHistoryAgendaUiState())
    val state: StateFlow<WorkHistoryAgendaUiState> = _state.asStateFlow()

    /** Cache kalender per tahun — pindah bulan dalam tahun sama tidak menembak Firestore lagi. */
    private val calendarCache = mutableMapOf<Int, WorkCalendarYear>()

    fun loadCurrentMonth() {
        if (_state.value.year != 0) return
        val today = TimeUtil.todayEpochDay(zone).toInt()
        val date = LocalDate.fromEpochDays(today)
        load(date.year, date.monthNumber)
    }

    fun showPreviousMonth() = shiftMonth(-1)

    fun showNextMonth() = shiftMonth(+1)

    // Aritmetika bulan ada di commonMain (shiftWorkHistoryMonth) supaya navigator iOS
    // memakai fungsi yang sama — pergantian tahun Januari/Desember adalah off-by-one
    // klasik yang gejalanya cuma "bulannya aneh", tanpa crash.
    private fun shiftMonth(delta: Int) {
        val s = _state.value
        if (s.year == 0) return
        val ref = shiftWorkHistoryMonth(s.year, s.month, delta)
        load(ref.year, ref.month)
    }

    fun toggleExpanded(dateEpochDay: Int) {
        _state.value = _state.value.copy(
            expandedEpochDay = if (_state.value.expandedEpochDay == dateEpochDay) null else dateEpochDay
        )
    }

    fun load(year: Int, month: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(
            loading = true,
            errorMessageRes = null,
            year = year,
            month = month,
            expandedEpochDay = null
        )
        try {
            val uid = when (val res = authRepository.currentUser()) {
                is AppResult.Success -> res.data?.uid
                is AppResult.Failure -> null
            }
            if (uid.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessageRes = R.string.driver_error_user_not_logged_in
                )
                return@launch
            }

            val range = monthEpochDayRange(year, month)
            val calendar = calendarCache[year] ?: when (val res = getWorkCalendarYear(year)) {
                is AppResult.Success -> res.data.also { calendarCache[year] = it }
                is AppResult.Failure -> {
                    // Kalender tidak terbaca: pakai aturan default tahun itu daripada gagal total.
                    // Konsekuensinya libur khusus perusahaan tidak terlihat, tapi weekend dan
                    // "tidak hadir" tetap benar — jauh lebih berguna daripada layar error.
                    DMLog.w(TAG, "work calendar unavailable, fallback to defaults: ${res.throwable.message}")
                    WorkCalendarYear(year = year).also { calendarCache[year] = it }
                }
            }

            val workDays = when (val res = getWorkDaysByRange(uid, range.first, range.last)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessageRes = R.string.driver_history_error_load
                    )
                    return@launch
                }
            }

            val data = buildWorkHistoryMonth(
                year = year,
                month = month,
                calendar = calendar,
                workDays = workDays,
                todayEpochDay = TimeUtil.todayEpochDay(zone).toInt(),
                parseHm = { hm, day -> TimeUtil.parseHm(hm, day.toLong(), zone) }
            )
            _state.value = _state.value.copy(
                loading = false,
                days = data.days,
                todayEpochDay = data.todayEpochDay,
                leadingBlankCount = data.leadingBlankCount,
                summary = data.summary
            )
            DMLog.i(
                TAG,
                "loaded month=$year-$month days=${data.days.size} " +
                    "scheduled=${data.summary.scheduledWorkdayCount}"
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "load failed: ${t.message}", t)
            _state.value = _state.value.copy(
                loading = false,
                errorMessageRes = R.string.driver_history_error_load
            )
        }
    }

}
