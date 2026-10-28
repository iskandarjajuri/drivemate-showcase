package com.drivemate.showcase.feature.expat.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Bahasa per-aplikasi: mekanisme yang sama dengan Pengaturan Driver (`AppCompatDelegate.setApplicationLocales`,
 * disimpan otomatis lewat `AppLocalesMetadataHolderService`). Expat tidak butuh DataStore tambahan: bahasa efektif
 * dibaca langsung dari AppCompat.
 */
internal fun applyExpatAppLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    )
}

/** Bahasa pilihan pengguna: `id`, `en`, `ja`, atau `null` (ikuti sistem). */
internal fun currentExpatAppLanguageTag(): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    return normalizeExpatLanguageTag(if (locales.isEmpty) null else locales[0]?.language)
}

/** Versi aplikasi dari PackageInfo (bukan konstanta), kosong bila gagal dibaca. */
internal fun expatAppVersionName(context: Context): String =
    runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName.orEmpty()
    }.getOrDefault("")
