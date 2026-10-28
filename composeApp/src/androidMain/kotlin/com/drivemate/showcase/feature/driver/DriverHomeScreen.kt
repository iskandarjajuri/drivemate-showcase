package com.drivemate.showcase.feature.driver

import androidx.compose.ui.platform.LocalConfiguration
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.dsv2.DmIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Notifications
import com.drivemate.showcase.navigation.floatingNavContentPadding
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.TrackingIssue
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.ProfileAvatar
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.feature.settings.toDisplayTitleCase
import com.drivemate.showcase.ui.patterns.rememberDmAuthBrandPalette
import com.drivemate.showcase.ui.patterns.rememberDmAuthIsDark
import com.drivemate.showcase.ui.ds.AutoDismissInlineAlert
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.Dsv2Radii
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import com.drivemate.showcase.location.hasLocationPermission
import com.drivemate.showcase.util.UiErrorLogger
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class HomeAttendanceAction { TapIn, TapOut }
private enum class HomeAttendancePhase { Idle, Open, Closed }

/**
 * Menggambar gradien linear 155 derajat yang membentang penuh melintasi area
 * gambar saat ini.
 *
 * Digambar lewat DrawScope (bukan `Modifier.background` dengan offset piksel
 * yang dipatok) supaya panjang ramp selalu dihitung dari ukuran nyata yang
 * sedang digambar: offset tetap akan memotong ramp begitu ukurannya berubah --
 * mis. kartu hero yang meninggi saat banner gate checklist muncul di dalamnya,
 * atau wadah ikon tile yang ukurannya berbeda dari hero.
 */
internal fun DrawScope.draw155Gradient(colors: List<Color>) {
    val dx = GRADIENT_155_DIR_X
    val dy = GRADIENT_155_DIR_Y
    val extent = size.width * abs(dx) + size.height * abs(dy)
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = Offset(dx * extent / 2f, dy * extent / 2f)
    drawRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = center - half,
            end = center + half
        )
    )
}

/**
 * Neural Wheel dipakai ulang sebagai avatar header. Splash memakai 132dp;
 * di sini jauh lebih kecil karena hanya penanda identitas di sebelah nama.
 */
private val HOME_AVATAR_SIZE = 44.dp

// Satu radius dan satu level elevasi untuk semua kartu Beranda; gutter 16dp, jarak antar kartu 12dp.
private val HOME_CARD_RADIUS = 20.dp
private val HOME_CARD_ELEVATION = 2.dp

// Arah gradien 155 derajat gaya CSS (0 = ke atas, searah jarum jam) ->
// (sin 155, -cos 155) pada koordinat layar y-ke-bawah. Dipakai kartu hero DAN
// wadah ikon tile, jadi sudutnya konsisten di seluruh layar.
internal const val GRADIENT_155_DIR_X = 0.4226f
internal const val GRADIENT_155_DIR_Y = 0.9063f

// Titik "sedang bertugas" di kartu hero. Hijau terang supaya terbaca di atas
// gradien navy; ini SATU-SATUNYA sinyal semantik warna yang tersisa di dalam
// hero setelah ikon/teks di sana jadi putih semua.
private val HERO_ON_DUTY_DOT_COLOR = Color(0xFF4ADE80)
private val HERO_ON_DUTY_DOT_SIZE = 8.dp
private const val HERO_ON_DUTY_PULSE_MILLIS = 1200

// ── Tile Aksi Cepat: "Widget-Style, Icon-Forward" (Concept 4) ──────────────
// Ikon jadi fokus visual tile, bukan aksesori: wadahnya 64dp dengan ikon 30dp
// di TENGAH tile, dan chrome kartunya sengaja dikurangi (border dibuang) supaya
// yang pertama tertangkap mata adalah ikonnya, bukan garis tepi kartunya.
// Tile tetap jauh di atas AppSizing.touchTarget (48dp): pada kolom selebar
// setengah layar dengan aspectRatio 1.2, tinggi efektifnya ~140dp.
private val TILE_ICON_CONTAINER = 64.dp
private val TILE_ICON_GLYPH = 30.dp
/** Dsv2Radii.medium = 20dp, persis radius rounded-square yang diminta. */
private val TILE_ICON_RADIUS = Dsv2Radii.medium
private val TILE_ICON_ELEVATION = 6.dp
private val TILE_BADGE_SIZE = 22.dp
/** Tebal rim putih di sekeliling glyph badge. */
private val TILE_BADGE_RIM = 2.dp
/** Geseran badge keluar dari sudut wadah ikon. */
private val TILE_BADGE_OFFSET = 5.dp
private const val TILE_PULSE_MILLIS = 1400
private const val TILE_PULSE_MAX_SCALE = 1.07f

// Ramp gradien per kategori (Concept 4 yang disetujui). Sebagian ujungnya
// memang sudah ada di token (mis. AppPalette.primary = #2563EB, dan #60A5FA /
// #A78BFA / #22D3EE sudah dipakai sebagai nodeColors Neural Wheel di
// DmAuthBrand), tapi TIDAK ada satu pun pasangan awal-akhir yang lengkap di
// AppPalette -- jadi keempatnya didefinisikan di satu tempat di sini alih-alih
// disebar sebagai hex lepas di titik pemakaian.
private val TILE_GRADIENT_CHECKLIST = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
private val TILE_GRADIENT_SAFETY = listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
private val TILE_GRADIENT_SOP = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))
private val TILE_GRADIENT_HISTORY = listOf(Color(0xFF22D3EE), Color(0xFF0891B2))

/**
 * Command Center layout (R4 rework). Tidak ada lagi section Route — desain
 * approved-nya sudah tidak punya itu, dan kode sebelum rework ini pun sudah
 * tidak punya section Route sama sekali (dicek sebelum rebuild ini).
 */
