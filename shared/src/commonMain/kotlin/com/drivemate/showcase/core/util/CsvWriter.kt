package com.drivemate.showcase.core.util

import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.calendar.recordedOvertimeMinutes
import kotlinx.datetime.TimeZone

object CsvWriter {
    fun tableToCsv(
        headers: List<String>,
        rows: List<List<String>>
    ): String = buildString {
        appendLine(headers.joinToString(",") { escapeCsvCell(it) })
        rows.forEach { row ->
            val normalized = if (row.size >= headers.size) {
                row.take(headers.size)
            } else {
                row + List(headers.size - row.size) { "" }
            }
            appendLine(normalized.joinToString(",") { escapeCsvCell(it) })
        }
    }

    fun escapeCsvCell(value: String?): String {
        val safeValue = value.orEmpty()
        val needsQuotes = safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = safeValue.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun attendanceToCsv(
        rows: List<AttendanceEntry>,
        tzId: String = "Asia/Jakarta",
        nameResolver: (String) -> String = { maskUid(it) }
    ): String {
        val zone = TimeZone.of(tzId)
        val headers = listOf("Date", "Time", "Type", "DriverName", "DriverUidMasked", "LocationName", "Lat", "Lon")
        val csvRows = rows.map { row ->
            val day = TimeUtil.epochDayOf(row.timestampMillis, zone).toInt()
            listOf(
                TimeUtil.formatYmdFromEpochDay(day, zone),
                TimeUtil.formatHm(row.timestampMillis, zone),
                row.type,
                nameResolver(row.driverUid),
                maskUid(row.driverUid),
                row.locationName.orEmpty(),
                row.latitude?.toString().orEmpty(),
                row.longitude?.toString().orEmpty()
            )
        }
        return tableToCsv(headers, csvRows)
    }

    fun workDaysToAdminCsv(
        rows: List<WorkDay>,
        tzId: String = "Asia/Jakarta",
        nameResolver: (String) -> String = { maskUid(it) }
    ): String {
        val zone = TimeZone.of(tzId)
        val headers = listOf(
            "Date",
            "DriverName",
            "ClockIn",
            "ClockOut",
            "TotalMinutes",
            // Blok 4.4: nilai TERCATAT di dokumen, bukan lembur terbayar. Header yang
            // menyebut "Overtime" polos membuat file ini terbaca seolah sebanding dengan
            // kolom lembur di CSV Rekap Bulanan, padahal rumusnya beda (lihat
            // recordedOvertimeMinutes vs payableOvertimeMinutes).
            "RecordedOvertimeMinutes",
            "Status",
            "ApproverUidMasked",
            "ApprovedAt"
        )
        val csvRows = rows.map { w ->
            val status = w.status?.ifBlank { null } ?: when (w.approvedByExpat) {
                true -> "approved"
                false -> "rejected"
                null -> "pending"
            }
            val approver = w.approverUid ?: w.approvedByUid
            val approvedAt = w.approvedAtMillis?.let { ts ->
                val day = TimeUtil.epochDayOf(ts, zone).toInt()
                "${TimeUtil.formatYmdFromEpochDay(day, zone)} ${TimeUtil.formatHm(ts, zone)}"
            } ?: "-"
            listOf(
                TimeUtil.formatYmdFromEpochDay(w.dateEpochDay, zone),
                w.driverName?.takeIf { it.isNotBlank() } ?: nameResolver(w.driverUid),
                TimeUtil.formatHm(w.clockInMillis, zone),
                TimeUtil.formatHm(w.clockOutMillis, zone),
                w.totalMinutes.toString(),
                // Lewat fungsi bernama, bukan field mentah: itulah cara Blok 4.4 menjaga
                // agar tidak ada lagi layar/ekspor yang menulis rumus lemburnya sendiri.
                recordedOvertimeMinutes(w).toString(),
                status,
                if (approver.isNullOrBlank()) "-" else maskUid(approver),
                approvedAt
            )
        }
        return tableToCsv(headers, csvRows)
    }

    private fun maskUid(uid: String): String {
        if (uid.isBlank()) return "-"
        val suffix = if (uid.length >= 4) uid.takeLast(4) else uid
        return "User-$suffix"
    }
}
