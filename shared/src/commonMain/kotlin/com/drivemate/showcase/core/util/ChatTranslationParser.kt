package com.drivemate.showcase.core.util

import com.drivemate.showcase.core.model.TranslationDto

/** Field terjemahan hasil baca dokumen pesan; semuanya opsional. */
data class ParsedChatTranslation(
    val srcLang: String?,
    val register: String?,
    val translations: Map<String, TranslationDto>?,
    val translationStatus: String?
)

/**
 * Membaca field terjemahan dari data dokumen pesan secara toleran: tipe yang salah atau
 * field hilang menghasilkan null, tidak pernah melempar. Hanya bahasa "id"/"ja" yang diterima.
 */
fun parseChatTranslation(data: Map<String, Any?>): ParsedChatTranslation {
    val raw = data["translations"] as? Map<*, *>
    val translations = raw?.mapNotNull { (key, value) ->
        val lang = (key as? String)?.takeIf { it == "id" || it == "ja" } ?: return@mapNotNull null
        val entry = value as? Map<*, *> ?: return@mapNotNull null
        val text = (entry["text"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        lang to TranslationDto(text = text, uncertain = entry["uncertain"] as? Boolean ?: false)
    }?.toMap()?.takeIf { it.isNotEmpty() }
    return ParsedChatTranslation(
        srcLang = (data["srcLang"] as? String)?.takeIf { it == "ja" || it == "id" || it == "other" },
        register = (data["register"] as? String)?.takeIf { it == "formal" || it == "polite" || it == "casual" },
        translations = translations,
        translationStatus = (data["translationStatus"] as? String)?.takeIf { it == "done" || it == "failed" || it == "skipped" }
    )
}
