package com.drivemate.showcase.feature.expat.ja

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val JA_DATE_WITH_YEAR = "yyyy年M月d日(E)"
private const val JA_DATE_SHORT = "M月d日(E)"

/** Skeleton `getBestDateTimePattern` untuk bahasa selain Jepang: hari dalam pekan selalu ikut (EN "Sat, Oct 17"). */
private const val SKELETON_SHORT_WITH_WEEKDAY = "MMMEd"
private const val SKELETON_YEAR_WITH_WEEKDAY = "yMMMEd"

/**
 * Fungsi MURNI label tanggal Expat, SEMUA bahasa menampilkan hari dalam pekan.
 * Jepang: "10月27日(火)" ([withYear] false) atau "2026年10月27日(火)" ([withYear] true), hari dalam pekan di
 * dalam tanda kurung sesuai kebiasaan dokumen kerja Jepang. Bahasa lain: pola terbaik lokal lewat [formatter]
 * dengan skeleton "MMMEd" / "yMMMEd" (EN "Sat, Oct 17", ID "Sab, 17 Okt"). [locale] adalah parameter, bukan
 * lokal bawaan JVM.
 */
fun expatDateLabel(epochDay: Int, locale: Locale, withYear: Boolean, formatter: (Int, Locale, String) -> String): String {
    if (!isJapaneseLanguage(locale.language)) {
        return formatter(epochDay, locale, if (withYear) SKELETON_YEAR_WITH_WEEKDAY else SKELETON_SHORT_WITH_WEEKDAY)
    }
    val date = LocalDate.ofEpochDay(epochDay.toLong())
    val pattern = if (withYear) JA_DATE_WITH_YEAR else JA_DATE_SHORT
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.JAPAN))
}

/** Pembungkus untuk layar: bahasa non-Jepang memakai [WorkdayTimeFormatUtil.formatDateLabel] dengan hari dalam pekan. */
fun expatDateLabel(epochDay: Int, locale: Locale, withYear: Boolean = false): String =
    expatDateLabel(epochDay, locale, withYear) { day, loc, skeleton -> WorkdayTimeFormatUtil.formatDateLabel(day, loc, skeleton) }

/** Lokal bahasa aplikasi saat ini, dibaca dari [LocalConfiguration] (observable), bukan dari lokal bawaan JVM. */
@Composable
fun rememberExpatLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) Locale.ROOT else locales[0]
}