@Composable
fun DriverHomeScreen(
    state: DriverUiState,
    trackingState: DriverTrackingUiState,
    driverName: String?,
    driverEmail: String?,
    driverRole: String?,
    sopAccepted: Boolean?,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?,
    homeStateLoading: Boolean,
    plannedStartMillis: Long?,
    plannedEndMillis: Long?,
    planSource: PlanSource?,
    recentWorkDays: List<WorkDay>,
    checklistSubmittedAtMillis: Long?,
    onOpenAttendance: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenSop: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenWorkdayDetail: (workdayId: String) -> Unit,
    onOpenAccount: () -> Unit,
    onRefresh: () -> Unit,
    onTapIn: () -> Unit,
    onTapOut: () -> Unit,
    onTrackingSync: (clockedIn: Boolean, permissionGranted: Boolean, providerEnabled: Boolean) -> Unit,
    profilePhoto: ImageBitmap? = null,
    notificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current

    val dailyStatus = DriverDailyStatus(
        sopAccepted = sopAccepted == true,
        tappedIn = state is DriverUiState.Working,
        checklistDone = checklistSubmitted == true,
        safetyDone = safetySubmitted == true
    )
    val nextTask = dailyStatus.nextTask()
    val completionBannerStore = remember { DriverHomeCompletionBannerStore(context) }
    var showCompletionBanner by rememberSaveable { mutableStateOf(false) }

    val trackingClockedIn = (state as? DriverUiState.Working)?.clockedIn == true
    val hasLocationPermission = hasLocationPermission(context)

    LaunchedEffect(Unit) {
        onRefresh()
    }

    // "Status absensi belum diketahui" BUKAN berarti "sedang tidak bertugas".
    //
    // `_state` berawal dari DriverUiState.Loading, jadi pada komposisi pertama
    // -- dan setiap kali refresh mengembalikannya ke Loading -- `trackingClockedIn`
    // terbaca false padahal driver mungkin sedang bertugas. Sebelum pembersihan
    // lokasi dibuat tanpa syarat, hal itu tidak terlihat karena gerbangnya toh
    // tidak pernah terbuka. Begitu tanpa syarat, ini jadi bug nyata: snapshot
    // lokasi driver yang SEDANG bekerja ikut terhapus tiap kali Home dibuka.
    //
    // Terdeteksi lewat logcat QA: tiap "Background started FGS" (bukti driver
    // benar-benar bertugas) diikuti ~150ms kemudian oleh "clear off-duty
    // location snapshot success". Karena itu rekonsiliasi ditahan sampai status
    // absensi benar-benar terselesaikan.
    val attendanceResolved = state is DriverUiState.Working

    LaunchedEffect(
        attendanceResolved,
        trackingClockedIn,
        hasLocationPermission,
        trackingState.warningType
    ) {
        if (!attendanceResolved) return@LaunchedEffect
        val providerEnabled = isLocationProviderEnabled(context)
        onTrackingSync(trackingClockedIn, hasLocationPermission, providerEnabled)
        if (trackingClockedIn && hasLocationPermission && trackingState.warningType == TrackingIssue.ProviderDisabled) {
            while (isActive) {
                delay(12_000)
                if (isLocationProviderEnabled(context)) {
                    onTrackingSync(trackingClockedIn, hasLocationPermission, true)
                    break
                }
            }
        }
    }

    val displayName = resolveDriverName(
        driverName = driverName,
        driverEmail = driverEmail,
        fallbackName = stringResource(R.string.driver_name_fallback)
    ).toDisplayTitleCase()
    val displayRole = driverRole?.takeIf { it.isNotBlank() } ?: stringResource(R.string.driver_role_default)
    val zone = TimeZone.of("Asia/Jakarta")
    val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()

    val snackbarHostState = remember { SnackbarHostState() }
    val checklistSubmittedTimeText = checklistSubmittedAtMillis?.let { TimeUtil.formatHm(it, zone) }
    val checklistSubmittedMessage = checklistSubmittedTimeText?.let {
        stringResource(R.string.driver_home_checklist_submitted_snackbar, it)
    }
    val checklistSubmittedActionLabel = stringResource(R.string.driver_home_checklist_submitted_action)
    // checklistSubmittedAtMillis is a one-shot value already consumed (read
    // and removed from the SavedStateHandle) by the caller -- it never
    // changes again after the first composition of this Home entry, so this
    // effect can't get cancelled mid-`showSnackbar` by its own key changing.
    LaunchedEffect(checklistSubmittedAtMillis) {
        val message = checklistSubmittedMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = checklistSubmittedActionLabel)
        if (result == SnackbarResult.ActionPerformed) {
            onOpenChecklist()
        }
    }
    val dateText = formatTanggalIndo(dateEpochDay)

    // Shown once per day, the first time every task is done -- then hidden on
    // every later visit, even though nextTask() keeps returning null.
    LaunchedEffect(nextTask, homeStateLoading, dateEpochDay) {
        if (homeStateLoading || nextTask != null) {
            showCompletionBanner = false
            return@LaunchedEffect
        }
        val today = dateEpochDay.toLong()
        if (completionBannerStore.shouldShowToday(today)) {
            completionBannerStore.markShown(today)
            showCompletionBanner = true
        } else {
            showCompletionBanner = false
        }
    }
    val todayAttendance = (state as? DriverUiState.Working)?.todayAttendance
    val attendancePhase = resolveHomeAttendancePhase(todayAttendance)
    val timeRangeText = buildTimeRange(
        clockInMillis = todayAttendance?.clockInMillis,
        clockOutMillis = todayAttendance?.clockOutMillis,
        zone = zone,
        fullRangeFormat = stringResource(R.string.driver_time_range_full),
        inOnlyFormat = stringResource(R.string.driver_time_range_in_only),
        emptyRange = stringResource(R.string.driver_time_range_empty)
    )
    val isWorking = attendancePhase == HomeAttendancePhase.Open
    val isCompleted = attendancePhase == HomeAttendancePhase.Closed
    val statusWorking = stringResource(R.string.driver_status_working)
    val statusCompleted = stringResource(R.string.driver_status_completed)
    val statusIdle = stringResource(R.string.driver_status_idle)
    val presensiStatus = when {
        isWorking -> statusWorking
        isCompleted -> statusCompleted
        else -> statusIdle
    }
    val presensiKind = when {
        isWorking -> Dsv2StatusKind.Approved
        isCompleted -> Dsv2StatusKind.Approved
        else -> Dsv2StatusKind.Pending
    }

    // "Complete" di sini berarti dua item harian sudah submit -- tidak ada
    // pecahan sub-item untuk ditimbang, hasChecklist/hasSafetyDeclaration
    // masing-masing cuma satu boolean per hari (dikonfirmasi lewat
    // ChecklistRepository/SafetyRepository, sama seperti gate PR #209).
    val checklistGateComplete = checklistSubmitted == true && safetySubmitted == true
    val checklistGateCompletedCount = listOf(checklistSubmitted, safetySubmitted).count { it == true }

    var pendingAction by rememberSaveable { mutableStateOf<HomeAttendanceAction?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var infoBanner by remember { mutableStateOf<String?>(null) }
    val errorMessage = (state as? DriverUiState.Error)?.messageRes?.let { stringResource(it) }
    val infoMessage = (state as? DriverUiState.Working)?.messageRes?.let { stringResource(it) }

    LaunchedEffect(state) {
        if (state is DriverUiState.Error) {
            errorBanner = errorMessage
        }
        if (state is DriverUiState.Working && !infoMessage.isNullOrBlank()) {
            infoBanner = infoMessage
        }
    }

    LaunchedEffect(state, pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        when (action) {
            HomeAttendanceAction.TapIn -> {
                if (state is DriverUiState.Working && attendancePhase != HomeAttendancePhase.Idle) {
                    pendingAction = null
                }
            }
            HomeAttendanceAction.TapOut -> {
                if (state is DriverUiState.Working && (
                        attendancePhase == HomeAttendancePhase.Closed ||
                            state.messageRes == R.string.driver_error_attendance_tap_out_failed
                        )
                ) {
                    pendingAction = null
                }
            }
        }
        if (state is DriverUiState.Error) {
            pendingAction = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.HOME_ROOT)
    ) {
        // contentWindowInsets nol, BUKAN default: layar ini sudah dibungkus
        // DriveMateRootScaffold, yang menyerahkan innerPadding mencakup status
        // bar + navigation bar ke setiap route non-full-bleed (Driver Home
        // termasuk -- fullBleedRoutes cuma Splash dan Login). Scaffold di sini
        // memakai ScaffoldDefaults.contentWindowInsets akan menghitung inset
        // yang SAMA untuk kedua kalinya, menambah lagi setinggi status bar
        // (~40dp) di puncak dan setinggi nav bar di dasar.
        //
        // Itu lapisan kedua dari ruang kosong di puncak layar, terpisah dari
        // gap item LazyColumn di bawah: yang satu ~72dp, yang ini ~40dp lagi.
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            when {
                state is DriverUiState.Error -> DriverErrorState(
                    message = stringResource(state.messageRes),
                    onRetry = onRefresh,
                    modifier = Modifier
                        .padding(padding)
                        .testTag(DriverMaestroTags.HOME_ERROR_STATE)
                )
                state is DriverUiState.Loading -> DriverHomeLoadingSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.md)
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Ketiga banner ini dikeluarkan dari LazyColumn, dan itu
                    // yang memperbaiki ruang kosong besar di puncak layar.
                    //
                    // Sebagai `item {}`, masing-masing hampir selalu bertinggi
                    // NOL (isinya dibungkus AnimatedVisibility(visible=false)),
                    // tapi LazyColumn tetap menyisipkan
                    // verticalArrangement.spacedBy(lg) di antara SETIAP item --
                    // termasuk item bertinggi nol. Tiga item kosong = tiga gap
                    // mati 24dp sebelum header, plus contentPadding atas dan
                    // inset status bar. Itulah ruang kosong yang terlihat, dan
                    // itu pula yang mendorong baris kedua grid Aksi Cepat ke
                    // bawah lipatan.
                    //
                    // Di luar LazyColumn, Column ini benar-benar nol tinggi
                    // saat semua banner tersembunyi -- tidak ada arrangement
                    // yang menyisipkan jarak. Animasi enter/exit dan timer
                    // auto-dismiss tetap utuh (tidak ada state yang di-hoist,
                    // tidak ada emisi bersyarat). Efek samping yang diinginkan:
                    // banner error sekarang menempel di atas, tidak ikut
                    // ter-scroll hilang saat driver menggulir.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dsv2Spacing.md)
                    ) {
                        DriverNetworkBanner(modifier = Modifier.fillMaxWidth())
                        // R3 — indikator real-time "melebihi batas kecepatan".
                        // Persisten (bukan auto-dismiss) selama driver masih di atas
                        // batas: ini kondisi berkelanjutan, bukan event sesaat.
                        // Terpisah dari janji willFollowSpeedLimit di Safety Declaration.
                        if (trackingState.overSpeedLimit) {
                            val speedNow = trackingState.currentSpeedKph?.roundToInt()
                            val speedLimit = trackingState.speedLimitKph?.roundToInt()
                            if (speedNow != null && speedLimit != null) {
                                InlineAlert(
                                    kind = InlineAlertKind.Error,
                                    message = stringResource(
                                        R.string.driver_speed_limit_banner,
                                        speedNow,
                                        speedLimit
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(DriverMaestroTags.HOME_SPEED_LIMIT_BANNER)
                                )
                            }
                        }
                        AutoDismissInlineAlert(
                            kind = InlineAlertKind.Warning,
                            message = infoBanner,
                            onDismiss = { infoBanner = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AutoDismissInlineAlert(
                            kind = InlineAlertKind.Error,
                            message = errorBanner,
                            onDismiss = { errorBanner = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(DriverMaestroTags.HOME_CONTENT),
                        contentPadding = floatingNavContentPadding(
                            PaddingValues(
                                horizontal = Dsv2Spacing.md,
                                vertical = Dsv2Spacing.md
                            )
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                    ) {
                    item {
                        HomeHeaderRow(
                            name = displayName,
                            role = displayRole,
                            photo = profilePhoto,
                            notificationCount = notificationCount,
                            onOpenNotifications = onOpenNotifications,
                            onOpenAccount = onOpenAccount
                        )
                    }

                    item {
                        StatusHeroCard(
                            statusText = presensiStatus,
                            statusKind = presensiKind,
                            dateText = dateText,
                            timeRange = timeRangeText,
                            isWorking = isWorking,
                            isCompleted = isCompleted,
                            attendancePhase = attendancePhase,
                            checklistGateComplete = checklistGateComplete,
                            checklistGateCompletedCount = checklistGateCompletedCount,
                            pendingAction = pendingAction,
                            onOpenDetail = onOpenAttendance,
                            onTapIn = {
                                pendingAction = HomeAttendanceAction.TapIn
                                onTapIn()
                            },
                            onTapOut = {
                                pendingAction = HomeAttendanceAction.TapOut
                                onTapOut()
                            },
                            modifier = Modifier.testTag(DriverMaestroTags.TODAY_SECTION)
                        )
                    }

                    // Gated on !homeStateLoading: sopAccepted is still null while
                    // loading, and DriverDailyStatus treats null as "not done" --
                    // without this gate the card would flash SOP (or whichever
                    // task an unloaded signal implies) for one frame before
                    // settling on the real next task.
                    if (!homeStateLoading && nextTask != null) {
                        item {
                            NextTaskCard(
                                dailyStatus = dailyStatus,
                                nextTask = nextTask,
                                onOpenSop = onOpenSop,
                                onOpenAttendance = onOpenAttendance,
                                onOpenChecklist = onOpenChecklist,
                                onOpenSafety = onOpenSafety,
                                modifier = Modifier.testTag(DriverMaestroTags.DAILY_FLOW_SECTION)
                            )
                        }
                    } else if (!homeStateLoading && showCompletionBanner) {
                        item {
                            AllTasksDoneRow(modifier = Modifier.testTag(DriverMaestroTags.DAILY_FLOW_SECTION))
                        }
                    }

                    if (!homeStateLoading && plannedStartMillis != null && plannedEndMillis != null) {
                        val todayWorkdayId = recentWorkDays.firstOrNull { it.dateEpochDay == dateEpochDay }?.id
                        item {
                            ScheduleCard(
                                plannedStartMillis = plannedStartMillis,
                                plannedEndMillis = plannedEndMillis,
                                planSource = planSource,
                                zone = zone,
                                onClick = todayWorkdayId?.let { id -> { onOpenWorkdayDetail(id) } }
                            )
                        }
                    }

                    item {
                        DriverSectionHeader(title = stringResource(R.string.driver_home_quick_actions_title))
                    }

                    item {
                        QuickActionGrid(
                            checklistDone = checklistSubmitted == true,
                            safetyDone = safetySubmitted == true,
                            onOpenChecklist = onOpenChecklist,
                            onOpenSafety = onOpenSafety,
                            onOpenSop = onOpenSop,
                            onOpenHistory = onOpenHistory
                        )
                    }

                    item {
                        RecentWorkHeader(onSeeAll = onOpenHistory)
                    }

                    item {
                        RecentWorkPreviewCard(
                            workDays = recentWorkDays,
                            zone = zone,
                            onOpen = onOpenHistory
                        )
                    }

                    // Kartu "Chat dengan admin" dihapus (chat = tab "Pesan"); kartu "Pengumuman"
                    // dihapus (UX v3 PR-I): pengumuman kini lewat lonceng -> layar Notifikasi.
                    }
                }
            }
        }
    }
}

// ─── Tiered-radius shells (hero 24dp w/ shadow, card 16dp, tile 14dp, icon 12dp) ──
// Dsv2ElevatedCard/DriverCard lock their radius to Dsv2Premium.cardPrimary/cardSecondary
// (28dp/20dp) and always draw a shadow. The approved design wants a different scale
// and shadow ONLY on the hero, so these are new local shells rather than a reuse.

/**
 * Titik hijau berdenyut untuk status "sedang bertugas" di kartu hero.
 * Menghormati reduce-motion: saat aktif, titik tetap digambar penuh, hanya
 * denyutnya yang dimatikan -- informasinya ada pada keberadaan titik, bukan
 * pada animasinya, jadi mematikan animasi tidak menghilangkan makna.
 */
@Composable
private fun OnDutyPulseDot() {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "onDutyDot")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(HERO_ON_DUTY_PULSE_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "onDutyDotAlpha"
        )
        animated
    }
    Box(
        modifier = Modifier
            .size(HERO_ON_DUTY_DOT_SIZE)
            .background(
                color = HERO_ON_DUTY_DOT_COLOR.copy(alpha = alpha),
                shape = CircleShape
            )
            .testTag(DriverMaestroTags.ON_DUTY_DOT)
    )
}

