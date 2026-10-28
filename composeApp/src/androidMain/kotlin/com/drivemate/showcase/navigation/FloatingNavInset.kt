package com.drivemate.showcase.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tinggi ruang yang ditutup nav bar mengambang (bar + margin) DI ATAS inset sistem,
 * atau 0.dp bila bar tidak tampil. Diisi oleh `DriveMateRootScaffold`; layar tab
 * memakainya supaya item terakhir tetap bisa digulir di atas bar.
 */
val LocalFloatingNavInset = compositionLocalOf { 0.dp }

/** Bar 64dp + margin atas/bawah 12dp. */
internal val FloatingNavReservedHeight = 88.dp

/** Tambahkan inset nav ke `contentPadding` kontainer gulir (LazyColumn dst.). */
@Composable
fun floatingNavContentPadding(base: PaddingValues = PaddingValues()): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(dir),
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(dir),
        bottom = base.calculateBottomPadding() + LocalFloatingNavInset.current
    )
}

/** Untuk `Column(Modifier.verticalScroll(..).floatingNavPadding())` — padding berada di DALAM area gulir. */
@Composable
fun Modifier.floatingNavPadding(): Modifier = padding(bottom = LocalFloatingNavInset.current)

/** Bar tampil hanya di rute tab masing-masing peran. */
internal fun isFloatingNavRoute(role: com.drivemate.showcase.core.model.Role?, route: String?): Boolean {
    if (route == null) return false
    return when (role) {
        com.drivemate.showcase.core.model.Role.DRIVER -> route in driverFloatingNavRoutes
        com.drivemate.showcase.core.model.Role.ADMIN -> false // no admin UI in the showcase
        com.drivemate.showcase.core.model.Role.EXPAT -> route in expatFloatingNavRoutes
        null -> false
    }
}

/**
 * Status mengecil nav bar saat gulir (murni, bisa diuji unit).
 * `onScroll(deltaY)`: positif = menggulir KE BAWAH (konten naik). Gulir ke bawah
 * terakumulasi > [collapseThreshold] → collapsed; gulir ke atas terakumulasi >
 * [expandThreshold] → expanded. Pembalikan arah memulai akumulasi baru.
 */
class FloatingNavBarState(
    private val collapseThreshold: Float,
    private val expandThreshold: Float
) {
    var collapsed by mutableStateOf(false)
        private set
    private var accumulated = 0f

    fun onScroll(deltaY: Float) {
        if (deltaY == 0f) return
        if (accumulated != 0f && (accumulated > 0f) != (deltaY > 0f)) accumulated = 0f
        accumulated += deltaY
        if (accumulated > collapseThreshold) collapsed = true
        else if (-accumulated > expandThreshold) collapsed = false
    }

    /** Pindah rute / kembali ke atas. */
    fun reset() {
        accumulated = 0f
        collapsed = false
    }
}

internal val NavCollapseThreshold: Dp = 24.dp
internal val NavExpandThreshold: Dp = 8.dp

/** True saat bar mengecil: item menyembunyikan label. */
val LocalFloatingNavCollapsed = staticCompositionLocalOf { false }
