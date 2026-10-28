package com.drivemate.showcase.core.util

import java.security.MessageDigest

/** SHA-256 heksadesimal huruf kecil (64 karakter) dari teks UTF-8; sama dengan `hashing.sha256(token).toHexString().lower()` di rules. */
fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
