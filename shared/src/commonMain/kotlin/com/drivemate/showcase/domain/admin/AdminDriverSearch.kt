package com.drivemate.showcase.domain.admin

/** Satu hasil pencarian driver. */
data class AdminDriverSearchHit(
    val uid: String,
    val displayName: String
)

/**
 * Pencarian driver berdasarkan nama atau UID — dipakai KEDUA platform.
 *
 * ## Kenapa di commonMain
 * Admin memakai ini untuk menemukan orang yang tepat sebelum bertindak atas namanya. Kalau
 * dua platform mencocokkan dengan cara berbeda, admin yang sama bisa menemukan driver di satu
 * perangkat dan tidak di perangkat lain — dan menyimpulkan drivernya tidak ada.
 *
 * Sebelum ini logikanya `private` di `AdminHomeViewModel` Android dan nol test.
 *
 * ## Aturan yang disengaja
 * - **Case-insensitive** pada nama DAN uid: admin mengetik "budi", bukan "Budi".
 * - **Substring, bukan prefix**: nama Indonesia sering punya beberapa kata, dan admin
 *   biasanya mengingat salah satunya — bukan yang pertama.
 * - **Query kosong mengembalikan kosong**, bukan seluruh roster: daftar 200 nama yang muncul
 *   begitu kolom disentuh menutupi layar tanpa diminta.
 * - Hasil diurutkan berdasarkan nama, lalu dibatasi [DEFAULT_SEARCH_LIMIT].
 */
const val DEFAULT_SEARCH_LIMIT: Int = 8

fun searchDrivers(
    query: String,
    driverNames: Map<String, String>,
    limit: Int = DEFAULT_SEARCH_LIMIT
): List<AdminDriverSearchHit> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return emptyList()

    return driverNames.entries
        .asSequence()
        .filter { (uid, name) ->
            uid.lowercase().contains(normalized) || name.lowercase().contains(normalized)
        }
        .map { (uid, name) -> AdminDriverSearchHit(uid = uid, displayName = name) }
        .sortedBy { it.displayName.lowercase() }
        .take(limit.coerceAtLeast(0))
        .toList()
}

/**
 * Apa yang sedang dicari admin — ditebak dari bentuk querynya.
 *
 * Bukan tebakan asal: query pendek atau bermuatan spasi hampir pasti nama orang, sementara
 * string panjang tanpa spasi yang didominasi angka hampir pasti sebuah ID yang di-paste.
 * Menebak salah tidak berbahaya — pencarian nama tetap dijalankan; yang ditentukan hanya
 * apakah pencocokan ID ikut dicoba.
 */
enum class AdminSearchIntent {
    NAME_ONLY,
    MAYBE_RECORD_ID
}

/** Panjang minimum sebelum sebuah query dianggap mungkin ID. */
private const val MIN_ID_LENGTH = 8

/**
 * Klasifikasikan query.
 *
 * Aturannya sengaja konservatif — salah menebak "ini ID" hanya menambah satu pencocokan yang
 * tidak menemukan apa-apa, sedangkan salah menebak "ini nama" membuat ID yang di-paste tidak
 * pernah cocok dan admin menyimpulkan recordnya tidak ada.
 */
fun classifyAdminSearchIntent(query: String): AdminSearchIntent {
    val trimmed = query.trim()
    if (trimmed.length < MIN_ID_LENGTH) return AdminSearchIntent.NAME_ONLY
    // Spasi berarti nama: ID Firestore maupun doc-id kanonik tidak pernah punya spasi.
    if (trimmed.any { it.isWhitespace() }) return AdminSearchIntent.NAME_ONLY
    // Karakter di luar alfanumerik/_/- berarti bukan ID yang dikenal skema ini.
    if (trimmed.any { !(it.isLetterOrDigit() || it == '_' || it == '-') }) {
        return AdminSearchIntent.NAME_ONLY
    }
    return AdminSearchIntent.MAYBE_RECORD_ID
}

/**
 * Cocokkan query terhadap ID record yang SUDAH dimuat.
 *
 * Bekerja atas data di tangan, bukan query Firestore baru: admin yang mem-paste ID hampir
 * selalu menyalinnya dari layar yang sedang dibukanya, jadi recordnya sudah ada. Query baru
 * per ketikan akan menembak Firestore berkali-kali untuk kasus yang sudah tertutup.
 *
 * Pencocokan **awalan**, bukan substring: ID sebagian yang tersalin sebagian tetap ketemu,
 * sementara substring akan mencocokkan ID acak yang kebetulan memuat potongan itu di tengah.
 */
fun matchRecordIds(query: String, recordIds: List<String>, limit: Int = DEFAULT_SEARCH_LIMIT): List<String> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return emptyList()
    if (classifyAdminSearchIntent(query) == AdminSearchIntent.NAME_ONLY) return emptyList()
    return recordIds
        .asSequence()
        .filter { it.lowercase().startsWith(normalized) }
        .distinct()
        .sorted()
        .take(limit.coerceAtLeast(0))
        .toList()
}
