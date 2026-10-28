package com.drivemate.showcase.feature.expat

import com.drivemate.showcase.util.safeDriverLabel

/**
 * Nama untuk sapaan Beranda Expat. Urutan: nama pada data pengguna, lalu awalan email (title case),
 * lalu `null`. `null` berarti layar memakai sapaan netral tanpa nama, bukan "Selamat pagi, " yang menggantung.
 */
internal fun expatGreetingName(storedName: String?, email: String?): String? =
    safeDriverLabel(storedName, email, placeholder = "").takeIf { it.isNotBlank() }
