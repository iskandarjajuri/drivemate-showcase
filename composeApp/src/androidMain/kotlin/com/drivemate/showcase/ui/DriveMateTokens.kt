package com.drivemate.showcase.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class Palette(
    val primary: Color,
    val primaryHover: Color,
    val primarySoft: Color,
    val accent: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val info: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val onInfo: Color,
    val onSuccess: Color,
    val onWarning: Color,
    val onError: Color,
    val link: Color,
    val disabledContainer: Color,
    val onDisabled: Color,
    val decor1: Color,
    val decor2: Color,
    val successContainerSolid: Color,
    val onSuccessContainer: Color,
    val warningContainerSolid: Color,
    val onWarningContainer: Color,
    val errorContainerSolid: Color,
    val onErrorContainer: Color,
    val infoContainerSolid: Color,
    val onInfoContainer: Color,
    val heroButtonContainer: Color,
    val heroButtonText: Color,
    val warningIcon: Color
)

private val LightPalette = Palette(
    // Realigned to the design-system SSOT (docs/design/drivemate_design_system.yaml:13).
    primary = Color(0xFF2563EB),
    primaryHover = Color(0xFF184DB0),
    primarySoft = Color(0xFFEFF6FF),
    accent = Color(0xFF38BDF8),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFD6DDEA),
    outlineVariant = Color(0xFFE8EEF7),
    info = Color(0xFF2563EB),
    success = Color(0xFF2F8E5A),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFDC2626),
    onInfo = Color(0xFFFFFFFF),
    onSuccess = Color(0xFFFFFFFF),
    onWarning = Color(0xFF0F172A),
    onError = Color(0xFFFFFFFF),
    link = Color(0xFF0F4FC0),
    disabledContainer = Color(0xFFF1F5F9),
    onDisabled = Color(0xFF64748B),
    decor1 = Color(0xFFDBEAFE),
    decor2 = Color(0xFFE0E7FF),
    successContainerSolid = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warningContainerSolid = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    errorContainerSolid = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    infoContainerSolid = Color(0xFFDBEAFE),
    onInfoContainer = Color(0xFF1E3A8A),
    heroButtonContainer = Color(0xFFFFFFFF),
    heroButtonText = Color(0xFF1E3A8A),
    warningIcon = Color(0xFFB45309)
)

private val DarkPalette = Palette(
    primary = Color(0xFF8FBCFF),
    primaryHover = Color(0xFF74A9FF),
    primarySoft = Color(0xFF1A263C),
    accent = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0B1220),
    background = Color(0xFF0A1322),
    onBackground = Color(0xFFE8EEF8),
    surface = Color(0xFF101B2F),
    onSurface = Color(0xFFE8EEF8),
    surfaceVariant = Color(0xFF1A2944),
    onSurfaceVariant = Color(0xFFD0DBF0),
    outline = Color(0xFF526583),
    outlineVariant = Color(0xFF243552),
    info = Color(0xFF60A5FA),
    success = Color(0xFF6DBB8D),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFEF4444),
    onInfo = Color(0xFF0B1220),
    onSuccess = Color(0xFF0B1220),
    onWarning = Color(0xFF0B1220),
    onError = Color(0xFF0B1220),
    link = Color(0xFFB8D4FF),
    disabledContainer = Color(0xFF1C2A44),
    onDisabled = Color(0xFFA2B3CD),
    decor1 = Color(0xFF1B2B46),
    decor2 = Color(0xFF182A52),
    successContainerSolid = Color(0xFF14532D),
    onSuccessContainer = Color(0xFFBBF7D0),
    warningContainerSolid = Color(0xFF451A03),
    onWarningContainer = Color(0xFFFDE68A),
    errorContainerSolid = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
    infoContainerSolid = Color(0xFF172554),
    onInfoContainer = Color(0xFFBFDBFE),
    heroButtonContainer = Color(0xFFFFFFFF),
    heroButtonText = Color(0xFF1E3A8A),
    warningIcon = Color(0xFFFBBF24)
)

object AppPalette {
    // Matches `docs/design/drivemate_design_system.yaml`.
    var primary = LightPalette.primary
    var primaryHover = LightPalette.primaryHover
    var primarySoft = LightPalette.primarySoft
    var accent = LightPalette.accent
    var onPrimary = LightPalette.onPrimary

    var background = LightPalette.background
    var onBackground = LightPalette.onBackground
    var surface = LightPalette.surface
    var onSurface = LightPalette.onSurface
    var surfaceVariant = LightPalette.surfaceVariant
    var onSurfaceVariant = LightPalette.onSurfaceVariant

    var outline = LightPalette.outline
    var outlineVariant = LightPalette.outlineVariant

    var info = LightPalette.info
    var success = LightPalette.success
    var warning = LightPalette.warning
    var error = LightPalette.error

    var onInfo = LightPalette.onInfo
    var onSuccess = LightPalette.onSuccess
    var onWarning = LightPalette.onWarning
    var onError = LightPalette.onError

