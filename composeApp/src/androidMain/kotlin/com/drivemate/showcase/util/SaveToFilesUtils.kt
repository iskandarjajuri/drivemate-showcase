package com.drivemate.showcase.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SaveToFilesUtils {
    sealed interface SaveCsvResult {
        data object Success : SaveCsvResult
        data class Failure(val throwable: Throwable) : SaveCsvResult
    }

    suspend fun writeCsv(
        context: Context,
        uri: Uri,
        content: String
    ): SaveCsvResult {
        return runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray())
                    stream.flush()
                } ?: error("Unable to open output stream")
            }
            SaveCsvResult.Success
        }.getOrElse { throwable ->
            SaveCsvResult.Failure(throwable)
        }
    }
}
