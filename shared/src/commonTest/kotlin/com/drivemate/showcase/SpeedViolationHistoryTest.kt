package com.drivemate.showcase

import com.drivemate.showcase.domain.safety.SpeedViolationPage
import com.drivemate.showcase.domain.safety.SpeedViolationRecord
import com.drivemate.showcase.domain.safety.buildSpeedViolationDays
import com.drivemate.showcase.domain.safety.mapSpeedViolationRecordOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Blok 3.3 — riwayat pelanggaran kecepatan (C1 pada PRD §7). */
class SpeedViolationHistoryTest {

    private val dayMs = 86_400_000L
    private val t0 = 20_703L * dayMs        // 2026-09-07 00:00 UTC

    private fun raw(
        driverUid: String? = "d1",
        speedKph: Any? = 84.0,
        limitKph: Any? = 60.0,
        occurredAt: Any? = t0 + 9 * 3_600_000L,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = buildMap {
        put("driverUid", driverUid)
        put("speedKph", speedKph)
        put("speedLimitKph", limitKph)
        put("occurredAtMillis", occurredAt)
        putAll(extra)
    }

    // ── Pemetaan ────────────────────────────────────────────────────────────────

    @Test
    fun mapsCompleteDocument() {
        val r = mapSpeedViolationRecordOrNull("sv1", raw())
        assertEquals("sv1", r?.id)
        assertEquals("d1", r?.driverUid)
        assertEquals(84.0, r?.speedKph)
        assertEquals(60.0, r?.speedLimitKph)
        assertNull(r?.latitude)
        assertNull(r?.longitude)
    }

    @Test
    fun missingSpeedIsDroppedNotDefaultedToZero() {
        // Baris pelanggaran dengan angka karangan lebih berbahaya daripada baris yang
        // tidak muncul: ia terlihat seperti bukti.
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(speedKph = null)))
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(limitKph = null)))
    }

    @Test
    fun blankOrMissingDriverUidIsDropped() {
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(driverUid = null)))
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(driverUid = "   ")))
    }

    @Test
    fun nonPositiveOccurredAtIsDropped() {
        // Nol berarti "tidak tercatat", bukan 1 Januari 1970.
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(occurredAt = 0L)))
        assertNull(mapSpeedViolationRecordOrNull("sv1", raw(occurredAt = -1L)))
    }

    @Test
    fun numericTypesFromFirestoreAreAllAccepted() {
        // Firestore mengembalikan Long untuk bilangan bulat dan Double untuk pecahan;
        // dokumen yang sama bisa datang sebagai salah satunya tergantung cara ia ditulis.
        val r = mapSpeedViolationRecordOrNull(
            "sv1",
            raw(speedKph = 84L, limitKph = 60, occurredAt = (t0 + 1).toDouble()),
        )
        assertEquals(84.0, r?.speedKph)
        assertEquals(60.0, r?.speedLimitKph)
        assertEquals(t0 + 1, r?.occurredAtMillis)
    }

    @Test
    fun nullDocumentDataIsDropped() {
        assertNull(mapSpeedViolationRecordOrNull("sv1", null))
    }

    @Test
    fun coordinatesAreReadWhenPresent() {
        val r = mapSpeedViolationRecordOrNull(
            "sv1",
            raw(extra = mapOf("latitude" to -6.2, "longitude" to 106.8)),
        )
        assertEquals(-6.2, r?.latitude)
        assertEquals(106.8, r?.longitude)
    }

    // ── Kelebihan kecepatan ─────────────────────────────────────────────────────

    @Test
    fun excessIsDifferenceAboveLimit() {
        assertEquals(24.0, rec("a", t0, speed = 84.0, limit = 60.0).excessKph)
    }

    @Test
    fun excessIsZeroAtOrBelowLimit() {
        // Batas terlampaui atau tidak diputuskan oleh detektor saat menulis; kalau
        // dokumennya ternyata tidak melebihi, angka negatif akan tampil sebagai "-5 km/jam"
        // dan membuat baris itu terbaca seperti pelanggaran ke arah sebaliknya.
        assertEquals(0.0, rec("a", t0, speed = 60.0, limit = 60.0).excessKph)
        assertEquals(0.0, rec("a", t0, speed = 55.0, limit = 60.0).excessKph)
    }

    // ── Pengelompokan ───────────────────────────────────────────────────────────

    private fun rec(
        id: String,
        occurredAt: Long,
        driverUid: String = "d1",
        speed: Double = 84.0,
        limit: Double = 60.0,
    ) = SpeedViolationRecord(id, driverUid, speed, limit, occurredAt)

    private val epochDayOf: (Long) -> Int = { (it / dayMs).toInt() }

    @Test
    fun daysAreNewestFirst() {
        val days = buildSpeedViolationDays(
            listOf(
                rec("a", t0 - 2 * dayMs),
                rec("b", t0),
                rec("c", t0 - dayMs),
            ),
            emptyMap(), epochDayOf,
        )
        assertEquals(listOf(20_703, 20_702, 20_701), days.map { it.dateEpochDay })
    }

    @Test
    fun rowsWithinDayAreNewestFirstEvenWhenInputIsShuffled() {
        // Diurutkan ulang walau query sudah `orderBy` menurun: begitu satu dokumen
        // digugurkan atau dua halaman digabung, urutan query tidak lagi jadi jaminan.
        val day = buildSpeedViolationDays(
            listOf(
                rec("pagi", t0 + 7 * 3_600_000L),
                rec("sore", t0 + 17 * 3_600_000L),
                rec("siang", t0 + 12 * 3_600_000L),
            ),
            emptyMap(), epochDayOf,
        ).single()
        assertEquals(listOf("sore", "siang", "pagi"), day.rows.map { it.record.id })
    }

    @Test
    fun sameMillisIsBrokenByIdSoOrderIsStable() {
        val ids = buildSpeedViolationDays(
            listOf(rec("z", t0), rec("a", t0), rec("m", t0)),
            emptyMap(), epochDayOf,
        ).single().rows.map { it.record.id }
        assertEquals(listOf("a", "m", "z"), ids)
    }

    @Test
    fun driverNameIsAttachedAndStaysNullWhenUnknown() {
        // `null`, BUKAN uid: UID tidak boleh muncul di layar lewat pintu belakang.
        val rows = buildSpeedViolationDays(
            listOf(rec("a", t0, driverUid = "d1"), rec("b", t0 + 1, driverUid = "dX")),
            mapOf("d1" to "Budi"), epochDayOf,
        ).single().rows
        assertEquals("Budi", rows.single { it.record.driverUid == "d1" }.driverName)
        assertNull(rows.single { it.record.driverUid == "dX" }.driverName)
    }

    // ── Halaman hasil: terpotong dan yang digugurkan ────────────────────────────

    @Test
    fun truncationIsDecidedByRawDocCountNotMappedRows() {
        // Kasus paling berisiko: jendela PENUH dan datanya berantakan. Menghitung
        // terpotong dari baris yang berhasil dipetakan membuat layar mengaku lengkap
        // justru di situ.
        val page = SpeedViolationPage(records = List(195) { rec("r$it", t0 + it) }, rawDocCount = 200)
        assertTrue(page.isTruncatedAt(200))
        assertEquals(5, page.droppedCount)
    }

    @Test
    fun notTruncatedWhenQueryReturnedFewerThanLimit() {
        val page = SpeedViolationPage(records = List(3) { rec("r$it", t0 + it) }, rawDocCount = 3)
        assertEquals(false, page.isTruncatedAt(200))
        assertEquals(0, page.droppedCount)
    }

    @Test
    fun droppedCountNeverGoesNegative() {
        // Tidak mungkin lewat jalur normal, tapi angka negatif di layar akan terbaca
        // sebagai "-2 catatan tidak dapat dibaca" dan tidak berarti apa-apa.
        val page = SpeedViolationPage(records = List(3) { rec("r$it", t0 + it) }, rawDocCount = 1)
        assertEquals(0, page.droppedCount)
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertTrue(buildSpeedViolationDays(emptyList(), emptyMap(), epochDayOf).isEmpty())
    }
}
