package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Batas tunggu sumber data inbox sebelum dianggap gagal (spinner tidak boleh abadi). */
const val CHAT_INBOX_LOAD_TIMEOUT_MILLIS = 10_000L

/** Keadaan satu sumber data: sedang memuat, sukses (boleh kosong), atau gagal. */
sealed interface Source<out T> {
    data object Loading : Source<Nothing>
    data class Success<T>(val value: T) : Source<T>
    data class Failure(val error: Throwable) : Source<Nothing>
}

/** Sumber tidak mengirim apa pun dalam batas waktu. */
class SourceTimeoutException : Exception("source timed out")

/**
 * Mengubah flow hasil menjadi [Source]: selalu mulai dengan [Source.Loading]; bila tidak ada
 * emisi dalam [timeoutMillis] -> [Source.Failure] ([SourceTimeoutException]) dan tetap
 * mendengarkan (emisi berikutnya memulihkan jadi Success). Galat/penutupan flow dipetakan ke Failure.
 */
fun <T> Flow<AppResult<T>>.toSource(timeoutMillis: Long): Flow<Source<T>> = channelFlow {
    send(Source.Loading)
    val timer = launch {
        delay(timeoutMillis)
        send(Source.Failure(SourceTimeoutException()))
    }
    catch { emit(AppResult.failure(it)) }
        .collect { result ->
            timer.cancel()
            send(
                when (result) {
                    is AppResult.Success -> Source.Success(result.data)
                    is AppResult.Failure -> Source.Failure(result.throwable)
                }
            )
        }
    // Flow selesai tanpa emisi: biarkan timer berjalan (menjadi Failure) lalu tutup bersih.
    awaitClose { timer.cancel() }
}
