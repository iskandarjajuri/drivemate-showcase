package com.drivemate.showcase.feature.settings

import com.drivemate.showcase.R

/**
 * Pemetaan murni languageTag -> bendera untuk layar Settings driver.
 * Label bahasa sengaja TIDAK ada di sini karena datang dari string resource (harus Composable);
 * berkas ini hanya memegang logika yang bisa diuji tanpa Android.
 */
object SettingsLanguageOptions {
    val supportedTags = listOf("id", "en", "ja")

    /** null = "Ikuti perangkat" (tidak punya bendera, memakai badge ikon smartphone). */
    fun flagResFor(tag: String?): Int? = when (tag) {
        "id", "in" -> R.drawable.ic_flag_id
        "en" -> R.drawable.ic_flag_en
        "ja" -> R.drawable.ic_flag_ja
        else -> null
    }

    /** Menormalkan tag yang tersimpan; tag tak dikenal diperlakukan seperti "Ikuti perangkat". */
    fun normalizeTag(tag: String?): String? = tag?.takeIf { it in supportedTags }
}
