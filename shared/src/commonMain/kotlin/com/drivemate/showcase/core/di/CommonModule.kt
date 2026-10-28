// core/di/CommonModule.kt
package com.drivemate.showcase.core.di

import com.drivemate.showcase.domain.repository.AdminDataRepository
import com.drivemate.showcase.domain.repository.*
import com.drivemate.showcase.domain.usecase.assignment.GetDailyOverrideUseCase
import com.drivemate.showcase.domain.usecase.assignment.GetOverridesForDateUseCase
import com.drivemate.showcase.domain.usecase.assignment.GetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.ResolveExpatForDriverUseCase
import com.drivemate.showcase.domain.usecase.assignment.SetPermanentManagerUseCase
import com.drivemate.showcase.domain.usecase.assignment.UpsertDailyOverrideUseCase
import com.drivemate.showcase.domain.usecase.attendance.*
import com.drivemate.showcase.domain.usecase.admin.GetAdminAttendanceDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistsUseCase
import com.drivemate.showcase.domain.usecase.admin.RecordAdminWorkdayDecisionUseCase
import com.drivemate.showcase.domain.usecase.admin.UpdateChecklistFollowUpUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdaysUseCase
import com.drivemate.showcase.domain.usecase.admin.GetDriverSopStatusesUseCase
import com.drivemate.showcase.domain.usecase.auth.*
import com.drivemate.showcase.domain.usecase.broadcast.DeleteAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.ObserveAnnouncementsUseCase
import com.drivemate.showcase.domain.usecase.broadcast.SendAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.broadcast.UpdateAnnouncementUseCase
import com.drivemate.showcase.domain.usecase.chat.ObserveChatRoomUseCase
import com.drivemate.showcase.domain.usecase.chat.SendMessageUseCase
import com.drivemate.showcase.domain.usecase.location.*
import com.drivemate.showcase.domain.usecase.mobility.GenerateMobilityInsightsUseCase
import com.drivemate.showcase.domain.usecase.report.*
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safety.GetSpeedLimitConfigUseCase
import com.drivemate.showcase.domain.usecase.safety.GetSpeedViolationsUseCase
import com.drivemate.showcase.domain.usecase.safety.ReportSpeedViolationUseCase
import com.drivemate.showcase.domain.usecase.safety.UpsertSpeedLimitConfigUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitSafetyDeclarationUseCase
import com.drivemate.showcase.domain.usecase.tracking.ObserveTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.UpdateTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import com.drivemate.showcase.domain.usecase.user.GetChatContactsUseCase
import com.drivemate.showcase.domain.usecase.user.GetAllDriverDisplayNamesUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.domain.usecase.user.AcceptDriverSopUseCase
import com.drivemate.showcase.domain.usecase.workload.CalculateDriverWorkloadUseCase
import com.drivemate.showcase.domain.usecase.workday.GenerateWorkdayFromAttendancesUseCase
import com.drivemate.showcase.domain.usecase.workday.GetDriverTodayScheduleUseCase
import com.drivemate.showcase.domain.usecase.workday.UpsertWorkdayPlanUseCase
import org.koin.dsl.module