    var infoContainer = info.copy(alpha = 0.10f)
    var successContainer = success.copy(alpha = 0.08f)
    var warningContainer = warning.copy(alpha = 0.14f)
    var errorContainer = error.copy(alpha = 0.10f)

    var link = LightPalette.link

    var disabledContainer = LightPalette.disabledContainer
    var onDisabled = LightPalette.onDisabled

    var decor1 = LightPalette.decor1
    var decor2 = LightPalette.decor2

    // Pasangan status SOLID (bukan alpha di atas latar): kontras tetap sama di kartu putih maupun di atas gradien hero.
    var successContainerSolid = LightPalette.successContainerSolid
    var onSuccessContainer = LightPalette.onSuccessContainer
    var warningContainerSolid = LightPalette.warningContainerSolid
    var onWarningContainer = LightPalette.onWarningContainer
    var errorContainerSolid = LightPalette.errorContainerSolid
    var onErrorContainer = LightPalette.onErrorContainer
    var infoContainerSolid = LightPalette.infoContainerSolid
    var onInfoContainer = LightPalette.onInfoContainer
    var heroButtonContainer = LightPalette.heroButtonContainer
    var heroButtonText = LightPalette.heroButtonText
    var warningIcon = LightPalette.warningIcon

    fun apply(darkTheme: Boolean) {
        val palette = if (darkTheme) DarkPalette else LightPalette
        primary = palette.primary
        primaryHover = palette.primaryHover
        primarySoft = palette.primarySoft
        accent = palette.accent
        onPrimary = palette.onPrimary
        background = palette.background
        onBackground = palette.onBackground
        surface = palette.surface
        onSurface = palette.onSurface
        surfaceVariant = palette.surfaceVariant
        onSurfaceVariant = palette.onSurfaceVariant
        outline = palette.outline
        outlineVariant = palette.outlineVariant
        info = palette.info
        success = palette.success
        warning = palette.warning
        error = palette.error
        onInfo = palette.onInfo
        onSuccess = palette.onSuccess
        onWarning = palette.onWarning
        onError = palette.onError
        link = palette.link
        disabledContainer = palette.disabledContainer
        onDisabled = palette.onDisabled
        decor1 = palette.decor1
        decor2 = palette.decor2
        successContainerSolid = palette.successContainerSolid
        onSuccessContainer = palette.onSuccessContainer
        warningContainerSolid = palette.warningContainerSolid
        onWarningContainer = palette.onWarningContainer
        errorContainerSolid = palette.errorContainerSolid
        onErrorContainer = palette.onErrorContainer
        infoContainerSolid = palette.infoContainerSolid
        onInfoContainer = palette.onInfoContainer
        heroButtonContainer = palette.heroButtonContainer
        heroButtonText = palette.heroButtonText
        warningIcon = palette.warningIcon
        infoContainer = info.copy(alpha = 0.10f)
        successContainer = success.copy(alpha = 0.08f)
        warningContainer = warning.copy(alpha = 0.14f)
        errorContainer = error.copy(alpha = 0.10f)
    }
}

/**
 * Per-role brand accents (additive — see docs/design/drivemate_design_system.yaml `color.role`).
 * Fixed brand constants, not theme-mutated. Available for role-scoped theming; not yet wired
 * into screens. Driver intentionally mirrors [AppPalette.primary] so the default role matches brand.
 */
object AppRoleColors {
    val driver = Color(0xFF2563EB)
    val driverSurface = Color(0xFFEFF6FF)
    val admin = Color(0xFF7C3AED)
    val adminSurface = Color(0xFFF5F3FF)
    val expat = Color(0xFF0891B2)
    val expatSurface = Color(0xFFECFEFF)
}

object AppSpacing {
    val scale4 = 4.dp
    val scale8 = 8.dp
    val scale12 = 12.dp
    val scale16 = 16.dp
    val scale24 = 24.dp
    val scale32 = 32.dp

    // Backward-compatible aliases.
    val xxs = scale4
    val xs = scale8
    val sm = scale12
    val md = scale16
    val lg = 20.dp
    val xl = scale24
    val xxl = scale32
}

object AppRadii {
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 28.dp
}

object AppShapes {
    val screenCard = AppRadii.l
    val field = AppRadii.m
    val button = AppRadii.m
    val logoBadge = 999.dp
}

object AppSizing {
    val touchTarget = 48.dp
    val textFieldHeight = 56.dp
    val primaryButtonHeight = 56.dp
    val mapHeight = AppSpacing.xxl * 11 + AppSpacing.xs
    val iconSm = 18.dp
    val iconMd = 22.dp
    val authFormMaxWidthPhone = 460.dp
    val authFormMaxWidthTablet = 520.dp
}

object AppElevation {
    val none = 0.dp
    val low = 1.dp
    val med = 3.dp
    val high = 8.dp
}

object AppMotion {
    const val fast = 120
    const val chipPress = 140
    const val surfacePress = 190
    const val settle = 200
    const val medium = 220
    const val slow = 320
    const val normal = medium

    fun duration(reduceMotion: Boolean, millis: Int): Int = if (reduceMotion) 0 else millis
}
