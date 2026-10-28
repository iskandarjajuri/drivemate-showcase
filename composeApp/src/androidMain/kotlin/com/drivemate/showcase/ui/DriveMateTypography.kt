package com.drivemate.showcase.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.drivemate.showcase.R

/**
 * Plus Jakarta Sans (SIL OFL, lihat licenses/PlusJakartaSans-OFL.txt), hanya bobot
 * 400/500/600/700. Font ini Latin saja: glyph Jepang jatuh ke font CJK sistem
 * (tidak membundel Noto Sans JP demi ukuran APK).
 */
val DriveMateFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

private fun Typography.withBrandFont(): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = DriveMateFontFamily),
    displayMedium = displayMedium.copy(fontFamily = DriveMateFontFamily),
    displaySmall = displaySmall.copy(fontFamily = DriveMateFontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = DriveMateFontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = DriveMateFontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = DriveMateFontFamily),
    titleLarge = titleLarge.copy(fontFamily = DriveMateFontFamily),
    titleMedium = titleMedium.copy(fontFamily = DriveMateFontFamily),
    titleSmall = titleSmall.copy(fontFamily = DriveMateFontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = DriveMateFontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = DriveMateFontFamily),
    bodySmall = bodySmall.copy(fontFamily = DriveMateFontFamily),
    labelLarge = labelLarge.copy(fontFamily = DriveMateFontFamily),
    labelMedium = labelMedium.copy(fontFamily = DriveMateFontFamily),
    labelSmall = labelSmall.copy(fontFamily = DriveMateFontFamily)
)

val DriveMateTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
).withBrandFont()
