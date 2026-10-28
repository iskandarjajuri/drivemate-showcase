package com.drivemate.showcase.util

/**
 * Privacy contract: never show UID-derived identifiers in UI.
 * Fallback order: name -> email prefix -> generic localized placeholder.
 */
object DriverLabelDefaults {
    @Volatile
    private var driverNameFallback: String = ""

    fun initialize(driverNameFallback: String) {
        this.driverNameFallback = driverNameFallback
    }

    fun driverNameFallback(): String = driverNameFallback
}

fun safeDriverLabel(
    name: String?,
    email: String? = null,
    placeholder: String = DriverLabelDefaults.driverNameFallback()
): String {
    val cleanedName = name?.trim().takeIf { !it.isNullOrBlank() }?.takeIf { !it.startsWith("User-") }
    // Title Case hanya saat tampil; nilai tersimpan tidak diubah.
    if (!cleanedName.isNullOrBlank()) return displayName(cleanedName)
    val prefix = email?.substringBefore("@")?.trim().takeIf { !it.isNullOrBlank() }
    return prefix?.let(::displayName) ?: placeholder
}


/**
 * Title Case hanya saat tampil: kata huruf kecil semua dikapitalkan, kata KAPITAL panjang
 * (> 3 huruf) diturunkan ke Title Case, kata campuran dan singkatan pendek dibiarkan.
 */
fun displayName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        val letters = word.filter { it.isLetter() }
        when {
            letters.isEmpty() -> word
            letters.all { it.isLowerCase() } -> word.replaceFirstChar { it.uppercase() }
            letters.length > 3 && letters.all { it.isUpperCase() } ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            else -> word
        }
    }
}
