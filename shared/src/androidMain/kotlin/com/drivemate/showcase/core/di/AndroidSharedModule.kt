package com.drivemate.showcase.core.di

import com.drivemate.showcase.domain.repository.ChatThreadRepository
import com.drivemate.showcase.domain.repository.ChatDirectoryRepository
import com.drivemate.showcase.data.repository.FirebaseChatThreadRepository
import com.drivemate.showcase.data.repository.FirebasePushTokenRepository
import com.drivemate.showcase.domain.repository.PushTokenRepository
import com.drivemate.showcase.data.repository.FirebaseChatDirectoryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.drivemate.showcase.core.config.FirestoreEmulatorConfig
import com.drivemate.showcase.core.log.AndroidDebugFlags
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.service.AndroidReverseGeocodingService
import com.drivemate.showcase.core.service.ReverseGeocodingService
import com.drivemate.showcase.data.FirestoreServiceImpl
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.data.repository.FirebaseAdminDataRepository
import com.drivemate.showcase.data.repository.FirebaseAnnouncementRepository
import com.drivemate.showcase.data.repository.FirebaseAttendanceRepository
import com.drivemate.showcase.data.repository.FirebaseAuthRepository
import com.drivemate.showcase.data.repository.FirebaseChatRepository
import com.drivemate.showcase.data.repository.FirebaseChecklistRepository
import com.drivemate.showcase.data.repository.FirebaseDriverAssignmentRepository
import com.drivemate.showcase.data.repository.FirebaseLocationRepository
import com.drivemate.showcase.data.repository.FirebaseSafetyConfigRepository
import com.drivemate.showcase.data.repository.FirebaseSafetyRepository
import com.drivemate.showcase.data.repository.FirebaseSpeedViolationRepository
import com.drivemate.showcase.data.repository.FirebaseUserDirectoryRepository
import com.drivemate.showcase.data.repository.FirebaseUserPhotoRepository
import com.drivemate.showcase.data.repository.FirebaseWorkdayRepository
import com.drivemate.showcase.data.repository.FirestoreWorkCalendarRepository
import com.drivemate.showcase.data.repository.PersistedTrackingStatusRepository
import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.repository.AnnouncementRepository
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.data.repository.FirebaseAccountDeletionRepository
import com.drivemate.showcase.domain.repository.AccountDeletionRepository
import com.drivemate.showcase.domain.usecase.account.GetAccountDeletionRequestUseCase
import com.drivemate.showcase.domain.usecase.account.RequestAccountDeletionUseCase
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.ChatRepository
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository
import com.drivemate.showcase.domain.repository.LocationRepository
import com.drivemate.showcase.domain.repository.SafetyConfigRepository
import com.drivemate.showcase.domain.repository.SafetyRepository
import com.drivemate.showcase.domain.repository.SpeedViolationRepository
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import com.drivemate.showcase.domain.repository.UserPhotoRepository
import com.drivemate.showcase.domain.usecase.profile.DeleteProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.GetProfilePhotoUseCase
import com.drivemate.showcase.domain.usecase.profile.SaveProfilePhotoUseCase
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.repository.WorkDayRepository
import android.content.pm.ApplicationInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.Locale

private const val FIRESTORE_DATABASE_ID = "drivemate-database"

