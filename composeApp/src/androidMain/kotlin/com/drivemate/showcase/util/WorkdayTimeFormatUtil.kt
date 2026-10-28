package com.drivemate.showcase.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.TimeUtil
import kotlinx.datetime.TimeZone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WorkdayCorrectionUiInfo(
    val hasCorrection: Boolean,
    val correctedByRole: String?,
    val correctedAtMillis: Long?,
    val beforeClockInMillis: Long?,
    val beforeClockOutMillis: Long?,
    val effectiveClockInMillis: Long?,
    val effectiveClockOutMillis: Long?
)

object WorkdayTimeFormatUtil {
    private val jakartaZone: TimeZone = TimeZone.of("Asia/Jakarta")

    fun effectiveClockOrNull(clockMillis: Long?): Long? = clockMillis?.takeIf { it > 0L }

    fun formatClockOrDash(
        clockMillis: Long?,
        dash: String,
        zone: TimeZone = jakartaZone
    ): String = effectiveClockOrNull(clockMillis)?.let { TimeUtil.formatHm(it, zone) } ?: dash

    fun formatClockOrNull(
        clockMillis: Long?,
        zone: TimeZone = jakartaZone
    ): String? = effectiveClockOrNull(clockMillis)?.let { TimeUtil.formatHm(it, zone) }

    @Composable
    fun formatDurationHoursMinutesOrDash(
        totalMinutes: Int?,
        dash: String
    ): String {
        val safeMinutes = totalMinutes?.takeIf { it >= 0 } ?: return dash
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return stringResource(R.string.driver_duration_hours_minutes, hours, minutes)
    }

    /**
     * "7 Sep 2026 · 09:15" pada zona laporan, atau `null` kalau timestamp-nya kosong.
     *
     * Membawa TANGGAL, berbeda dari [formatClockOrNull] yang dipakai baris tap-in/tap-out.
     * Dipakai jejak keputusan (Blok 4.2), yang langkahnya bisa terjadi berhari-hari setelah
     * hari kerjanya — jam tanpa tanggal di sana membaca seolah keputusannya diambil pada
     * hari kerja itu.
     *
     * Zonanya sama dengan seluruh formatter di objek ini, dan sengaja tidak bisa diganti
     * pemanggil: dua baris berdampingan dengan zona berbeda adalah bentuk salah baca yang
     * paling sulit dilihat.
     */
    fun formatDateTimeOrNull(clockMillis: Long?): String? {
        val millis = effectiveClockOrNull(clockMillis) ?: return null
        val date = formatDateLabel(TimeUtil.epochDayFromMillis(millis, jakartaZone).toInt())
        val clock = TimeUtil.formatHm(millis, jakartaZone)
        return "$date · $clock"
    }

    fun formatDateLabel(
        epochDay: Int,
        locale: Locale = Locale.getDefault(),
        skeleton: String = "yMMMd"
    ): String {
        val date = LocalDate.ofEpochDay(epochDay.toLong())
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun correctionUiInfo(workDay: WorkDay): WorkdayCorrectionUiInfo {
        val effectiveIn = effectiveClockOrNull(workDay.clockInMillis)
        val effectiveOut = effectiveClockOrNull(workDay.clockOutMillis)
        val originalIn = effectiveClockOrNull(workDay.originalClockInMillis)
        val originalOut = effectiveClockOrNull(workDay.originalClockOutMillis)

        val explicitRole = normalizeCorrectionRole(workDay.correctedByRole)
            ?: normalizeCorrectionRole(workDay.clockCorrectionMeta?.correctedByRole)
        val explicitCorrectedAt = (workDay.correctedAtMillis ?: workDay.clockCorrectionMeta?.correctedAtMillis)
            ?.takeIf { it > 0L }
        val hasExplicitCorrection = explicitRole != null
            || explicitCorrectedAt != null
            || workDay.clockCorrectionMeta != null
            || workDay.expatCorrectionHistoryCount > 0

        val hasLegacyMismatch = (originalIn != null && effectiveIn != null && originalIn != effectiveIn) ||
            (originalOut != null && effectiveOut != null && originalOut != effectiveOut)
        val hasCorrection = hasExplicitCorrection || hasLegacyMismatch

        val derivedRole = explicitRole
            ?: if (workDay.expatCorrectionHistoryCount > 0) {
                "EXPAT"
            } else if (!workDay.adjustedBy.isNullOrBlank()) {
                "ADMIN"
            } else {
                null
            }
        val derivedCorrectedAt = explicitCorrectedAt ?: workDay.adjustedAt?.takeIf { it > 0L }

        return WorkdayCorrectionUiInfo(
            hasCorrection = hasCorrection,
            correctedByRole = if (hasCorrection) derivedRole else null,
            correctedAtMillis = if (hasCorrection) derivedCorrectedAt else null,
            beforeClockInMillis = if (hasCorrection) originalIn else null,
            beforeClockOutMillis = if (hasCorrection) originalOut else null,
            effectiveClockInMillis = effectiveIn,
            effectiveClockOutMillis = effectiveOut
        )
    }

    private fun normalizeCorrectionRole(raw: String?): String? {
        return when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "ADMIN", "EXPAT", "SYSTEM" -> raw.trim().uppercase(Locale.ROOT)
            else -> null
        }
    }
}