@Composable
internal fun HeroShell(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = Dsv2Spacing.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(HOME_CARD_RADIUS)
    // Gradien brand yang sudah mapan di Splash/Login, diambil lewat accessor
    // yang sama (#2563EB -> #173E9E di mode terang) alih-alih hex hardcode:
    // dark mode di app ini berbasis JAM, bukan setelan sistem, jadi hex terang
    // yang dipatok akan salah setelah pukul 19:00.
    val brand = rememberDmAuthBrandPalette(rememberDmAuthIsDark())
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = HOME_CARD_ELEVATION, shape = shape, clip = false)
            .clip(shape)
            .drawBehind {
                draw155Gradient(listOf(brand.gradientStart, brand.gradientEnd))
            }
    ) {
        // Konten hero berada di atas gradien gelap, jadi seluruh teks/ikon di
        // dalamnya mewarisi putih lewat LocalContentColor -- bukan onSurface
        // navy yang akan jadi navy-di-atas-navy.
        //
        // brand.wordmarkPrimary, BUKAN AppPalette.onPrimary: onPrimary ikut
        // tema keseluruhan (putih di light, navy gelap di dark -- karena
        // AppPalette.primary jadi TERANG di dark mode). Tapi gradien hero ini
        // sendiri (brand.gradientStart/End) tetap gelap di KEDUA mode -- di
        // dark mode malah lebih gelap lagi (#1E3A8A -> #0B1220). onPrimary
        // gelap di atas gradien yang JUGA gelap = teks nyaris tak terbaca sama
        // sekali di dark mode -- ditemukan lewat QA nyata di emulator, bug
        // pre-existing yang sama juga kena Driver Home. wordmarkPrimary sudah
        // dihitung tepat untuk skenario ini (dipakai wordmark Splash/Login).
        CompositionLocalProvider(LocalContentColor provides brand.wordmarkPrimary) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
                content = content
            )
        }
    }
}