val AndroidSharedModule = module {

    // Firebase singletons
    single {
        FirebaseAuth.getInstance().apply {
            // Debug-only, same two-layer guard as Firestore: the release build type hardcodes
            // the flag to false, and the debug flag check below keeps a sideloaded build honest.
            val cfg = getOrNull<FirestoreEmulatorConfig>() ?: FirestoreEmulatorConfig.Disabled
            val debuggable = (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (cfg.enabled && debuggable && cfg.authPort > 0) {
                useEmulator(cfg.host, cfg.authPort)
                DMLog.w("DM/Firebase", "⚠️ AUTH EMULATOR ACTIVE at ${cfg.host}:${cfg.authPort} — NOT production.")
            }
        }
    }

    single {
        val context = androidContext()
        AndroidDebugFlags.isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (AndroidDebugFlags.isDebug) {
            FirebaseFirestore.setLoggingEnabled(true)
        }
        val app = FirebaseApp.getInstance()
        // Missing registration (e.g. a consumer of :shared other than composeApp) means
        // Disabled, not a crash — absence must fail closed to production, never to "on".
        val emulatorConfig = getOrNull<FirestoreEmulatorConfig>() ?: FirestoreEmulatorConfig.Disabled
        val useEmulator = emulatorConfig.enabled && AndroidDebugFlags.isDebug
        FirebaseFirestore.getInstance(app, FIRESTORE_DATABASE_ID).apply {
            if (useEmulator) {
                useEmulator(emulatorConfig.host, emulatorConfig.port)
                DMLog.w(
                    "DM/Firebase",
                    "⚠️ FIRESTORE EMULATOR ACTIVE at ${emulatorConfig.host}:${emulatorConfig.port} — NOT production."
                )
            }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder().build()
                )
                .build()
            DMLog.i(
                "DM/Firebase",
                "Firestore databaseId=$FIRESTORE_DATABASE_ID (named instance)" +
                    if (useEmulator) " [EMULATOR]" else ""
            )
        }
    }

    // Firestore service
    single {
        // db = get() resolves the Firestore singleton above first, which sets
        // AndroidDebugFlags.isDebug — read it only after, so this recomputes the exact
        // same effective decision that singleton used to decide whether to actually
        // call useEmulator(). Passing the raw (unvalidated) config here instead would
        // let FirestoreServiceImpl's guard expect emulator settings that were never
        // applied, e.g. if enabled=true but the debug check somehow disagreed.
        val db = get<FirebaseFirestore>()
        val rawConfig = getOrNull<FirestoreEmulatorConfig>() ?: FirestoreEmulatorConfig.Disabled
        val effectiveConfig = if (rawConfig.enabled && AndroidDebugFlags.isDebug) {
            rawConfig
        } else {
            FirestoreEmulatorConfig.Disabled
        }
        FirestoreServiceImpl(
            db = db,
            auth = get(),
            defaultTimeoutMs = 15_000L,
            emulatorConfig = effectiveConfig
        )
    }
    single<FirestoreService> { get<FirestoreServiceImpl>() }

    // Repositories
    singleOf(::FirebaseAuthRepository) { bind<AuthRepository>() }
    // Blok 5. Definisi ini sengaja di modul ANDROID, bukan commonModule: belum ada
    // implementasi iOS-nya, dan mendaftarkan use case yang bergantung padanya di
    // commonModule akan membuat resolusi Koin di iOS gagal begitu ada yang memintanya.
    singleOf(::FirebaseAccountDeletionRepository) { bind<AccountDeletionRepository>() }
    single { RequestAccountDeletionUseCase(get(), get()) }
    single { GetAccountDeletionRequestUseCase(get(), get()) }
    singleOf(::FirebaseUserPhotoRepository) { bind<UserPhotoRepository>() }
    single { GetProfilePhotoUseCase(get(), get()) }
    single { SaveProfilePhotoUseCase(get(), get()) }
    single { DeleteProfilePhotoUseCase(get(), get()) }
    singleOf(::FirebaseAttendanceRepository) { bind<AttendanceRepository>() }
    singleOf(::FirebaseLocationRepository) { bind<LocationRepository>() }
    singleOf(::FirebaseWorkdayRepository) { bind<WorkDayRepository>() }
    singleOf(::FirebaseChecklistRepository) { bind<ChecklistRepository>() }
    singleOf(::FirebaseSafetyRepository) { bind<SafetyRepository>() }
    // R3 — deteksi batas kecepatan
    singleOf(::FirebaseSafetyConfigRepository) { bind<SafetyConfigRepository>() }
    singleOf(::FirebaseSpeedViolationRepository) { bind<SpeedViolationRepository>() }
    singleOf(::FirebaseAdminDataRepository) { bind<AdminDataRepository>() }
    singleOf(::FirebaseAnnouncementRepository) { bind<AnnouncementRepository>() }
    singleOf(::FirebaseDriverAssignmentRepository) { bind<DriverAssignmentRepository>() }
    single<ChatRepository> {
        val context = androidContext()
        val fallbackResId = context.resources.getIdentifier(
            "driver_name_fallback",
            "string",
            context.packageName
        )
        check(fallbackResId != 0) { "Missing driver_name_fallback string resource in app package" }
        FirebaseChatRepository(
            displayNameFallback = context.getString(fallbackResId),
            fs = get()
        )
    }
    // Chat v7 (Android saja; interface baru, tidak menyentuh ChatRepository yang dipakai iOS)
    single<ChatDirectoryRepository> { FirebaseChatDirectoryRepository(db = get(), fs = get()) }
    single<PushTokenRepository> { FirebasePushTokenRepository(db = get()) }
    single<ChatThreadRepository> { FirebaseChatThreadRepository(db = get(), fs = get()) }
    single<UserDirectoryRepository> {
        val context = androidContext()
        val fallbackResId = context.resources.getIdentifier(
            "driver_name_fallback",
            "string",
            context.packageName
        )
        check(fallbackResId != 0) { "Missing driver_name_fallback string resource in app package" }
        FirebaseUserDirectoryRepository(
            driverNameFallback = context.getString(fallbackResId),
            fs = get()
        )
    }
    single<TrackingStatusRepository> { PersistedTrackingStatusRepository(androidContext()) }
    singleOf(::FirestoreWorkCalendarRepository) { bind<WorkCalendarRepository>() }

    // Reverse geocoding (Android-only)
    single<ReverseGeocodingService> {
        AndroidReverseGeocodingService(
            geocoder = android.location.Geocoder(
                androidContext(),
                Locale.forLanguageTag("id-ID")
            )
        )
    }
}
