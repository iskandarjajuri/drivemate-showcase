package com.drivemate.showcase

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.TrackingConstants
import com.drivemate.showcase.domain.admin.AdminActivityEvent
import com.drivemate.showcase.domain.admin.AdminActivityType
import com.drivemate.showcase.domain.admin.ADMIN_ACTIVITY_FEED_LIMIT
import com.drivemate.showcase.domain.admin.buildAdminActivityFeed
import com.drivemate.showcase.domain.admin.adminActivityTypeFromAttendance
import com.drivemate.showcase.domain.admin.AdminAttentionCounts
import com.drivemate.showcase.domain.admin.countAdminOverview
import com.drivemate.showcase.domain.admin.dayOverDayTrendPercent
import com.drivemate.showcase.domain.admin.computeAdminLiveMetrics
import com.drivemate.showcase.domain.admin.trackedDriversTrendPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Metrik "perlu perhatian" Admin Home — dipakai KEDUA platform.
 *
 * Angka ini yang dipakai admin memutuskan apakah harus bertindak. Kalau kedua platform
 * menghitungnya sendiri-sendiri, dua admin yang melihat data yang sama bisa melihat jumlah
 * yang berbeda — dan tidak ada yang tahu mana yang benar. Sebelum ini semuanya `private` di
 * ViewModel Android dan nol test.
 */
class AdminLiveMetricsTest {

    private val now = 1_755_000_000_000L
    private val stale = TrackingConstants.STALE_AFTER_MILLIS

    private fun loc(
        uid: String = "d1",
        lat: Double? = -6.2,
        lon: Double? = 106.8,
        ageMillis: Long = 0
    ) = Location(
        driverUid = uid,
        latitude = lat,
        longitude = lon,
        updatedAtMillis = now - ageMillis
    )

    @Test
    fun a_fresh_location_counts_as_tracked() {
        val m = computeAdminLiveMetrics(listOf(loc()), AdminAttentionCounts(), now)
        assertEquals(1, m.trackedDriversCount)
        assertEquals(0, m.staleDriversCount)
    }

    @Test
    fun a_location_older_than_the_stale_window_counts_as_stale() {
        val m = computeAdminLiveMetrics(listOf(loc(ageMillis = stale + 1)), AdminAttentionCounts(), now)
        assertEquals(0, m.trackedDriversCount)
        assertEquals(1, m.staleDriversCount)
    }

    @Test
    fun exactly_at_the_stale_boundary_still_counts_as_tracked() {
        val m = computeAdminLiveMetrics(listOf(loc(ageMillis = stale)), AdminAttentionCounts(), now)
        assertEquals(1, m.trackedDriversCount)
    }

    /**
     * Dokumen tanpa koordinat bukan driver terpantau MAUPUN yang basi — ia tidak membawa
     * informasi posisi sama sekali. Menghitungnya di salah satu ember akan membuat total
     * tidak cocok dengan apa yang tampak di peta.
     */
    @Test
    fun a_location_without_coordinates_counts_in_neither_bucket() {
        val m = computeAdminLiveMetrics(
            listOf(loc(lat = null), loc(uid = "d2", lon = null)),
            AdminAttentionCounts(),
            now
        )
        assertEquals(0, m.trackedDriversCount)
        assertEquals(0, m.staleDriversCount)
        assertEquals(0, m.needsAttentionCount)
    }

    @Test
    fun tracked_and_stale_together_account_for_every_positioned_location() {
        val locations = listOf(
            loc(uid = "a"),
            loc(uid = "b", ageMillis = stale + 1),
            loc(uid = "c", ageMillis = stale + 5_000),
            loc(uid = "d", lat = null)          // tanpa posisi — di luar hitungan
        )
        val m = computeAdminLiveMetrics(locations, AdminAttentionCounts(), now)
        assertEquals(3, m.trackedDriversCount + m.staleDriversCount)
    }

    @Test
    fun needs_attention_sums_every_pending_source() {
        val m = computeAdminLiveMetrics(
            listOf(loc(ageMillis = stale + 1)),          // 1 basi
            AdminAttentionCounts(
                openWorkdayCount = 2,
                checklistMissingToday = 3,
                safetyMissingToday = 4,
                sopPendingCount = 5
            ),
            now
        )
        assertEquals(1 + 2 + 3 + 4 + 5, m.needsAttentionCount)
    }

