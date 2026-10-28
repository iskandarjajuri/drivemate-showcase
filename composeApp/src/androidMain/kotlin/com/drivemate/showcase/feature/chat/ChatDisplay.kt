package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatMessage

/** Status terjemahan yang perlu ditampilkan di bubble. */
enum class TranslationUiState { None, Pending, Failed }

/**
 * Keputusan teks yang tampil untuk satu bubble (fungsi murni, diuji di `ChatDisplayTest`).
 *
 * @property primary teks utama bubble.
 * @property original teks asli untuk ditampilkan saat chip diketuk; null bila tidak ada terjemahan.
 * @property fromLang bahasa asal (untuk chip "Dari ..."); null bila tidak ada terjemahan.
 * @property register register asli; null bila tidak diketahui.
 * @property uncertain terjemahan ditandai "mungkin kurang tepat".
 * @property sentLang pesan sendiri: bahasa seberang tempat pesan diterjemahkan untuk lawan bicara.
 */
data class DisplayText(
    val primary: String,
    val original: String? = null,
    val fromLang: String? = null,
    val register: String? = null,
    val uncertain: Boolean = false,
    val sentLang: String? = null,
    val state: TranslationUiState = TranslationUiState.None
) {
    val translated: Boolean get() = original != null
}

/** Umur (ms) pesan masuk tanpa status yang masih dianggap "sedang diterjemahkan" (latensi normal 1-2 dtk). */
const val TRANSLATION_PENDING_WINDOW_MILLIS = 20_000L

/**
 * @param viewerLang bahasa baca = bahasa aplikasi ("id", "ja"; lainnya -> tampilkan asli).
 * @param featureActive true bila ada bukti terjemahan aktif di thread (ada pesan ber-translationStatus).
 */
fun chooseDisplayText(
    viewerLang: String,
    isMine: Boolean,
    message: ChatMessage,
    nowMillis: Long,
    featureActive: Boolean
): DisplayText {
    val original = DisplayText(primary = message.body)
    if (viewerLang != "id" && viewerLang != "ja") return original

    if (isMine) {
        val sent = message.translations
            ?.takeIf { message.translationStatus == "done" }
            ?.keys?.firstOrNull { it != viewerLang && (it == "id" || it == "ja") }
        return original.copy(sentLang = sent)
    }

    val src = message.srcLang
    val translated = message.translations?.get(viewerLang)
    if (message.translationStatus == "done" && src != null && src != "other" && src != viewerLang && translated != null) {
        return DisplayText(
            primary = translated.text,
            original = message.body,
            fromLang = src,
            register = message.register,
            uncertain = translated.uncertain
        )
    }
    return when {
        message.translationStatus == "failed" -> original.copy(state = TranslationUiState.Failed)
        message.translationStatus == null && featureActive &&
            nowMillis - message.createdAtMillis in 0 until TRANSLATION_PENDING_WINDOW_MILLIS ->
            original.copy(state = TranslationUiState.Pending)
        else -> original
    }
}

/**
 * Bahasa target terjemahan untuk petunjuk composer: bahasa efektif LAWAN BICARA, hanya bila
 * keduanya diketahui dan berbeda (sama bahasa -> tidak ada terjemahan -> tanpa petunjuk).
 */
fun translationTargetHint(viewerLang: String?, partnerLang: String?): String? =
    partnerLang?.takeIf { viewerLang != null && it != viewerLang && (it == "id" || it == "ja") }

/** Bahasa ditulis pengguna = bahasa aplikasi bila "id"/"ja"; selain itu null (baris info disembunyikan). */
fun composerLanguage(viewerLang: String): String? = viewerLang.takeIf { it == "id" || it == "ja" }

/** Penghitung karakter tampil mulai [COUNTER_THRESHOLD]. */
const val CHAT_MAX_CHARS = 1000
const val CHAT_COUNTER_THRESHOLD = 900
fun showCharCounter(length: Int): Boolean = length >= CHAT_COUNTER_THRESHOLD
