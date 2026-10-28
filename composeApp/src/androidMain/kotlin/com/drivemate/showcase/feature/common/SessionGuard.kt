// ===============================================================
// feature/common/SessionGuard.kt
// ===============================================================
package com.drivemate.showcase.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.feature.session.SessionViewModel
import com.drivemate.showcase.navigation.NavRoutes
import org.koin.androidx.compose.koinViewModel

private const val TAG = "DM/SessionGuard"

/**
 * 🔒 SessionGuard berbasis vm.session (UID-first + role).
 *
 * - Jika session == null → redirect ke Login (sekali).
 * - Jika [required] != null dan role tidak cocok → redirect ke dashboard sesuai role aktif.
 * - Menunggu isReady dari VM agar tidak “lompat-lompat” saat app baru naik.
 */
@Composable
fun SessionGuard(
    navController: NavHostController,
    required: Role? = null,
    content: @Composable () -> Unit
) {
    val svm: SessionViewModel = koinViewModel()

    // Pakai session (UserSession?) + isReady dari VM versi terbaru
    val isReady by svm.isReady.collectAsState(initial = false)
    val session by svm.session.collectAsState()

    // Anti dobel navigasi
    val didGoLogin = remember { mutableStateOf(false) }
    val didRoleRedirect = remember { mutableStateOf(false) }

    LaunchedEffect(isReady, session?.uid, session?.role) {
        if (!isReady) {
            DMLog.v(TAG, "Auth not ready → hold")
            return@LaunchedEffect
        }

        // 1) Belum login → Login
        if (session == null) {
            if (!didGoLogin.value) {
                didGoLogin.value = true
                DMLog.w(TAG, "No session → navigate Login")
                navController.navigate(NavRoutes.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        // 2) Sudah login → reset flag login
        if (didGoLogin.value) didGoLogin.value = false

        // 3) Jika butuh role tertentu, tapi role belum ter-resolve → tunggu
        val role = session?.role
        if (required != null && role == null) {
            DMLog.d(TAG, "Required=$required but role=null → waiting resolution…")
            return@LaunchedEffect
        }

        // 4) Role mismatch → arahkan ke dashboard sesuai role aktif
        if (required != null && role != required) {
            if (!didRoleRedirect.value) {
                didRoleRedirect.value = true

                // role sudah dipastikan non-null oleh guard di atas; tetap kasih safety line
                val r = role ?: return@LaunchedEffect

                val dest = when (r) {
                    Role.ADMIN  -> NavRoutes.Login.route
                    Role.DRIVER -> NavRoutes.DriverHome.route
                    Role.EXPAT  -> NavRoutes.ExpatHome.route
                }

                DMLog.i(TAG, "Role mismatch (need=$required, have=$r) → $dest")
                navController.navigate(dest) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        } else {
            if (didRoleRedirect.value) didRoleRedirect.value = false
        }

        DMLog.i(TAG, "✅ Guard passed (uid=${session?.uid}, role=$role)")
    }

    // Render: hanya bila siap & session valid & (role cocok jika required)
    when {
        !isReady -> Unit // optional: shimmer/splash ringan
        session == null -> Unit // redirect sudah di-handle di atas
        required != null && session?.role != required -> Unit // redirect sudah di-handle
        else -> content()
    }
}