@Composable
private fun FlatCardShell(
    modifier: Modifier = Modifier,
    containerColor: Color = AppPalette.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(HOME_CARD_RADIUS)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = containerColor,
        shadowElevation = HOME_CARD_ELEVATION,
        border = BorderStroke(1.dp, AppPalette.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs),
            content = content
        )
    }
}

// ─── Sections ──────────────────────────────────────────────────────────────

@Composable
private fun DriverHomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.lg)
    ) {
        DriverTonalCard(modifier = Modifier.fillMaxWidth(), secondary = false) {
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.xl))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(AppSpacing.md))
        }
        DriverCard(modifier = Modifier.fillMaxWidth(), secondary = false) {
            SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(AppSpacing.md))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.xxl))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.lg))
        }
        repeat(3) {
            DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
                SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.xxl))
            }
        }
    }
}

@Composable
private fun HomeHeaderRow(
    name: String,
    role: String,
    photo: ImageBitmap?,
    notificationCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val accountLabel = stringResource(R.string.account_settings_entry)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        // Blok 5. Pintu masuk layar Akun = seluruh blok identitas (avatar + nama +
        // peran + chevron) sebagai SATU tombol; gerbang toko aplikasi menuntut jalur
        // penghapusan akun mudah ditemukan. Di kanan: lonceng notifikasi (keluar hanya lewat Pengaturan).
        // Tanggal hanya tampil di StatusHeroCard (tidak diduplikasi di sini).
        // Nama perusahaan sengaja TIDAK ditampilkan -- keputusan white-label.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dsv2Sizing.touch)
                .clip(RoundedCornerShape(Dsv2Radii.small))
                .clickable(role = Role.Button, onClick = onOpenAccount)
                .testTag(DriverMaestroTags.HOME_ACCOUNT)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$name, $role, $accountLabel"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            // contentDescription avatar dikosongkan: gabungan blok (nama, peran,
            // label akun) sudah disampaikan oleh contentDescription tombol di atas
            // lewat mergeDescendants; deskripsi terpisah akan membacakan dua kali.
            ProfileAvatar(
                name = name,
                photo = photo,
                size = HOME_AVATAR_SIZE,
                containerColor = AppPalette.infoContainerSolid,
                contentColor = AppPalette.onInfoContainer,
                textStyle = MaterialTheme.typography.titleMedium
            )
            Column {
                Text(
                    text = stringResource(R.string.driver_home_greeting_format, name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Chevron duduk tepat setelah teks; Spacer menyerap sisa lebar sehingga
            // seluruh baris tetap SATU area klik (semantics/test tag tidak berubah).
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dsv2Sizing.icon)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        NotificationBell(count = notificationCount, onClick = onOpenNotifications)
    }
}

@Composable
private fun StatusHeroCard(
    statusText: String,
    statusKind: Dsv2StatusKind,
    dateText: String,
    timeRange: String,
    isWorking: Boolean,
    isCompleted: Boolean,
    attendancePhase: HomeAttendancePhase,
    checklistGateComplete: Boolean,
    checklistGateCompletedCount: Int,
    pendingAction: HomeAttendanceAction?,
    onOpenDetail: () -> Unit,
    onTapIn: () -> Unit,
    onTapOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroShell(modifier = modifier) {
        val statusIcon = when {
            isWorking -> Icons.Outlined.PlayCircle
            isCompleted -> Icons.Outlined.CheckCircle
            else -> Icons.Outlined.Schedule
        }
        // Di atas gradien navy, ikon status diwarnai putih, bukan warna semantik
        // (success/primary/warning): biru primary di atas navy praktis hilang,
        // dan amber/hijau pun turun kontrasnya. Sinyal "sedang bertugas"
        // dipindahkan ke titik hijau berdenyut di bawah, yang jauh lebih terbaca.
        //
        // LocalContentColor.current, BUKAN AppPalette.onPrimary langsung: warna
        // itu ikut tema keseluruhan dan jadi GELAP di dark mode, padahal gradien
        // hero ini tetap gelap di kedua mode -- teks/ikon jadi nyaris tak
        // terbaca. HeroShell sudah menyediakan warna yang benar (wordmarkPrimary)
        // lewat LocalContentColor; tint eksplisit di sini harus ikut itu, bukan
        // baca AppPalette.onPrimary langsung yang melewati provider tersebut.
        val statusColor = LocalContentColor.current

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.driver_home_today_title),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
            ) {
                if (isWorking) {
                    OnDutyPulseDot()
                }
                DriverStatusPill(text = statusText, kind = statusKind)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onOpenDetail
                )
                .testTag(DriverMaestroTags.ATTENDANCE_ENTRY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
        ) {
            DriverIconBubble(
                icon = statusIcon,
                contentDescription = null,
                tint = statusColor,
                size = Dsv2Sizing.iconContainerLg,
                iconSize = Dsv2Sizing.icon
            )
            Column {
                // onSurfaceVariant (slate gelap) tidak terbaca di atas gradien
                // navy -- pakai putih beralpha supaya tetap sekunder terhadap
                // rentang jam di bawahnya tanpa kehilangan kontras.
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f)
                )
                Text(text = timeRange, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (attendancePhase == HomeAttendancePhase.Open && !checklistGateComplete) {
            InlineAlert(
                kind = InlineAlertKind.Warning,
                message = stringResource(
                    R.string.driver_attendance_checklist_gate_warning,
                    checklistGateCompletedCount,
                    2
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DriverMaestroTags.CHECKLIST_GATE_WARNING)
            )
        }

        val tapInEnabled = attendancePhase == HomeAttendancePhase.Idle && pendingAction == null
        val tapOutEligible = attendancePhase == HomeAttendancePhase.Open &&
            pendingAction == null &&
            checklistGateComplete
        // Tap Out stays tappable while gate-blocked (not merely `enabled = false`)
        // so it can intercept the tap and give a Reject haptic instead of doing
        // nothing -- a disabled Compose `clickable` can never fire feedback.
        val tapOutBlocked = attendancePhase == HomeAttendancePhase.Open &&
            pendingAction == null &&
            !checklistGateComplete

        when (attendancePhase) {
            HomeAttendancePhase.Closed -> {
                // Sudah tap-in dan tap-out hari ini -- tidak ada aksi lagi untuk
                // ditampilkan, status pill di atas sudah bilang "Completed".
            }
            HomeAttendancePhase.Idle -> {
                DriverPrimaryButton(
                    text = stringResource(R.string.driver_attendance_button_tap_in),
                    onClick = onTapIn,
                    enabled = tapInEnabled,
                    loading = pendingAction == HomeAttendanceAction.TapIn,
                    spinnerOnly = true,
                    onHero = true,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DriverMaestroTags.TAP_IN_BUTTON)
                )
            }
            HomeAttendancePhase.Open -> {
                DriverPrimaryButton(
                    text = stringResource(R.string.driver_attendance_button_tap_out),
                    onClick = { if (tapOutEligible) onTapOut() },
                    enabled = tapOutEligible || tapOutBlocked,
                    visuallyLocked = tapOutBlocked,
                    loading = pendingAction == HomeAttendanceAction.TapOut,
                    spinnerOnly = true,
                    onHero = true,
                    hapticFeedbackType = if (tapOutEligible) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DriverMaestroTags.TAP_OUT_BUTTON)
                )
            }
        }
    }
}

