package com.drivemate.showcase.feature.expat.ja

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

/**
 * Peran teks untuk gaya Jepang berlingkup Expat. Rujukan: docs/design/expat-ja-ux.md.
 */
enum class ExpatTextRole { TITLE, BODY, CAPTION, BUTTON }

/** Rasio tinggi baris / ukuran huruf untuk judul (DADS: 1,4 sampai 1,5). */
const val EXPAT_JA_TITLE_LINE_HEIGHT_RATIO = 1.45f

/** Rasio untuk isi (DADS: minimal 1,5; umum 1,6 sampai 1,75). */
const val EXPAT_JA_BODY_LINE_HEIGHT_RATIO = 1.6f

/** Keterangan kecil diperlakukan sebagai isi, jadi tetap minimal 1,5. */
const val EXPAT_JA_CAPTION_LINE_HEIGHT_RATIO = 1.5f

/** Kode bahasa Jepang pada konfigurasi aplikasi. */
private const val JAPANESE_LANGUAGE = "ja"

/**
 * Pemenggalan isi/keterangan Jepang: strategi dan keketatan sama dengan preset Paragraph, TETAPI memakai
 * WordBreak.Phrase seperti contoh resmi Android. QA 27 Okt 2026 (emulator, bahasa Jepang) membuktikan preset
 * Paragraph memenggal "あります" menjadi "あり|ます" (titik patah identik dengan sebelum J1); Phrase mencegahnya.
 */
val ExpatJaBodyLineBreak: LineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase
)

/** True bila [languageTag] (mis. "ja", "ja-JP") adalah bahasa Jepang. */
fun isJapaneseLanguage(languageTag: String?): Boolean =
    languageTag != null && (languageTag == JAPANESE_LANGUAGE || languageTag.startsWith("$JAPANESE_LANGUAGE-"))

/** Rasio tinggi baris untuk [role]; null = tinggi baris dibiarkan seperti gaya masukan (tombol). */
fun expatJaLineHeightRatio(role: ExpatTextRole): Float? = when (role) {
    ExpatTextRole.TITLE -> EXPAT_JA_TITLE_LINE_HEIGHT_RATIO
    ExpatTextRole.BODY -> EXPAT_JA_BODY_LINE_HEIGHT_RATIO
    ExpatTextRole.CAPTION -> EXPAT_JA_CAPTION_LINE_HEIGHT_RATIO
    ExpatTextRole.BUTTON -> null
}

/**
 * Fungsi MURNI: memetakan peran teks + status bahasa menjadi [TextStyle].
 * Bukan Jepang: [base] dikembalikan apa adanya (tampilan identik dengan sebelum J1).
 * Jepang: tinggi baris diatur menurut rasio peran dan [LineBreak] dipilih
 * (judul/tombol = Heading, isi/keterangan = ExpatJaBodyLineBreak). Jarak huruf sengaja tidak diubah.
 */
fun expatJaTextStyle(role: ExpatTextRole, isJapanese: Boolean, base: TextStyle): TextStyle {
    if (!isJapanese) return base
    val lineBreak = when (role) {
        ExpatTextRole.TITLE, ExpatTextRole.BUTTON -> LineBreak.Heading
        ExpatTextRole.BODY, ExpatTextRole.CAPTION -> ExpatJaBodyLineBreak
    }
    val ratio = expatJaLineHeightRatio(role)
    val size = base.fontSize
    val lineHeight: TextUnit =
        if (ratio != null && size.type == TextUnitType.Sp) (size.value * ratio).sp else base.lineHeight
    return base.copy(lineHeight = lineHeight, lineBreak = lineBreak)
}

/** Memetakan seluruh [Typography] ke gaya Jepang; hanya slot yang dipakai tema DriveMate. */
fun expatJaTypography(base: Typography): Typography = base.copy(
    headlineSmall = expatJaTextStyle(ExpatTextRole.TITLE, true, base.headlineSmall),
    titleLarge = expatJaTextStyle(ExpatTextRole.TITLE, true, base.titleLarge),
    titleMedium = expatJaTextStyle(ExpatTextRole.TITLE, true, base.titleMedium),
    titleSmall = expatJaTextStyle(ExpatTextRole.TITLE, true, base.titleSmall),
    bodyLarge = expatJaTextStyle(ExpatTextRole.BODY, true, base.bodyLarge),
    bodyMedium = expatJaTextStyle(ExpatTextRole.BODY, true, base.bodyMedium),
    bodySmall = expatJaTextStyle(ExpatTextRole.CAPTION, true, base.bodySmall),
    labelLarge = expatJaTextStyle(ExpatTextRole.BUTTON, true, base.labelLarge),
    labelMedium = expatJaTextStyle(ExpatTextRole.CAPTION, true, base.labelMedium),
    labelSmall = expatJaTextStyle(ExpatTextRole.CAPTION, true, base.labelSmall)
)

/**
 * Pembungkus berlingkup Expat. Bahasa dibaca dari [LocalConfiguration] (bahasa aplikasi),
 * bukan dari lokal bawaan JVM. Bukan Jepang: [content] dipanggil tanpa pembungkus tema.
 * Jepang: tipografi di dalam [content] diganti gaya Jepang dan ditandai localeList "ja"
 * (hipotesis Han unification; efeknya belum dibuktikan di perangkat).
 */
@Composable
fun ExpatJaTypography(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val tag = configuration.locales.takeIf { !it.isEmpty }?.get(0)?.language
    if (!isJapaneseLanguage(tag)) {
        content()
        return
    }
    val colorScheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val outer = MaterialTheme.typography
    val typography = remember(outer) {
        val ja = LocaleList(JAPANESE_LANGUAGE)
        expatJaTypography(outer).let { t ->
            t.copy(
                headlineSmall = t.headlineSmall.copy(localeList = ja),
                titleLarge = t.titleLarge.copy(localeList = ja),
                titleMedium = t.titleMedium.copy(localeList = ja),
                titleSmall = t.titleSmall.copy(localeList = ja),
                bodyLarge = t.bodyLarge.copy(localeList = ja),
                bodyMedium = t.bodyMedium.copy(localeList = ja),
                bodySmall = t.bodySmall.copy(localeList = ja),
                labelLarge = t.labelLarge.copy(localeList = ja),
                labelMedium = t.labelMedium.copy(localeList = ja),
                labelSmall = t.labelSmall.copy(localeList = ja)
            )
        }
    }
    MaterialTheme(colorScheme = colorScheme, shapes = shapes, typography = typography, content = content)
}
