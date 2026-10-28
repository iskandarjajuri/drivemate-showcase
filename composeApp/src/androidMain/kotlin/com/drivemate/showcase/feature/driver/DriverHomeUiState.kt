package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay

data class DriverHomeUiState(
    // True until the first refreshHomeState() completes -- lets the UI tell
    // "still loading" apart from "loaded, genuinely nothing" (e.g. no plan
    // for today) instead of treating both the same as null.
    val loading: Boolean = true,
    val driverName: String? = null,
    val sopAccepted: Boolean? = null,
    val checklistSubmitted: Boolean? = null,
    val safetySubmitted: Boolean? = null,
    // Diambil dari WorkDay hari ini, atau fallback ke workday_plans kalau
    // dokumen workdays belum ada (lihat GetDriverTodayScheduleUseCase). Null
    // berarti belum ada jadwal yang bisa ditampilkan, bukan error.
    val plannedStartMillis: Long? = null,
    val plannedEndMillis: Long? = null,
    val planSource: PlanSource? = null,
    // Pratinjau riwayat kerja di Home. Sumbernya SAMA dengan tab "Riwayat
    // kerja" (GetWorkDaysByRangeUseCase atas jendela bergulir 3 bulan) --
    // use case itu memang sudah di-inject ke DriverViewModel untuk keperluan
    // lain, jadi tidak ada query baru maupun wiring DI tambahan di sini.
    // Daftar ini sudah diurut terbaru-dulu dan dipotong di ViewModel.
    val recentWorkDays: List<WorkDay> = emptyList()
)