    @Test
    fun a_clean_day_reports_zero_attention() {
        val m = computeAdminLiveMetrics(listOf(loc()), AdminAttentionCounts(), now)
        assertEquals(0, m.needsAttentionCount)
    }

    @Test
    fun an_empty_roster_is_all_zeroes_not_a_crash() {
        val m = computeAdminLiveMetrics(emptyList(), AdminAttentionCounts(), now)
        assertEquals(0, m.trackedDriversCount)
        assertEquals(0, m.staleDriversCount)
        assertEquals(0, m.needsAttentionCount)
    }

    // ---------- Tren ----------

    @Test
    fun a_zero_baseline_reports_zero_trend_not_infinity() {
        // Naik dari 0 ke 3 driver bukan "peningkatan tak terhingga persen" — itu pembacaan
        // menyesatkan, bukan informasi.
        assertEquals(0, trackedDriversTrendPercent(current = 3, baseline = 0))
    }

    @Test
    fun an_unchanged_count_reports_zero_trend() {
        assertEquals(0, trackedDriversTrendPercent(current = 5, baseline = 5))
    }

    @Test
    fun a_doubling_reports_one_hundred_percent() {
        assertEquals(100, trackedDriversTrendPercent(current = 10, baseline = 5))
    }

    @Test
    fun a_decline_reports_a_negative_trend() {
        assertEquals(-50, trackedDriversTrendPercent(current = 5, baseline = 10))
    }

    @Test
    fun a_small_decline_still_reads_as_a_decline() {
        // 199 dari 200 = -0.5%. Membulatkan ke 0 akan menyembunyikan penurunan.
        assertEquals(-1, trackedDriversTrendPercent(current = 199, baseline = 200))
    }
}

/**
 * Hitungan aktivitas harian + tren hari-ke-hari Admin Home.
 *
 * Sebelumnya `private` di ViewModel Android dan nol test.
 */
class AdminOverviewCountsTest {

    private val today = 20_668
    private val yesterday = 20_667

    @Test
    fun only_the_target_day_is_counted() {
        val counts = countAdminOverview(
            checklistDates = listOf(today, today, yesterday),
            safetyDates = listOf(yesterday),
            attendanceDates = emptyList(),
            attendanceTypes = emptyList(),
            targetEpochDay = today
        )
        assertEquals(2, counts.checklistSubmitted)
        assertEquals(0, counts.safetySubmitted)
    }

    /**
     * Firestore menyimpan "IN"/"in" tergantung jalur penulisnya. Pencocokan sensitif-huruf
     * akan diam-diam melaporkan NOL tap-in — angka yang salah, tanpa error.
     */
    @Test
    fun attendance_type_matching_is_case_insensitive() {
        val counts = countAdminOverview(
            checklistDates = emptyList(),
            safetyDates = emptyList(),
            attendanceDates = listOf(today, today, today, today),
            attendanceTypes = listOf("IN", "in", "OUT", "out"),
            targetEpochDay = today
        )
        assertEquals(2, counts.attendanceIn)
        assertEquals(2, counts.attendanceOut)
    }

    @Test
    fun an_unknown_attendance_type_is_counted_in_neither_bucket() {
        val counts = countAdminOverview(
            checklistDates = emptyList(),
            safetyDates = emptyList(),
            attendanceDates = listOf(today),
            attendanceTypes = listOf("SOMETHING"),
            targetEpochDay = today
        )
        assertEquals(0, counts.attendanceIn)
        assertEquals(0, counts.attendanceOut)
    }

    @Test
    fun mismatched_attendance_list_lengths_do_not_read_out_of_bounds() {
        // Hanya mungkin karena bug pemanggil, tapi crash di Admin Home jauh lebih buruk.
        val counts = countAdminOverview(
            checklistDates = emptyList(),
            safetyDates = emptyList(),
            attendanceDates = listOf(today, today, today),
            attendanceTypes = listOf("IN"),
            targetEpochDay = today
        )
        assertEquals(1, counts.attendanceIn)
    }

    // ---------- Tren hari-ke-hari ----------

