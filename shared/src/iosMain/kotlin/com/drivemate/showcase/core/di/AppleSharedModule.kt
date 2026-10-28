package com.drivemate.showcase.core.di

import com.drivemate.showcase.data.repository.ApplePersistedTrackingStatusRepository
import com.drivemate.showcase.data.repository.IOSAdminDataRepository
import com.drivemate.showcase.data.repository.IOSAttendanceRepository
import com.drivemate.showcase.data.repository.IOSChatRepository
import com.drivemate.showcase.data.repository.IOSChecklistRepository
import com.drivemate.showcase.data.repository.IOSDriverAssignmentRepository
import com.drivemate.showcase.data.repository.IOSFirebaseAnnouncementRepository
import com.drivemate.showcase.data.repository.IOSFirebaseAuthRepository
import com.drivemate.showcase.data.repository.IOSFirebaseWorkdayRepository
import com.drivemate.showcase.data.repository.IOSLocationRepository
import com.drivemate.showcase.data.repository.IOSSafetyConfigRepository
import com.drivemate.showcase.data.repository.IOSSafetyRepository
import com.drivemate.showcase.data.repository.IOSFirebaseAccountDeletionRepository
import com.drivemate.showcase.data.repository.IOSPushTokenRepository
import com.drivemate.showcase.data.repository.IOSSpeedViolationRepository
import com.drivemate.showcase.data.repository.IOSUserDirectoryRepository
import com.drivemate.showcase.data.repository.IOSWorkCalendarRepository
import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.repository.AnnouncementRepository
import com.drivemate.showcase.domain.repository.AttendanceRepository
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.ChatRepository
import com.drivemate.showcase.domain.repository.ChecklistRepository
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository
import com.drivemate.showcase.domain.repository.LocationRepository
import com.drivemate.showcase.domain.repository.SafetyConfigRepository
import com.drivemate.showcase.domain.repository.SafetyRepository
import com.drivemate.showcase.domain.repository.AccountDeletionRepository
import com.drivemate.showcase.domain.repository.PushTokenRepository
import com.drivemate.showcase.domain.repository.SpeedViolationRepository
import com.drivemate.showcase.domain.usecase.account.GetAccountDeletionRequestUseCase
import com.drivemate.showcase.domain.usecase.account.RequestAccountDeletionUseCase
import com.drivemate.showcase.domain.repository.TrackingStatusRepository
import com.drivemate.showcase.domain.repository.UserDirectoryRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.repository.WorkDayRepository
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module

val AppleSharedModule = module {
    single { IOSFirebaseAuthRepository() }
    single<AuthRepository> { get<IOSFirebaseAuthRepository>() }

    single { IOSFirebaseAnnouncementRepository() }
    single<AnnouncementRepository> { get<IOSFirebaseAnnouncementRepository>() }

    single { ApplePersistedTrackingStatusRepository() }
    single<TrackingStatusRepository> { get<ApplePersistedTrackingStatusRepository>() }

    single { IOSFirebaseWorkdayRepository(get<AuthRepository>(), get<WorkCalendarRepository>()) }
    single<WorkDayRepository> { get<IOSFirebaseWorkdayRepository>() }

    single { IOSWorkCalendarRepository() }
    single<WorkCalendarRepository> { get<IOSWorkCalendarRepository>() }

    single { IOSLocationRepository() }
    single<LocationRepository> { get<IOSLocationRepository>() }

    // ── Repository iOS dengan jalur baca/tulis nyata ──────────────────────────
    // (Komentar lama di sini menyebut blok ini "iOS stubs — throw
    // UnsupportedOperationException until wired". Itu sudah tidak benar sejak
    // lama: ketujuhnya terimplementasi penuh. Stub yang benar-benar tersisa
    // ditandai di bloknya sendiri di bawah.)

    single { IOSAdminDataRepository() }
    single<AdminDataRepository> { get<IOSAdminDataRepository>() }

    single { IOSAttendanceRepository() }
    single<AttendanceRepository> { get<IOSAttendanceRepository>() }

    single { IOSChatRepository() }
    single<ChatRepository> { get<IOSChatRepository>() }

    single { IOSChecklistRepository() }
    single<ChecklistRepository> { get<IOSChecklistRepository>() }

    single { IOSSafetyRepository() }
    single<SafetyRepository> { get<IOSSafetyRepository>() }

    single { IOSUserDirectoryRepository() }
    single<UserDirectoryRepository> { get<IOSUserDirectoryRepository>() }

    single { IOSDriverAssignmentRepository() }
    single<DriverAssignmentRepository> { get<IOSDriverAssignmentRepository>() }

    // ── Stub sungguhan — R3 (deteksi batas kecepatan), terblokir infrastruktur ─
    // Terdaftar meski stub supaya definisi `commonModule` untuk
    // GetSpeedLimitConfigUseCase/ReportSpeedViolationUseCase tidak menggantung.
    // `single {}` Koin itu lazy, jadi definisi menggantung tidak terdeteksi saat
    // startKoin maupun oleh `scripts/verify-ios.sh` — ia meledak sebagai
    // NoDefinitionFoundException di splash saat pemanggil iOS pertama muncul.
    // Alasan lengkap ada di KDoc IOSSafetyConfigRepository.

    single { IOSSafetyConfigRepository() }
    single<SafetyConfigRepository> { get<IOSSafetyConfigRepository>() }

    single { IOSSpeedViolationRepository() }
    single<SpeedViolationRepository> { get<IOSSpeedViolationRepository>() }

    // ── Penghapusan akun di dalam aplikasi — gerbang Apple 5.1.1(v) (Blok 5) ────
    // Kedua use case didaftarkan DI SINI, bukan di `commonModule`, dan itu bukan
    // kelalaian: Android mendaftarkannya di `AndroidSharedModule` juga. Selama iOS
    // belum punya implementasi repository-nya, menaruhnya di commonModule akan
    // membuat definisi menggantung yang persis seperti ranjau R3 di atas — lolos
    // startKoin, lolos verify-ios.sh, lalu meledak sebagai NoDefinitionFoundException
    // pada pemanggil iOS pertama. Sekarang implementasinya ADA, jadi pemindahannya
    // ke commonModule menjadi mungkin — tapi itu refactor tersendiri yang menyentuh
    // kedua platform, bukan efek samping dari menambah fitur ini.
    single { IOSFirebaseAccountDeletionRepository() }
    single<AccountDeletionRepository> { get<IOSFirebaseAccountDeletionRepository>() }
    single { RequestAccountDeletionUseCase(get(), get()) }
    single { GetAccountDeletionRequestUseCase(get(), get()) }

    // ── Token FCM privat `fcm_tokens` (misi fcm-legacy-cabut T4) ────────────────
    // Padanan `single<PushTokenRepository>` di AndroidSharedModule. Dipakai
    // IOSPushTokenBridge lewat IOSFoundationBridge.
    single { IOSPushTokenRepository() }
    single<PushTokenRepository> { get<IOSPushTokenRepository>() }
}

private var iosKoinApplication: KoinApplication? = null

fun ensureIosKoinStarted(): Koin {
    val existing = iosKoinApplication
    if (existing != null) return existing.koin

    val started = startKoin {
        modules(
            commonModule,
            AppleSharedModule
        )
    }

    iosKoinApplication = started
    return started.koin
}
