package com.drivemate.showcase.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShareUtils {
    sealed interface CsvShareResult {
        data class Success(val fileName: String) : CsvShareResult
        data class Failure(val throwable: Throwable) : CsvShareResult
    }

    private val jakartaZoneId: ZoneId = ZoneId.of("Asia/Jakarta")
    private val fileTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    suspend fun shareCsv(
        context: Context,
        fileName: String,
        content: String,
        chooserTitle: String
    ): CsvShareResult {
        return runCatching {
            val safeName = sanitizeFileName(fileName)
            val file = withContext(Dispatchers.IO) {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                val exportDir = File(baseDir, "exports")
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    error("Unable to create export directory")
                }
                File(exportDir, safeName).also { exportFile ->
                    exportFile.writeText(content)
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            CsvShareResult.Success(file.name)
        }.getOrElse { throwable ->
            CsvShareResult.Failure(throwable)
        }
    }

    fun buildSuggestedCsvFileName(
        domainLabel: String,
        driverName: String,
        periodLabel: String,
        timestampMillis: Long
    ): String {
        val timestamp = Instant.ofEpochMilli(timestampMillis)
            .atZone(jakartaZoneId)
            .format(fileTimestampFormatter)
        val raw = "$domainLabel-$driverName-$periodLabel-$timestamp.csv"
        return sanitizeFileName(raw)
    }

    fun sanitizeFileName(fileName: String): String {
        val base = fileName.replace(Regex("[\\\\/:]"), "-")
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "export.csv" }
    }
}
