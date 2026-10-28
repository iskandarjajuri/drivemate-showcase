package com.drivemate.showcase.domain.admin

/**
 * Bagian hari untuk sapaan Admin Home — dipakai KEDUA platform.
 *
 * ## Kenapa di commonMain
 * Sebelumnya ini `private enum` + `private fun` di `AdminHomeScreen.kt` Android dengan **nol
 * test**, dan iOS tidak punya sapaan sama sekali. Menulis ulang ambangnya di Swift berarti
 * dua platform bisa menyapa admin yang sama dengan bagian hari yang berbeda pada jam yang
 * sama — kecil, tapi persis jenis perbedaan yang tidak pernah ada yang melaporkannya sebagai
 * bug, hanya membuat aplikasinya terasa tidak dirawat.
 *
 * Sekarang Android memakai fungsi ini juga, jadi suite Android menjadi uji-kesetaraan untuk
 * logika yang dipakai iOS — satu-satunya cara menguji aturan ini di mesin tanpa toolchain
 * Swift.
 */
enum class GreetingPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    /** Token stabil untuk menyeberangi bridge ke Swift. */
    fun toRaw(): String = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): GreetingPeriod? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * Petakan jam (0-23) ke bagian hari.
 *
 * Ambang batasnya dipertahankan persis seperti Android sebelumnya (5-10 / 11-14 / 15-18 /
 * sisanya) — memindahkan fungsi ini ke commonMain adalah perkara berbagi aturan, bukan
 * kesempatan mengubahnya diam-diam.
 *
 * Jam di luar 0-23 jatuh ke [GreetingPeriod.NIGHT] lewat cabang `else`, sama seperti
 * sebelumnya. Dalam praktiknya tidak bisa terjadi: kedua platform mengambilnya dari API jam
 * sistem yang selalu 0-23.
 *
 * Fungsi MURNI — jam disuntikkan, nol pembacaan jam sistem, supaya bisa diuji deterministik.
 */
fun resolveGreetingPeriod(hour: Int): GreetingPeriod = when (hour) {
    in 5..10 -> GreetingPeriod.MORNING
    in 11..14 -> GreetingPeriod.AFTERNOON
    in 15..18 -> GreetingPeriod.EVENING
    else -> GreetingPeriod.NIGHT
}
