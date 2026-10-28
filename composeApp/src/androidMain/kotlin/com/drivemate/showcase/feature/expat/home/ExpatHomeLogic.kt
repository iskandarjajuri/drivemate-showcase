package com.drivemate.showcase.feature.expat.home

/** Maksimum item yang ikut animasi masuk berurutan (sisanya langsung tampil). */
internal const val EXPAT_STAGGER_MAX_ITEMS = 6

/** Selang antar item pada animasi masuk berurutan (ms). */
internal const val EXPAT_STAGGER_STEP_MILLIS = 40

/** Jeda animasi masuk untuk item ke-[index]; item di atas batas memakai jeda terbesar. */
internal fun expatStaggerDelayMillis(index: Int): Int =
    index.coerceIn(0, EXPAT_STAGGER_MAX_ITEMS - 1) * EXPAT_STAGGER_STEP_MILLIS

/** Denyut "perlu tindakan" hanya saat ada yang menunggu. */
internal fun expatPulseEnabled(pending: Int): Boolean = pending > 0

/** Progres hero = ditinjau / (menunggu + ditinjau); 0 bila belum ada hari kerja. */
internal fun expatReviewProgress(pending: Int, reviewed: Int): Float {
    val p = pending.coerceAtLeast(0)
    val r = reviewed.coerceAtLeast(0)
    val total = p + r
    return if (total == 0) 0f else r.toFloat() / total
}

/**
 * Inisial untuk avatar kecil (header, baris perhatian): huruf pertama kata pertama dan terakhir, satu kata = satu huruf,
 * kosong = "?". Memakai code point penuh agar nama Jepang tidak terpotong.
 */
internal fun homeAvatarInitials(name: String?): String {
    val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    fun first(word: String): String = String(Character.toChars(word.codePointAt(0))).uppercase()
    return if (words.size == 1) first(words.first()) else first(words.first()) + first(words.last())
}