val commonModule = module {
    // AUTH
    single { LoginUseCase(get()) }
    single { LogoutUseCase(get()) }
    single { ObserveAuthStateUseCase(get()) }          // legacy (masih boleh ada)
    single { ObserveUserSessionUseCase(get()) }        // ✅ baru: UID-first session

    // ATTENDANCE
    single { GetTodayAttendanceUseCase(get<AttendanceRepository>()) }
    single { TapInUseCase(get<AttendanceRepository>()) }
    single { TapOutUseCase(get<AttendanceRepository>(), get<ChecklistRepository>(), get<SafetyRepository>()) }
    single { ReconcileAttendanceDailyUseCase(get<AttendanceRepository>(), get<WorkCalendarRepository>()) }
    single { GetAttendanceByRangeUseCase(get()) }
    single { GetDriverAttendanceHistoryUseCase(get<AttendanceRepository>()) }

    // LOCATION
    single { UpdateDriverLocationUseCase(get<LocationRepository>()) }
    single { ClearDriverLocationUseCase(get<LocationRepository>()) }
    single { ObserveAllDriversLocationUseCase(get<LocationRepository>()) }

    // TRACKING
    single { ObserveTrackingStatusUseCase(get()) }
    single { UpdateTrackingStatusUseCase(get()) }

    // MOBILITY INSIGHT
    single { GenerateMobilityInsightsUseCase() }
    single { CalculateDriverWorkloadUseCase() }

    // REPORT
    single { GetWorkDaysByMonthUseCase(get<WorkDayRepository>()) }
    single { GetWorkDaysByRangeUseCase(get<WorkDayRepository>()) }
    single { GetWorkdaySummaryUseCase(get<WorkDayRepository>()) }
    single { BulkApproveWorkDaysUseCase(get<WorkDayRepository>()) }
    single { BulkRejectWorkDaysUseCase(get<WorkDayRepository>()) }
    single { ApproveWorkDayUseCase(get<WorkDayRepository>()) }
    single { RejectWorkDayUseCase(get<WorkDayRepository>()) }
    single { AdminOverrideWorkdayDecisionUseCase(get<WorkDayRepository>()) }
    single { GetWorkdayDetailUseCase(get<WorkDayRepository>()) }
    single { ExportReportCsvUseCase() }
    single {
        GetMonthlyDriverRecapUseCase(
            adminRepo = get(),
            calendarRepo = get(),
            directoryRepo = get(),
        )
    }
    single { GenerateWorkdayFromAttendancesUseCase(get<WorkDayRepository>()) }
    single { UpsertWorkdayPlanUseCase(get<WorkDayRepository>()) }
    single { GetDriverTodayScheduleUseCase(get<WorkDayRepository>()) }

    // SAFETY/CHECKLIST
    single { SubmitChecklistUseCase(get<ChecklistRepository>()) }
    single { SubmitSafetyDeclarationUseCase(get<SafetyRepository>()) }
    single { GetChecklistStatusUseCase(get<ChecklistRepository>()) }
    single { GetSafetyDeclarationStatusUseCase(get<SafetyRepository>()) }
    // R3 — deteksi batas kecepatan
    single { GetSpeedLimitConfigUseCase(get<SafetyConfigRepository>()) }
    single { UpsertSpeedLimitConfigUseCase(get<SafetyConfigRepository>()) }
    single { ReportSpeedViolationUseCase(get<SpeedViolationRepository>()) }
    single { GetSpeedViolationsUseCase(get<SpeedViolationRepository>()) }

    // ADMIN DATA
    single { GetAdminChecklistsUseCase(get()) }
    single { UpdateChecklistFollowUpUseCase(get<AdminDataRepository>()) }
    single { RecordAdminWorkdayDecisionUseCase(get<AdminDataRepository>()) }
    single { GetAdminSafetyUseCase(get()) }
    single { GetAdminLocationsUseCase(get()) }
    single { GetAdminWorkdaysUseCase(get()) }
    single { GetDriverSopStatusesUseCase(get()) }
    single { GetAdminAttendanceDetailUseCase(get()) }
    single { GetAdminChecklistDetailUseCase(get()) }
    single { GetAdminSafetyDetailUseCase(get()) }
    single { GetAdminWorkdayDetailUseCase(get()) }
    single { GetAdminLocationDetailUseCase(get()) }

    // ANNOUNCEMENTS
    single { ObserveAnnouncementsUseCase(get()) }
    single { SendAnnouncementUseCase(get()) }
    single { UpdateAnnouncementUseCase(get()) }
    single { DeleteAnnouncementUseCase(get()) }

    // CHAT
    single { ObserveChatRoomUseCase(get()) }
    single { SendMessageUseCase(get()) }

    // USER DIRECTORY
    single { GetUserDisplayNameUseCase(get()) }
    single { GetAllDriverDisplayNamesUseCase(get()) }
    single { GetDriverSopStatusUseCase(get()) }
    single { GetManagedDriversUseCase(get()) }
    single { GetChatContactsUseCase(get()) }
    single { AcceptDriverSopUseCase(get()) }

    // DRIVER-EXPAT ASSIGNMENT
    single { SetPermanentManagerUseCase(get<DriverAssignmentRepository>()) }
    single { UpsertDailyOverrideUseCase(get<DriverAssignmentRepository>()) }
    single { GetDailyOverrideUseCase(get<DriverAssignmentRepository>()) }
    single { ResolveExpatForDriverUseCase(get<DriverAssignmentRepository>()) }
    single { GetPermanentManagerUseCase(get<DriverAssignmentRepository>()) }
    single { GetOverridesForDateUseCase(get<DriverAssignmentRepository>()) }
}
