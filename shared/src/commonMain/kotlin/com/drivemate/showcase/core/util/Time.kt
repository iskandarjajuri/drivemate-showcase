// TimeUtil.kt (paling atas file)
@file:Suppress("DEPRECATION")
package com.drivemate.showcase.core.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as KtInstant

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant as KxInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Util waktu lintas platform berbasiskan kotlin.time (modern),
 * dengan bridge ke kotlinx.datetime HANYA di sini.
 *
 * Tujuan:
 * - Seluruh project memanggil waktu melalui TimeUtil (now, rentang hari, format).
 * - Tidak ada lagi import kotlin.time/kotlinx.datetime tersebar.
 * - Minimal @OptIn: hanya di file ini.
 */
@OptIn(ExperimentalTime::class)
object TimeUtil {

    /** Instant sekarang (kotlin.time) */
    fun nowInstant(): KtInstant = Clock.System.now()

    /** Epoch millis "sekarang" */
    fun nowEpochMillis(): Long = nowInstant().toEpochMilliseconds()

    /** Konversi kotlin.time.Instant -> kotlinx.datetime.Instant (untuk operasi kalender) */
    private fun KtInstant.toKx(): KxInstant =
        KxInstant.fromEpochMilliseconds(this.toEpochMilliseconds())

    /**
     * Rentang hari ini [start, end) dalam epoch millis untuk zona lokal.
     * start = 00:00 lokal hari ini, end = 00:00 lokal hari besok (exclusive).
     */
    fun todayRangeMillis(zone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        val nowLdt = nowInstant().toKx().toLocalDateTime(zone)
        val start = nowLdt.date.atStartOfDayIn(zone).toEpochMilliseconds()
        val end = nowLdt.date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
        return start to end
    }

    /**
     * Format HH:mm lintas platform (tanpa DateFormat Android).
     * Jika null, kembalikan "--:--".
     */
    fun formatHm(
        epochMillis: Long?,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (epochMillis == null) return "--:--"
        val t = KxInstant
            .fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(zone)
            .time

        val hh = t.hour.toString().padStart(2, '0')
        val mm = t.minute.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    /** Epoch day (hari ke-N sejak 1970-01-01) untuk "hari ini" */
    fun todayEpochDay(zone: TimeZone = TimeZone.currentSystemDefault()): Long =
        nowInstant().toKx().toLocalDateTime(zone).date.toEpochDays()

    /**
     * Rentang hari [start, end] untuk jendela bergulir berbasis bulan kalender.
     * Contoh: 2026-04-07 dengan 3 bulan -> 2026-01-07..2026-04-07 (inklusif).
     */
    fun rollingMonthWindowEpochDays(
        endEpochDay: Int,
        monthCount: Int
    ): IntRange {
        require(monthCount > 0) { "monthCount must be positive" }
        val endDate = LocalDate.fromEpochDays(endEpochDay)
        val startDate = endDate.minus(DatePeriod(months = monthCount))
        return startDate.toEpochDays().toInt()..endEpochDay
    }

    fun rollingThreeMonthWindowEpochDays(
        endEpochDay: Int
    ): IntRange = rollingMonthWindowEpochDays(endEpochDay = endEpochDay, monthCount = 3)

    fun rollingThreeMonthWindowEpochDays(
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): IntRange = rollingThreeMonthWindowEpochDays(endEpochDay = todayEpochDay(zone).toInt())

    /**
     * Epoch day untuk timestamp tertentu (epochMillis) pada zona waktu (default: lokal).
     * Berguna untuk membandingkan apakah dua timestamp berada pada tanggal yang sama.
     */
    fun epochDayOf(
        epochMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): Long =
        KxInstant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(zone)
            .date
            .toEpochDays()

    /** Epoch day untuk timestamp tertentu; alias semantik agar pemanggil tidak memakai "today". */
    fun epochDayFromMillis(
        epochMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): Long = epochDayOf(epochMillis, zone)

    /**
     * Parse teks "HH:mm" menjadi epochMillis untuk tanggal tertentu (epochDay).
     *
     * @param hm format jam:menit, misalnya "07:30"
     * @param epochDay hari ke-N sejak epoch (biasanya dari LocalDate.toEpochDays())
     * @param zone zona waktu lokal (default: currentSystemDefault)
     * @return epoch millis pada tanggal + jam:menit tersebut
     */
    fun parseHm(
        hm: String,
        epochDay: Long,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        val (hour, minute) = hm.split(":").map { it.toInt() }
        val date = kotlinx.datetime.LocalDate.fromEpochDays(epochDay.toInt())
        val dt = kotlinx.datetime.LocalDateTime(
            date.year, date.month, date.dayOfMonth,
            hour, minute
        )
        return dt.toInstant(zone).toEpochMilliseconds()
    }
    fun parseHmDefaultStart(): Long = parseHm("07:30", todayEpochDay())
    fun parseHmDefaultEnd(): Long = parseHm("16:30", todayEpochDay())

    // KMM-safe formatter (tanpa java.util.Formatter/String.format)
    private fun Int.pad2() = this.toString().padStart(2, '0')
    private fun Int.pad4() = this.toString().padStart(4, '0')

    fun formatYmdFromEpochDay(
        epochDay: Int,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val date = kotlinx.datetime.LocalDate.fromEpochDays(epochDay)
        return "${date.year.pad4()}/${date.month.number.pad2()}/${date.dayOfMonth.pad2()}"
    }
}
