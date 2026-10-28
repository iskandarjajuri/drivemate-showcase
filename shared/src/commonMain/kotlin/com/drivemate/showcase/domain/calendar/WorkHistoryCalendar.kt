package com.drivemate.showcase.domain.calendar

import kotlinx.datetime.LocalDate

/**
 * Klasifikasi satu tanggal menurut Work Calendar — TIGA state, bukan dua.
 *
 * ## Kenapa ini ada
 * [resolveWorkSpecForDate] mengembalikan `WorkSpec?`, dan `null` di sana MENGGABUNGKAN dua
 * sebab yang berbeda secara produk:
 *  - akhir pekan biasa (`defaultRuleForDate(treatWeekendAsWeekday = false)` -> null), dan
 *  - libur khusus perusahaan (`override.isWorkday == false` -> early return null).
 *
 * Untuk UI Riwayat Kerja keduanya WAJIB tampil berbeda, jadi memanggil
 * `resolveWorkSpecForDate(...) == null` saja tidak cukup. Datanya sebenarnya sudah ada di
 * [WorkCalendarYear.overrides]; yang belum ada cuma fungsi yang mengembalikan klasifikasinya.
 * Tanpa fungsi ini tiap pemanggil (Android sekarang, iOS nanti) terpaksa mengulik `overrides`
 * sendiri-sendiri dan pasti mendrift.
 */
enum class DayCalendarKind {
    /** Hari kerja terjadwal (weekday default, atau override yang menjadikannya hari kerja). */
    SCHEDULED_WORKDAY,

    /** Akhir pekan biasa — memang bukan hari kerja, tanpa override apa pun. */
    WEEKEND,

    /** Libur khusus perusahaan — ada override eksplisit `isWorkday = false`. */
    COMPANY_HOLIDAY
}

/**
 * Klasifikasikan satu tanggal. Fungsi murni; tidak menyentuh jam sistem.
 *
 * Urutan presedensi mengikuti [resolveWorkSpecForDate] persis supaya dua fungsi ini tidak
 * pernah berbeda pendapat soal "hari kerja atau bukan":
 * override menang atas aturan default.
 */
fun resolveDayCalendarKind(
    calendar: WorkCalendarYear,
    dateEpochDay: Int
): DayCalendarKind {
    val date = LocalDate.fromEpochDays(dateEpochDay)
    val override = calendar.overrides.firstOrNull { it.date == date }
    if (override != null) {
        return if (override.isWorkday) {
            DayCalendarKind.SCHEDULED_WORKDAY
        } else {
            DayCalendarKind.COMPANY_HOLIDAY
        }
    }
    return if (resolveWorkSpecForDate(calendar, dateEpochDay) != null) {
        DayCalendarKind.SCHEDULED_WORKDAY
    } else {
        DayCalendarKind.WEEKEND
    }
}

/**
 * Jumlah hari kerja TERJADWAL dalam satu bulan — ini penyebut "Hari Kerja X/Y".
 *
 * Dihitung dari dokumen kalender otoritatif (`work_calendars/{year}`), bukan tebakan generik
 * "20 hari" atau "hitung weekday". Repository tidak perlu method baru: `getYear(year)` sudah
 * mengembalikan seluruh aturan + override yang dibutuhkan.
 *
 * @param month 1..12
 */
fun scheduledWorkdayCountInMonth(
    calendar: WorkCalendarYear,
    year: Int,
    month: Int
): Int {
    var count = 0
    for (epochDay in monthEpochDayRange(year, month)) {
        if (resolveDayCalendarKind(calendar, epochDay) == DayCalendarKind.SCHEDULED_WORKDAY) {
            count += 1
        }
    }
    return count
}

/** Rentang epoch-day inklusif untuk seluruh tanggal dalam satu bulan. */
fun monthEpochDayRange(year: Int, month: Int): IntRange {
    val first = LocalDate(year, month, 1)
    val firstOfNext = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return first.toEpochDays().toInt()..(firstOfNext.toEpochDays().toInt() - 1)
}
