package com.drivemate.showcase.di

import com.drivemate.showcase.BuildConfig
import com.drivemate.showcase.core.config.FirestoreEmulatorConfig
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.data.RoleCache
import com.drivemate.showcase.data.RoleCacheImpl
import com.drivemate.showcase.feature.announcement.AnnouncementsViewModel
import com.drivemate.showcase.feature.auth.AuthViewModel
import com.drivemate.showcase.feature.chat.ChatInboxViewModel
import com.drivemate.showcase.feature.chat.ChatThreadViewModel
import com.drivemate.showcase.feature.driver.DriverChecklistViewModel
import com.drivemate.showcase.feature.driver.DriverSafetyDeclarationViewModel
import com.drivemate.showcase.feature.driver.DriverAnnouncementsViewModel
import com.drivemate.showcase.feature.driver.DriverSopViewModel
import com.drivemate.showcase.feature.driver.DriverViewModel
import com.drivemate.showcase.feature.driver.DriverWorkHistoryAgendaViewModel
import com.drivemate.showcase.feature.driver.DriverWorkdayDetailViewModel
import com.drivemate.showcase.feature.settings.DriverSettingsViewModel
import com.drivemate.showcase.feature.expat.ExpatHomeViewModel
import com.drivemate.showcase.feature.expat.settings.ExpatSettingsViewModel
import com.drivemate.showcase.feature.expat.detail.ExpatDriverDetailViewModel
import com.drivemate.showcase.feature.expat.review.ExpatMonthlyReviewViewModel
import com.drivemate.showcase.feature.expat.review.ExpatWorkdayDetailViewModel
import com.drivemate.showcase.feature.session.SessionViewModel
import com.drivemate.showcase.core.model.Role
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import com.drivemate.showcase.feature.account.AccountSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val androidModule = module {

    DMLog.i("DM/DI", "Registering Android ViewModels (Auth, Driver, Expat, Session)")

    // ---- Android singletons (require Context)
    single<RoleCache> { RoleCacheImpl(androidContext()).apply { migrateIfNeeded() } }
    single { FirebaseMessaging.getInstance() }
    single {
        com.drivemate.showcase.messaging.PushTokenManager(
            repo = get(),
            source = com.drivemate.showcase.messaging.FirebasePushTokenSource(get()),
            store = com.drivemate.showcase.messaging.SharedPrefsPushTokenStore(androidContext()),
            appVersion = com.drivemate.showcase.BuildConfig.VERSION_NAME,
            onSignedOut = {
                androidx.core.app.NotificationManagerCompat.from(androidContext()).cancelAll()
                com.drivemate.showcase.messaging.PushRoutes.bus.consume()
            }
        )
    }

    // Dev-only Firestore emulator redirect, consumed by :shared's Firestore singleton.
    // BuildConfig.USE_FIRESTORE_EMULATOR is hardcoded false in release (build.gradle.kts) —
    // this registration can never carry a true value into a release build.
    single {
        FirestoreEmulatorConfig(
            enabled = BuildConfig.USE_FIRESTORE_EMULATOR,
            host = BuildConfig.FIRESTORE_EMULATOR_HOST,
            port = BuildConfig.FIRESTORE_EMULATOR_PORT,
            authPort = BuildConfig.AUTH_EMULATOR_PORT
        )
    }

    // ---- ViewModels (typed DSL - Koin 4.1.1)
    viewModelOf(::AuthViewModel)

    // Blok 5. Lambda `viewModel { }` EKSPLISIT, bukan viewModelOf: constructor-nya punya
    // parameter ber-default (`nowMillis: () -> Long`), dan Koin tetap mencoba me-resolve
    // `() -> Long` sebagai dependensi lalu gagal saat runtime, bukan saat kompilasi.
    viewModel { AccountSettingsViewModel(get(), get()) }
    viewModel {
        DriverViewModel(
            tapIn = get(),
            tapOut = get(),
            updateLocation = get(),
            getTodayAttendance = get(),
            getWorkDaysByRange = get(),
            getDriverTodaySchedule = get(),
            getChecklistStatus = get(),
            getSafetyStatus = get(),
            getSopStatus = get(),
            getUserDisplayName = get(),
            authRepo = get(),
            observeTrackingStatus = get(),
            updateTrackingStatus = get(),
            clearDriverLocation = get(),
            reverseGeocoder = get(),
            appContext = androidContext()
        )
    }
    viewModelOf(::DriverChecklistViewModel)
    viewModelOf(::DriverSafetyDeclarationViewModel)
    viewModelOf(::DriverSopViewModel)
    viewModelOf(::DriverWorkHistoryAgendaViewModel)
    viewModelOf(::DriverAnnouncementsViewModel)
    single { com.drivemate.showcase.feature.driver.notifications.DriverNotificationReadStore(androidContext()) }
    viewModel {
        com.drivemate.showcase.feature.driver.notifications.DriverNotificationsViewModel(
            observeAnnouncements = get(),
            store = get(),
            approvalLabel = { com.drivemate.showcase.util.WorkdayTimeFormatUtil.formatDateLabel(it.dateEpochDay) }
        )
    }
    single { com.drivemate.showcase.feature.expat.notifications.ExpatNotificationReadStore(androidContext()) }
    viewModel {
        com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsViewModel(
            observeAnnouncements = get(),
            authRepo = get(),
            store = get()
        )
    }
    viewModel {
        com.drivemate.showcase.feature.expat.queue.ExpatReviewQueueViewModel(
            getManagedDrivers = get(),
            getRange = get(),
            authRepo = get()
        )
    }
    viewModelOf(::DriverWorkdayDetailViewModel)
    single<com.drivemate.showcase.feature.settings.DriverSettingsPreferences> {
        com.drivemate.showcase.feature.settings.DataStoreDriverSettingsPreferences(androidContext())
    }
    viewModelOf(::DriverSettingsViewModel)
    // PR-E foto profil. Store = cache memori bersama Settings + Home (dikunci per uid).
    // Lambda `viewModel { }` EKSPLISIT: constructor-nya menerima fungsi/dispatcher, bukan
    // sekadar dependensi yang bisa di-resolve Koin.
    single { com.drivemate.showcase.feature.settings.photo.ProfilePhotoStore() }
    single { com.drivemate.showcase.feature.settings.photo.ProfilePhotoLocalStore(androidContext()) }
    single { com.drivemate.showcase.feature.settings.photo.ProfilePhotoProcessor(androidContext().contentResolver) }
    viewModel {
        val processor: com.drivemate.showcase.feature.settings.photo.ProfilePhotoProcessor = get()
        com.drivemate.showcase.feature.settings.photo.ProfilePhotoViewModel(
            authRepository = get(),
            getPhoto = get(),
            savePhoto = get(),
            deletePhoto = get(),
            store = get(),
            localStore = get(),
            processUri = processor::process,
            // Decode/encode = kerja CPU, bukan I/O murni: Default agar tidak berebut thread IO.
            workDispatcher = kotlinx.coroutines.Dispatchers.Default,
            nowMillis = System::currentTimeMillis,
        )
    }
    viewModelOf(::SessionViewModel)
    viewModel {
        ChatInboxViewModel(
            threadRepository = get(),
            directoryRepository = get(),
            authRepository = get(),
            unknownName = androidContext().getString(com.drivemate.showcase.R.string.chat_unknown_user)
        )
    }
    viewModel { com.drivemate.showcase.feature.chat.ChatUnreadViewModel(threadRepository = get()) }
    viewModel { (partnerUid: String, partnerName: String) ->
        ChatThreadViewModel(
            partnerUid = partnerUid,
            partnerName = partnerName,
            observeChatRoom = get(),
            sendMessage = get(),
            authRepository = get(),
            threadRepository = get(),
            directoryRepository = get()
        )
    }
    viewModelOf(::ExpatHomeViewModel)
    viewModelOf(::ExpatSettingsViewModel)
    viewModelOf(::AnnouncementsViewModel)
    viewModelOf(::ExpatDriverDetailViewModel)
    viewModelOf(::ExpatMonthlyReviewViewModel)
    viewModelOf(::ExpatWorkdayDetailViewModel)

    DMLog.i("DM/DI", "All Android ViewModels registered successfully ✅")
}
