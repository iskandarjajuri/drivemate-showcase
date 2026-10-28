package com.drivemate.showcase.feature.expat.settings

/**
 * Inisial untuk avatar (Expat tidak bisa mengganti foto: aturan `user_photos` hanya mengizinkan Driver menulis).
 * Dua kata atau lebih: huruf pertama kata pertama dan terakhir; satu kata: huruf pertamanya; kosong: "?".
 * Memakai code point penuh sehingga nama Jepang (kanji) dan karakter di luar BMP tidak terpotong.
 */
internal fun expatInitials(name: String?): String {
    val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    fun first(word: String): String =
        word.codePointAt(0).let { String(Character.toChars(it)) }.uppercase()
    return if (words.size == 1) first(words.first()) else first(words.first()) + first(words.last())
}

/**
 * Jumlah driver yang dikelola untuk layar Profil, dari data Beranda yang sudah ada (tanpa panggilan baru).
 * `null` = belum bisa dipercaya (gagal, atau memuat pertama kali), supaya layar menampilkan tanda strip
 * dan bukan "0" palsu; daftar kosong setelah selesai memuat tanpa galat memang berarti 0.
 */
internal fun expatManagedDriverCount(loading: Boolean, hasError: Boolean, driverCount: Int): Int? =
    if (hasError || (loading && driverCount == 0)) null else driverCount

/**
 * Menormalkan kode bahasa dari AppCompat: Java lama memakai `in` untuk Indonesia, sedangkan daftar pilihan memakai `id`.
 * Kode yang tak dikenal diperlakukan sebagai "ikuti sistem" (`null`).
 */
internal fun normalizeExpatLanguageTag(raw: String?): String? = when (raw?.lowercase()) {
    "id", "in" -> "id"
    "en" -> "en"
    "ja" -> "ja"
    else -> null
}
