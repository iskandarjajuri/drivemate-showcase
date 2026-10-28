package com.drivemate.showcase.core.model

/** Terjemahan satu bahasa untuk sebuah pesan; ditulis server (Cloud Function), tidak pernah oleh klien. */
data class TranslationDto(
    val text: String,
    val uncertain: Boolean = false
)

data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderUid: String,
    val senderName: String?,
    val body: String,
    val createdAtMillis: Long,
    /** Bahasa asal hasil deteksi server: "ja" | "id" | "other"; null = belum/tidak diterjemahkan. */
    val srcLang: String? = null,
    /** "formal" | "polite" | "casual". */
    val register: String? = null,
    /** Kunci = kode bahasa tujuan ("id"/"ja"). */
    val translations: Map<String, TranslationDto>? = null,
    /** "done" | "failed" | "skipped"; null = belum diproses atau fitur mati. */
    val translationStatus: String? = null,
    /** Tombstone "Pesan dihapus": isi & terjemahan sudah dikosongkan server. */
    val deleted: Boolean = false
)