/**
 * Replaces the old static "Checklist Harian" card: one card for whichever
 * daily task is still outstanding, in a fixed order (see [DriverDailyStatus]),
 * instead of listing all daily items regardless of what's actually next.
 */
@Composable
private fun NextTaskCard(
    dailyStatus: DriverDailyStatus,
    nextTask: DriverTask,
    onOpenSop: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes: Int
    val hintRes: Int
    val onClick: () -> Unit
    when (nextTask) {
        DriverTask.SOP -> {
            titleRes = R.string.driver_action_sop_title
            hintRes = R.string.driver_home_next_task_hint_sop
            onClick = onOpenSop
        }
        DriverTask.TAP_IN -> {
            titleRes = R.string.driver_attendance_button_tap_in
            hintRes = R.string.driver_home_next_task_hint_tap_in
            onClick = onOpenAttendance
        }
        DriverTask.VEHICLE_CHECKLIST -> {
            titleRes = R.string.driver_action_checklist_title
            hintRes = R.string.driver_home_next_task_hint_checklist
            onClick = onOpenChecklist
        }
        DriverTask.SAFETY_DECLARATION -> {
            titleRes = R.string.driver_action_safety_title
            hintRes = R.string.driver_home_next_task_hint_safety
            onClick = onOpenSafety
        }
    }

    FlatCardShell(modifier = modifier) {
        Text(
            text = stringResource(
                R.string.driver_home_next_task_eyebrow_format,
                dailyStatus.doneCount,
                2
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(hintRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { dailyStatus.doneCount / 2f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        DriverPrimaryButton(
            text = stringResource(R.string.driver_home_next_task_button),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Shown once, the first time all of today's tasks become done -- see the LaunchedEffect in DriverHomeScreen. */
@Composable
private fun AllTasksDoneRow(modifier: Modifier = Modifier) {
    FlatCardShell(modifier = modifier, containerColor = AppPalette.successContainerSolid) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            DriverIconBubble(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = AppPalette.success,
                size = Dsv2Sizing.iconContainer,
                iconSize = Dsv2Sizing.icon
            )
            Text(
                text = stringResource(R.string.driver_home_all_done_title),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    plannedStartMillis: Long?,
    plannedEndMillis: Long?,
    planSource: PlanSource?,
    zone: TimeZone,
    onClick: (() -> Unit)? = null
) {
    FlatCardShell(onClick = onClick) {
        Text(
            text = stringResource(R.string.driver_home_schedule_title),
            style = MaterialTheme.typography.titleMedium
        )
        if (plannedStartMillis != null && plannedEndMillis != null) {
            val startText = WorkdayTimeFormatUtil.formatClockOrDash(
                plannedStartMillis,
                dash = stringResource(R.string.driver_time_range_empty),
                zone = zone
            )
            val endText = WorkdayTimeFormatUtil.formatClockOrDash(
                plannedEndMillis,
                dash = stringResource(R.string.driver_time_range_empty),
                zone = zone
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
                DriverIconBubble(
                    icon = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = Dsv2Sizing.iconContainer,
                    iconSize = Dsv2Sizing.icon
                )
                Text(
                    text = stringResource(R.string.driver_time_range_full, startText, endText),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (planSource == PlanSource.EXPLICIT) {
                DriverStatusPill(
                    text = stringResource(R.string.driver_home_schedule_source_explicit),
                    kind = Dsv2StatusKind.Neutral
                )
            }
        }
    }
}

/**
 * Pratinjau ringkas riwayat kerja di Home.
 *
 * Datanya dari `DriverHomeUiState.recentWorkDays`, yang diisi
 * `GetWorkDaysByRangeUseCase` -- use case yang sama persis dipakai tab
 * "Riwayat kerja", atas jendela bergulir 3 bulan yang sama. Tidak ada query
 * yang diduplikasi di sini; komposabel ini murni menampilkan.
 */
@Composable
private fun RecentWorkHeader(onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.driver_home_recent_work_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(
            onClick = onSeeAll,
            modifier = Modifier.testTag(DriverMaestroTags.HISTORY_PREVIEW_SEE_ALL)
        ) {
            Text(stringResource(R.string.driver_home_recent_work_see_all) + " \u203A")
        }
    }
}

@Composable
private fun RecentWorkPreviewCard(
    workDays: List<WorkDay>,
    zone: TimeZone,
    onOpen: () -> Unit
) {
    val dash = stringResource(R.string.driver_time_range_empty)
    val notOut = stringResource(R.string.driver_home_recent_work_not_clocked_out)
    val running = stringResource(R.string.driver_home_recent_work_in_progress)
    FlatCardShell(modifier = Modifier.testTag(DriverMaestroTags.HISTORY_PREVIEW)) {
        if (workDays.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dsv2Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.driver_home_recent_work_empty_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            workDays.forEachIndexed { index, workDay ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val date = java.time.LocalDate.ofEpochDay(workDay.dateEpochDay.toLong())
                val monthShort = date.month.getDisplayName(java.time.format.TextStyle.SHORT, LocalConfiguration.current.locales[0])
                val clockIn = WorkdayTimeFormatUtil.formatClockOrDash(workDay.clockInMillis, dash, zone)
                val hasOut = workDay.clockOutMillis > 0L
                val main = if (hasOut) {
                    stringResource(
                        R.string.driver_time_range_full,
                        clockIn,
                        WorkdayTimeFormatUtil.formatClockOrDash(workDay.clockOutMillis, dash, zone)
                    )
                } else {
                    stringResource(R.string.driver_time_range_full, clockIn, notOut)
                }
                val caption = if (hasOut) {
                    stringResource(
                        R.string.driver_home_recent_work_duration_format,
                        workDay.totalMinutes / 60,
                        workDay.totalMinutes % 60
                    )
                } else {
                    running
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .dmClickable(onClick = onOpen, role = Role.Button),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
                ) {
                    Column(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = monthShort,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = main,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DriverStatusPill(
                        text = stringResource(workDay.previewStatusRes()),
                        kind = workDay.previewStatusKind()
                    )
                }
            }
        }
    }
}

/** Lonceng notifikasi: wadah tonal kontras + lencana angka merah ("9+" bila > 9). */
@Composable
private fun NotificationBell(count: Int, onClick: () -> Unit) {
    val badge = formatNotificationBadge(count)
    val description = if (badge == null) {
        stringResource(R.string.driver_notifications_bell_cd)
    } else {
        stringResource(R.string.driver_notifications_bell_cd_count_format, badge)
    }
    Box(modifier = Modifier.testTag(DriverMaestroTags.BELL)) {
        Box(
            modifier = Modifier
                .size(Dsv2Sizing.touch)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            DmIconButton(
                onClick = onClick,
                imageVector = Icons.Outlined.Notifications,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(horizontal = 4.dp)
                    .testTag(DriverMaestroTags.BELL_BADGE),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

/**
 * Status pratinjau sengaja hanya membedakan "sudah disetujui expat" vs "masih
 * berjalan/menunggu" -- Home bukan tempat menampilkan seluruh state review;
 * itu urusan tab Riwayat kerja yang punya ruang untuk menjelaskannya.
 */
@StringRes
private fun WorkDay.previewStatusRes(): Int = when {
    approvedByExpat == true -> R.string.driver_home_recent_work_status_approved
    else -> R.string.driver_home_recent_work_status_pending
}

private fun WorkDay.previewStatusKind(): Dsv2StatusKind = when {
    approvedByExpat == true -> Dsv2StatusKind.Approved
    else -> Dsv2StatusKind.Pending
}

private data class QuickActionTile(
    val title: String,
    val icon: ImageVector,
    /** Ramp gradien wadah ikon (awal -> akhir) untuk kategori tile ini. */
    val gradient: List<Color>,
    /**
     * null = tile ini memang tidak punya konsep selesai/belum (SOP, Riwayat),
     * jadi tidak diberi badge dan tidak ikut beranimasi.
     */
    val statusDone: Boolean?,
    val testTag: String,
    val onClick: () -> Unit
) {
    /** Hanya tile yang benar-benar menunggu tindakan yang berdenyut. */
    val pending: Boolean get() = statusDone == false
}

@Composable
private fun QuickActionGrid(
    checklistDone: Boolean,
    safetyDone: Boolean,
    onOpenChecklist: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenSop: () -> Unit,
    onOpenHistory: () -> Unit
) {
    // Dua tile pertama punya status penyelesaian nyata (dan karena itu badge +
    // denyut); dua terakhir murni navigasi, tanpa konsep selesai/belum.
    val tiles = listOf(
        QuickActionTile(
            title = stringResource(R.string.driver_action_checklist_title),
            icon = Icons.Outlined.CheckCircle,
            gradient = TILE_GRADIENT_CHECKLIST,
            statusDone = checklistDone,
            testTag = DriverMaestroTags.CHECKLIST_ENTRY,
            onClick = onOpenChecklist
        ),
        QuickActionTile(
            title = stringResource(R.string.driver_action_safety_title),
            icon = Icons.Outlined.Shield,
            gradient = TILE_GRADIENT_SAFETY,
            statusDone = safetyDone,
            testTag = DriverMaestroTags.SAFETY_ENTRY,
            onClick = onOpenSafety
        ),
        QuickActionTile(
            title = stringResource(R.string.driver_action_sop_title),
            icon = Icons.Outlined.Description,
            gradient = TILE_GRADIENT_SOP,
            statusDone = null,
            testTag = DriverMaestroTags.SOP_ENTRY,
            onClick = onOpenSop
        ),
        QuickActionTile(
            title = stringResource(R.string.driver_action_history_title),
            icon = Icons.Outlined.History,
            gradient = TILE_GRADIENT_HISTORY,
            statusDone = null,
            testTag = DriverMaestroTags.HISTORY_ENTRY,
            onClick = onOpenHistory
        )
    )

    // Grid manual (bukan LazyVerticalGrid) supaya tinggi mengikuti konten --
    // tinggi tetap sebelumnya (180dp) memotong baris kedua begitu judul+subjudul
    // tile butuh lebih dari itu (kelihatan lewat QA emulator).
    Column(verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
            ) {
                rowTiles.forEach { tile ->
                    QuickActionTileCard(
                        tile = tile,
                        modifier = Modifier.weight(1f).aspectRatio(1.2f)
                    )
                }
                if (rowTiles.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionTileCard(tile: QuickActionTile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HOME_CARD_RADIUS)
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = tile.onClick
            )
            .testTag(tile.testTag),
        shape = shape,
        color = AppPalette.surface,
        shadowElevation = HOME_CARD_ELEVATION,
        // Tanpa border. Ini inti "Widget-Style": chrome kartu dikurangi supaya
        // ikon bergradien yang jadi fokus visual, bukan garis tepi kartunya.
        // Kartu tetap terbaca sebagai bidang tersendiri karena surface putih
        // berdiri di atas latar #F6F8FC.
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dsv2Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TileIconBadgeBox(tile = tile)
            Spacer(Modifier.height(Dsv2Spacing.sm))
            // Teks status ("Belum dikirim"/"Sudah dikirim") sengaja tidak ada
            // di sini -- informasinya dibawa badge di sudut wadah ikon, plus
            // label aksesibilitas badge untuk pengguna screen reader.
            Text(
                text = tile.title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Wadah ikon besar bergradien + badge status di sudutnya.
 *
 * Badge dibedakan BENTUK, bukan cuma warna: pending memakai segitiga peringatan
 * (dengan tanda seru di dalamnya), selesai memakai lingkaran bercentang. Dua
 * lingkaran yang cuma beda warna akan terbaca sama saja bagi driver yang buta
 * warna, dan pada ukuran badge sekecil ini glyph di dalam lingkaran identik
 * praktis tidak terbaca di layar.
 */
@Composable
private fun TileIconBadgeBox(tile: QuickActionTile) {
    val reduceMotion = LocalReduceMotion.current
    // Hanya tile yang menunggu tindakan yang berdenyut. SOP dan Riwayat tidak
    // punya konsep selesai/belum, jadi diam -- gerakan di sini berarti "ada
    // yang perlu dikerjakan", bukan hiasan yang dipasang di semua tempat.
    val scale = if (tile.pending && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "tilePulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = TILE_PULSE_MAX_SCALE,
            animationSpec = infiniteRepeatable(
                animation = tween(TILE_PULSE_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tilePulseScale"
        )
        animated
    } else {
        1f
    }

    Box {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                // Bayangan hanya di wadah ikon, bukan di seluruh kartu -- itu
                // yang memberi kesan terangkat tanpa membuat grid jadi ramai.
                .shadow(
                    elevation = TILE_ICON_ELEVATION,
                    shape = RoundedCornerShape(TILE_ICON_RADIUS),
                    clip = false
                )
                .clip(RoundedCornerShape(TILE_ICON_RADIUS))
                .size(TILE_ICON_CONTAINER)
                .drawBehind { draw155Gradient(tile.gradient) },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = tile.icon,
                contentDescription = null,
                // Color.White tetap, BUKAN AppPalette.onPrimary: tile.gradient
                // (TILE_GRADIENT_*) adalah hex tetap yang selalu vivid di kedua
                // mode, tidak ikut apply(darkTheme). onPrimary ikut tema dan
                // jadi navy gelap di dark mode -- glyph jadi nyaris tak
                // terlihat di atas gradien yang tetap terang/vivid.
                tint = Color.White,
                modifier = Modifier.size(TILE_ICON_GLYPH)
            )
        }

        if (tile.statusDone != null) {
            TileStatusBadge(
                done = tile.statusDone,
                tileTitle = tile.title,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * Badge status di sudut wadah ikon.
 *
 * Dibedakan BENTUK, bukan warna: segitiga peringatan untuk pending, lingkaran
 * bercentang untuk selesai. Warna saja tidak cukup -- screen reader tidak
 * mengumumkan warna, dan pembeda berbasis warna gagal untuk driver buta warna.
 *
 * Karena badge ini tidak punya teks yang terlihat, label aksesibilitasnya wajib
 * menyebut statusnya DENGAN KATA, lengkap dengan nama tile-nya, supaya
 * pengguna screen reader mendengar "Checklist kendaraan: Belum dikirim" alih-
 * alih sekadar "Belum dikirim" tanpa tahu itu milik tile yang mana.
 */
@Composable
private fun TileStatusBadge(
    done: Boolean,
    tileTitle: String,
    modifier: Modifier = Modifier
) {
    val statusWord = if (done) {
        stringResource(R.string.driver_action_status_submitted)
    } else {
        stringResource(R.string.driver_action_status_not_submitted)
    }
    val description = stringResource(
        R.string.driver_action_status_content_description,
        tileTitle,
        statusWord
    )
    val glyph = if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning
    Box(
        modifier = modifier
            // Digeser keluar sedikit supaya terbaca sebagai badge yang menempel
            // di sudut wadah ikon, bukan glyph yang menimpa isinya.
            .offset(x = TILE_BADGE_OFFSET, y = -TILE_BADGE_OFFSET)
            .size(TILE_BADGE_SIZE)
            .semantics { contentDescription = description }
            .testTag(
                if (done) DriverMaestroTags.TILE_BADGE_DONE else DriverMaestroTags.TILE_BADGE_PENDING
            ),
        contentAlignment = Alignment.Center
    ) {
        // Rim putih dibuat dari glyph yang SAMA diperbesar, bukan lingkaran di
        // belakangnya: lingkaran putih di belakang segitiga menyisakan tepian
        // putih yang menonjol dan merusak siluet segitiga -- padahal justru
        // siluet itu yang membedakan "pending" dari "selesai" bagi driver yang
        // tidak bisa mengandalkan warna.
        androidx.compose.material3.Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = AppPalette.surface,
            modifier = Modifier.fillMaxSize()
        )
        androidx.compose.material3.Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = if (done) AppPalette.success else AppPalette.warningIcon,
            modifier = Modifier.size(TILE_BADGE_SIZE - TILE_BADGE_RIM * 2)
        )
    }
}

@Composable
private fun DriverActionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlatCardShell(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DriverIconBubble(
                icon = icon,
                contentDescription = null,
                tint = AppPalette.primary,
                size = Dsv2Sizing.iconContainerLg,
                iconSize = Dsv2Sizing.icon
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun resolveDriverName(
    driverName: String?,
    driverEmail: String?,
    fallbackName: String
): String {
    if (!driverName.isNullOrBlank()) return driverName
    val emailPrefix = driverEmail?.substringBefore("@")?.trim()
    if (!emailPrefix.isNullOrBlank()) return emailPrefix
    return fallbackName
}

private fun isLocationProviderEnabled(context: android.content.Context): Boolean {
    if (!hasLocationPermission(context)) return false
    val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    val gpsEnabled = runCatching { manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) }
        .onFailure { err ->
            if (err is SecurityException) {
                UiErrorLogger.e("DM/DriverHome", "provider check security exception", err)
            }
        }
        .getOrDefault(false)
    val networkEnabled = runCatching { manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) }
        .onFailure { err ->
            if (err is SecurityException) {
                UiErrorLogger.e("DM/DriverHome", "provider check security exception", err)
            }
        }
        .getOrDefault(false)
    return gpsEnabled || networkEnabled
}

private fun resolveHomeAttendancePhase(todayAttendance: TodayAttendanceUi?): HomeAttendancePhase {
    val clockIn = todayAttendance?.clockInMillis
    val clockOut = todayAttendance?.clockOutMillis
    return when {
        clockIn == null -> HomeAttendancePhase.Idle
        clockOut != null && clockOut > clockIn -> HomeAttendancePhase.Closed
        else -> HomeAttendancePhase.Open
    }
}

private fun buildTimeRange(
    clockInMillis: Long?,
    clockOutMillis: Long?,
    zone: TimeZone,
    fullRangeFormat: String,
    inOnlyFormat: String,
    emptyRange: String
): String {
    val inText = clockInMillis?.let { TimeUtil.formatHm(it, zone) }
    val outText = clockOutMillis?.let { TimeUtil.formatHm(it, zone) }
    return when {
        inText != null && outText != null -> String.format(Locale.getDefault(), fullRangeFormat, inText, outText)
        inText != null && outText == null -> String.format(Locale.getDefault(), inOnlyFormat, inText)
        else -> emptyRange
    }
}

private fun formatTanggalIndo(epochDay: Int): String {
    val date = LocalDate.ofEpochDay(epochDay.toLong())
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMd")
    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
    return date.format(formatter)
}
