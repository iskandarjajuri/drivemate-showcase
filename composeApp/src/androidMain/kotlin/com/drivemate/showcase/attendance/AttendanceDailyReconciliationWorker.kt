package com.drivemate.showcase.attendance

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.attendance.ReconcileAttendanceDailyUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import org.koin.java.KoinJavaComponent.getKoin
import java.util.concurrent.TimeUnit

data class AttendanceDailyReconcileLastRun(
    val runAtMillis: Long,
    val source: String,
    val scannedDays: Int,
    val upserts: Int,
    val noOpDays: Int,
    val failedDays: Int,
    val outcome: String,
    val reason: String? = null,
    val errorType: String? = null
)

enum class ManualReconcileEnqueueResult {
    TRIGGERED,
    ALREADY_RUNNING,
    NOT_PERMITTED,
    FAILED
}

private object AttendanceDailyReconcileStore {
    private const val PREF_NAME = "attendance_daily_reconcile_prefs"
    private const val KEY_LAST_RUN_MILLIS = "last_run_millis"
    private const val KEY_SOURCE = "source"
    private const val KEY_SCANNED_DAYS = "scanned_days"
    private const val KEY_UPSERTS = "upserts"
    private const val KEY_NO_OP_DAYS = "no_op_days"
    private const val KEY_FAILED_DAYS = "failed_days"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_REASON = "reason"
    private const val KEY_ERROR_TYPE = "error_type"

    fun read(context: Context): AttendanceDailyReconcileLastRun? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val runAtMillis = prefs.getLong(KEY_LAST_RUN_MILLIS, 0L)
        if (runAtMillis <= 0L) return null
        return AttendanceDailyReconcileLastRun(
            runAtMillis = runAtMillis,
            source = prefs.getString(KEY_SOURCE, AttendanceDailyReconciliationScheduler.SOURCE_UNKNOWN)
                ?: AttendanceDailyReconciliationScheduler.SOURCE_UNKNOWN,
            scannedDays = prefs.getInt(KEY_SCANNED_DAYS, 0),
            upserts = prefs.getInt(KEY_UPSERTS, 0),
            noOpDays = prefs.getInt(KEY_NO_OP_DAYS, 0),
            failedDays = prefs.getInt(KEY_FAILED_DAYS, 0),
            outcome = prefs.getString(KEY_OUTCOME, "unknown") ?: "unknown",
            reason = prefs.getString(KEY_REASON, null),
            errorType = prefs.getString(KEY_ERROR_TYPE, null)
        )
    }

    fun write(context: Context, value: AttendanceDailyReconcileLastRun) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_RUN_MILLIS, value.runAtMillis)
            putString(KEY_SOURCE, value.source)
            putInt(KEY_SCANNED_DAYS, value.scannedDays)
            putInt(KEY_UPSERTS, value.upserts)
            putInt(KEY_NO_OP_DAYS, value.noOpDays)
            putInt(KEY_FAILED_DAYS, value.failedDays)
            putString(KEY_OUTCOME, value.outcome)
            putString(KEY_REASON, value.reason)
            putString(KEY_ERROR_TYPE, value.errorType)
        }
    }
}

class AttendanceDailyReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val TAG = "DM/AttendanceReconcileWorker"
        const val LOOKBACK_DAYS = 7L
        const val DEBOUNCE_WINDOW_MILLIS = 15L * 60L * 1000L
    }

    override suspend fun doWork(): Result {
        val source = inputData.getString(AttendanceDailyReconciliationScheduler.INPUT_SOURCE)
            ?: AttendanceDailyReconciliationScheduler.SOURCE_PERIODIC
        val force = inputData.getBoolean(AttendanceDailyReconciliationScheduler.INPUT_FORCE, false)
        val startedAt = TimeUtil.nowEpochMillis()
        DMLog.i(TAG, "run_start source=$source force=$force")

        return try {
            val koin = getKoin()
            val authRepository: AuthRepository = koin.get()
            val reconcileAttendanceDailyUseCase: ReconcileAttendanceDailyUseCase = koin.get()

            if (!force) {
                val lastRun = AttendanceDailyReconcileStore.read(applicationContext)
                if (lastRun != null && startedAt - lastRun.runAtMillis < DEBOUNCE_WINDOW_MILLIS) {
                    DMLog.i(TAG, "run_end source=$source outcome=skipped reason=debounced")
                    AttendanceDailyReconcileStore.write(
                        applicationContext,
                        AttendanceDailyReconcileLastRun(
                            runAtMillis = startedAt,
                            source = source,
                            scannedDays = 0,
                            upserts = 0,
                            noOpDays = 0,
                            failedDays = 0,
                            outcome = "skipped",
                            reason = "debounced",
                            errorType = null
                        )
                    )
                    return Result.success()
                }
            }

            val currentUser = when (val current = authRepository.currentUser()) {
                is AppResult.Failure -> {
                    DMLog.w(TAG, "Skip reconcile: currentUser() failed: ${current.throwable.message}")
                    null
                }
                is AppResult.Success -> current.data
            }
            if (currentUser == null) {
                DMLog.i(TAG, "run_end source=$source outcome=skipped reason=no_session")
                AttendanceDailyReconcileStore.write(
                    applicationContext,
                    AttendanceDailyReconcileLastRun(
                        runAtMillis = startedAt,
                        source = source,
                        scannedDays = 0,
                        upserts = 0,
                        noOpDays = 0,
                        failedDays = 0,
                        outcome = "skipped",
                        reason = "no_session",
                        errorType = null
                    )
                )
                return Result.success()
            }
            val rulesAligned = currentUser.role == Role.DRIVER
            DMLog.d(TAG, "rulesAligned=$rulesAligned role=${currentUser.role}")
            if (!rulesAligned) {
                DMLog.i(TAG, "run_end source=$source outcome=skipped reason=role_not_driver")
                AttendanceDailyReconcileStore.write(
                    applicationContext,
                    AttendanceDailyReconcileLastRun(
                        runAtMillis = startedAt,
                        source = source,
                        scannedDays = 0,
                        upserts = 0,
                        noOpDays = 0,
                        failedDays = 0,
                        outcome = "skipped",
                        reason = "role_not_driver",
                        errorType = null
                    )
                )
                return Result.success()
            }

            val zone = TimeZone.of("Asia/Jakarta")
            val endEpochDay = TimeUtil.todayEpochDay(zone)
            val startEpochDay = endEpochDay - LOOKBACK_DAYS
            when (
                val result = reconcileAttendanceDailyUseCase(
                    driverUid = currentUser.uid,
                    startEpochDay = startEpochDay,
                    endEpochDay = endEpochDay,
                    zone = zone
                )
            ) {
                is AppResult.Success -> {
                    val stats = result.data
                    AttendanceDailyReconcileStore.write(
                        applicationContext,
                        AttendanceDailyReconcileLastRun(
                            runAtMillis = startedAt,
                            source = source,
                            scannedDays = stats.scannedDays,
                            upserts = stats.repairedDays,
                            noOpDays = stats.noOpDays,
                            failedDays = stats.failedDays,
                            outcome = if (stats.failedDays > 0) "partial" else "success",
                            reason = null,
                            errorType = null
                        )
                    )
                    DMLog.i(
                        TAG,
                        "run_end source=$source uid=${currentUser.uid} outcome=${if (stats.failedDays > 0) "partial" else "success"} days=${stats.scannedDays} upserts=${stats.repairedDays} noOp=${stats.noOpDays} failed=${stats.failedDays}"
                    )
                }
                is AppResult.Failure -> {
                    AttendanceDailyReconcileStore.write(
                        applicationContext,
                        AttendanceDailyReconcileLastRun(
                            runAtMillis = startedAt,
                            source = source,
                            scannedDays = 0,
                            upserts = 0,
                            noOpDays = 0,
                            failedDays = 0,
                            outcome = "error",
                            reason = "reconcile_failed",
                            errorType = result.throwable::class.simpleName
                        )
                    )
                    DMLog.e(
                        TAG,
                        "run_end source=$source outcome=error reason=reconcile_failed errorType=${result.throwable::class.simpleName}",
                        result.throwable
                    )
                }
            }
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AttendanceDailyReconcileStore.write(
                applicationContext,
                AttendanceDailyReconcileLastRun(
                    runAtMillis = startedAt,
                    source = source,
                    scannedDays = 0,
                    upserts = 0,
                    noOpDays = 0,
                    failedDays = 0,
                    outcome = "error",
                    reason = "unexpected_exception",
                    errorType = t::class.simpleName
                )
            )
            DMLog.e(TAG, "run_end source=$source outcome=error reason=unexpected_exception errorType=${t::class.simpleName}", t)
            Result.success()
        }
    }
}

object AttendanceDailyReconciliationScheduler {
    const val PERIODIC_UNIQUE_WORK_NAME = "attendance_daily_reconcile_periodic"
    const val MANUAL_UNIQUE_WORK_NAME = "attendance_daily_reconcile_manual"
    private const val LEGACY_PERIODIC_UNIQUE_WORK_NAME = "attendance_daily_reconciliation"

    const val INPUT_SOURCE = "source"
    const val INPUT_FORCE = "force"
    const val SOURCE_PERIODIC = "periodic"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_UNKNOWN = "unknown"

    private const val TAG = "DM/AttendanceReconcileScheduler"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_UNIQUE_WORK_NAME)
        val request = PeriodicWorkRequestBuilder<AttendanceDailyReconciliationWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInputData(
                workDataOf(
                    INPUT_SOURCE to SOURCE_PERIODIC,
                    INPUT_FORCE to false
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        DMLog.i(TAG, "periodic_schedule uniqueName=$PERIODIC_UNIQUE_WORK_NAME policy=KEEP")
    }

    suspend fun enqueueManual(context: Context): ManualReconcileEnqueueResult {
        return try {
            val authRepository = getKoin().get<AuthRepository>()
            val currentUser = when (val result = authRepository.currentUser()) {
                is AppResult.Failure -> null
                is AppResult.Success -> result.data
            }
            if (currentUser == null || currentUser.role != Role.ADMIN) {
                return ManualReconcileEnqueueResult.NOT_PERMITTED
            }

            val workManager = WorkManager.getInstance(context)
            val alreadyRunning = workManager
                .getWorkInfosForUniqueWorkFlow(MANUAL_UNIQUE_WORK_NAME)
                .first()
                .any { info -> !info.state.isFinished }
            if (alreadyRunning) {
                return ManualReconcileEnqueueResult.ALREADY_RUNNING
            }

            val request = OneTimeWorkRequestBuilder<AttendanceDailyReconciliationWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(INPUT_SOURCE, SOURCE_MANUAL)
                        .putBoolean(INPUT_FORCE, true)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                MANUAL_UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            DMLog.i(TAG, "manual_enqueue uniqueName=$MANUAL_UNIQUE_WORK_NAME policy=KEEP")
            ManualReconcileEnqueueResult.TRIGGERED
        } catch (t: Throwable) {
            DMLog.e(TAG, "manual_enqueue_failed: ${t.message}", t)
            ManualReconcileEnqueueResult.FAILED
        }
    }

    fun getLastRunSummary(context: Context): AttendanceDailyReconcileLastRun? =
        AttendanceDailyReconcileStore.read(context)
}
