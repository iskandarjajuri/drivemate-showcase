package com.drivemate.showcase

import com.drivemate.showcase.domain.admin.AdminSearchIntent
import com.drivemate.showcase.domain.admin.classifyAdminSearchIntent
import com.drivemate.showcase.domain.admin.matchRecordIds
import com.drivemate.showcase.domain.admin.DEFAULT_SEARCH_LIMIT
import com.drivemate.showcase.domain.admin.searchDrivers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pencarian driver — dipakai KEDUA platform.
 *
 * Admin memakai ini untuk menemukan orang yang tepat sebelum bertindak atas namanya. Kalau
 * kedua platform mencocokkan berbeda, admin yang sama bisa menemukan driver di satu perangkat
 * dan tidak di perangkat lain, lalu menyimpulkan drivernya tidak ada.
 */
class AdminDriverSearchTest {

    private val roster = mapOf(
        "uid-001" to "Budi Santoso",
        "uid-002" to "Ahmad Wijaya",
        "uid-003" to "Siti Budiarti",
        "uid-004" to "Tanaka Hiroshi"
    )

    @Test
    fun an_empty_query_returns_nothing_not_the_whole_roster() {
        // Daftar 200 nama yang muncul begitu kolom disentuh menutupi layar tanpa diminta.
        assertTrue(searchDrivers("", roster).isEmpty())
        assertTrue(searchDrivers("   ", roster).isEmpty())
    }

    @Test
    fun matching_is_case_insensitive() {
        // Admin mengetik "budi", bukan "Budi".
        assertEquals(2, searchDrivers("budi", roster).size)
        assertEquals(2, searchDrivers("BUDI", roster).size)
    }

    @Test
    fun matching_is_substring_not_prefix() {
        // Nama Indonesia sering beberapa kata, dan admin biasanya mengingat salah satunya —
        // belum tentu yang pertama.
        val hits = searchDrivers("santoso", roster)
        assertEquals(1, hits.size)
        assertEquals("Budi Santoso", hits.first().displayName)
    }

    @Test
    fun a_uid_fragment_also_matches() {
        val hits = searchDrivers("uid-004", roster)
        assertEquals(1, hits.size)
        assertEquals("Tanaka Hiroshi", hits.first().displayName)
    }

    @Test
    fun results_are_sorted_by_name() {
        val hits = searchDrivers("a", roster).map { it.displayName }
        assertEquals(hits.sortedBy { it.lowercase() }, hits)
    }

    @Test
    fun results_are_capped_at_the_limit() {
        val many = (1..50).associate { "uid-$it" to "Driver $it" }
        assertEquals(DEFAULT_SEARCH_LIMIT, searchDrivers("driver", many).size)
        assertEquals(3, searchDrivers("driver", many, limit = 3).size)
    }

    @Test
    fun a_query_matching_nothing_returns_empty_rather_than_everything() {
        assertTrue(searchDrivers("zzzz", roster).isEmpty())
    }

    @Test
    fun a_zero_or_negative_limit_returns_empty_rather_than_crashing() {
        assertTrue(searchDrivers("budi", roster, limit = 0).isEmpty())
        assertTrue(searchDrivers("budi", roster, limit = -5).isEmpty())
    }

    @Test
    fun an_empty_roster_is_safe() {
        assertTrue(searchDrivers("budi", emptyMap()).isEmpty())
    }
}

/**
 * Klasifikasi query + pencocokan ID record.
 *
 * Salah menebak "ini nama" membuat ID yang di-paste tidak pernah cocok, dan admin
 * menyimpulkan recordnya tidak ada. Salah menebak "ini ID" hanya menambah satu pencocokan
 * yang tidak menemukan apa-apa — jadi aturannya sengaja condong ke sisi yang murah.
 */
class AdminSearchIntentTest {

    @Test
    fun a_short_query_is_a_name() {
        assertEquals(AdminSearchIntent.NAME_ONLY, classifyAdminSearchIntent("budi"))
        assertEquals(AdminSearchIntent.NAME_ONLY, classifyAdminSearchIntent("abcdefg"))
    }

    @Test
    fun a_query_with_spaces_is_a_name() {
        // ID Firestore maupun doc-id kanonik tidak pernah punya spasi.
        assertEquals(AdminSearchIntent.NAME_ONLY, classifyAdminSearchIntent("Budi Santoso"))
    }

    @Test
    fun a_query_with_punctuation_is_a_name() {
        assertEquals(AdminSearchIntent.NAME_ONLY, classifyAdminSearchIntent("budi@example.com"))
    }

    @Test
    fun a_long_unbroken_alphanumeric_query_may_be_an_id() {
        assertEquals(AdminSearchIntent.MAYBE_RECORD_ID, classifyAdminSearchIntent("aBcD1234EfGh"))
        // doc-id kanonik `{uid}_{epochDay}` memakai garis bawah — harus tetap lolos.
        assertEquals(AdminSearchIntent.MAYBE_RECORD_ID, classifyAdminSearchIntent("uid12345_20668"))
    }

    // ---------- Pencocokan ID ----------

    private val ids = listOf("aBcD1234EfGh", "aBcD1234Zzzz", "xYz98765Qwer")

    @Test
    fun a_full_id_matches_itself() {
        assertEquals(listOf("aBcD1234EfGh"), matchRecordIds("aBcD1234EfGh", ids))
    }

    @Test
    fun a_partial_id_matches_by_prefix() {
        // ID yang tersalin sebagian tetap ketemu.
        assertEquals(2, matchRecordIds("aBcD1234", ids).size)
    }

    /**
     * Awalan, BUKAN substring: substring akan mencocokkan ID acak yang kebetulan memuat
     * potongan itu di tengah, dan admin tidak punya cara tahu kenapa hasilnya muncul.
     */
    @Test
    fun matching_is_prefix_not_substring() {
        assertTrue(matchRecordIds("1234EfGh", ids).isEmpty())
    }

    @Test
    fun a_name_shaped_query_never_matches_record_ids() {
        assertTrue(matchRecordIds("budi", ids).isEmpty())
        assertTrue(matchRecordIds("Budi Santoso", ids).isEmpty())
    }

    @Test
    fun matching_is_case_insensitive() {
        assertEquals(2, matchRecordIds("abcd1234", ids).size)
    }

    @Test
    fun duplicates_are_collapsed() {
        assertEquals(1, matchRecordIds("aBcD1234EfGh", ids + ids).size)
    }

    @Test
    fun an_empty_query_or_roster_is_safe() {
        assertTrue(matchRecordIds("", ids).isEmpty())
        assertTrue(matchRecordIds("aBcD1234EfGh", emptyList()).isEmpty())
    }
}
