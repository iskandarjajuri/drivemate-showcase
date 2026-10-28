package com.drivemate.showcase.feature.settings

import androidx.compose.ui.graphics.Color

/** Satu pasangan warna gradien untuk badge ikon 3D di layar Settings driver. */
data class SettingsIconTint(val start: Color, val end: Color)

/**
 * Pasangan gradien badge Settings — tetap (fixed), mengikuti preseden [com.drivemate.showcase.ui.AppRoleColors]:
 * warna aksen konstan yang tidak berubah mengikuti tema, supaya badge 3D tetap "hidup" di mode gelap.
 * Nilainya mengikuti spec `docs/drivemate-settings-redesign.html` bagian 2; jangan menambah pasangan baru.
 */
object SettingsIconTints {
    val language = SettingsIconTint(start = Color(0xFF60A5FA), end = Color(0xFF1D4ED8))
    val reminder = SettingsIconTint(start = Color(0xFFFCD34D), end = Color(0xFFD97706))
    val time = SettingsIconTint(start = Color(0xFFC4B5FD), end = Color(0xFF6D28D9))
    val help = SettingsIconTint(start = Color(0xFF5EEAD4), end = Color(0xFF0F766E))
    val location = SettingsIconTint(start = Color(0xFF86EFAC), end = Color(0xFF15803D))
    val logout = SettingsIconTint(start = Color(0xFFFDA4AF), end = Color(0xFFBE123C))
    val system = SettingsIconTint(start = Color(0xFFCBD5E1), end = Color(0xFF475569))
}