    /**
     * Kemarin nol, hari ini 3 -> +300%, BUKAN 0. Mengembalikan 0 akan menyembunyikan
     * lonjakan aktivitas dari nol — justru kejadian yang paling layak diperhatikan admin.
     */
    @Test
    fun a_jump_from_zero_is_reported_not_hidden() {
        assertEquals(300, dayOverDayTrendPercent(todayCount = 3, yesterdayCount = 0))
    }

    @Test
    fun no_change_reports_zero() {
        assertEquals(0, dayOverDayTrendPercent(todayCount = 5, yesterdayCount = 5))
    }

    @Test
    fun a_halving_reports_minus_fifty() {
        assertEquals(-50, dayOverDayTrendPercent(todayCount = 5, yesterdayCount = 10))
    }

    @Test
    fun dropping_to_zero_reports_minus_one_hundred() {
        assertEquals(-100, dayOverDayTrendPercent(todayCount = 0, yesterdayCount = 8))
    }

    @Test
    fun both_zero_reports_zero_rather_than_dividing_by_zero() {
        assertEquals(0, dayOverDayTrendPercent(todayCount = 0, yesterdayCount = 0))
    }
}

/**
 * Activity feed Admin Home — dipakai KEDUA platform.
 *
 * Feed ini menjawab "apa yang baru saja terjadi?". Urutan atau batas yang berbeda antar
 * platform berarti dua admin melihat kejadian TERAKHIR yang berbeda, dan yang satu mengira
 * kejadian yang dilihat rekannya tidak pernah terjadi.
 */
class AdminActivityFeedTest {

    private fun event(ts: Long, id: String = "e") =
        AdminActivityEvent(
            type = AdminActivityType.ATTENDANCE_IN,
            driverUid = "d1",
            recordId = id,
            timestampMillis = ts
        )

    @Test
    fun newest_events_come_first() {
        val feed = buildAdminActivityFeed(
            listOf(event(100), event(300), event(200))
        )
        assertEquals(listOf(300L, 200L, 100L), feed.map { it.timestampMillis })
    }

    @Test
    fun the_feed_is_capped() {
        val many = (1..50).map { event(it.toLong(), id = "e$it") }
        assertEquals(ADMIN_ACTIVITY_FEED_LIMIT, buildAdminActivityFeed(many).size)
    }

    @Test
    fun the_cap_keeps_the_newest_not_the_first_seen() {
        val many = (1..50).map { event(it.toLong(), id = "e$it") }
        val feed = buildAdminActivityFeed(many)
        assertEquals(50L, feed.first().timestampMillis)
    }

    /**
     * Dokumen tanpa waktu yang sah dibuang. Menempatkannya di ujung daftar — paling atas
     * maupun paling bawah — sama-sama menyesatkan pada feed yang urut waktu.
     */
    @Test
    fun events_without_a_valid_timestamp_are_dropped() {
        val feed = buildAdminActivityFeed(listOf(event(0), event(-5), event(100)))
        assertEquals(1, feed.size)
        assertEquals(100L, feed.first().timestampMillis)
    }

    @Test
    fun an_empty_input_gives_an_empty_feed() {
        assertTrue(buildAdminActivityFeed(emptyList()).isEmpty())
    }

    @Test
    fun a_zero_limit_gives_an_empty_feed_rather_than_crashing() {
        assertTrue(buildAdminActivityFeed(listOf(event(100)), limit = 0).isEmpty())
        assertTrue(buildAdminActivityFeed(listOf(event(100)), limit = -3).isEmpty())
    }

    @Test
    fun attendance_type_mapping_is_case_insensitive() {
        assertEquals(AdminActivityType.ATTENDANCE_IN, adminActivityTypeFromAttendance("in"))
        assertEquals(AdminActivityType.ATTENDANCE_IN, adminActivityTypeFromAttendance("IN"))
        assertEquals(AdminActivityType.ATTENDANCE_OUT, adminActivityTypeFromAttendance("out"))
    }

    @Test
    fun an_unknown_attendance_type_maps_to_null_rather_than_a_wrong_guess() {
        assertNull(adminActivityTypeFromAttendance("SOMETHING"))
        assertNull(adminActivityTypeFromAttendance(""))
    }
}
