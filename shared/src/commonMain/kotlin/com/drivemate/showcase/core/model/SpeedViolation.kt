package com.drivemate.showcase.core.model

/**
 * Satu event pelanggaran batas kecepatan — append-only, satu dokumen per kejadian
 * di koleksi `speed_violations`.
 *
 * Sengaja BUKAN field di `locations/{uid}`: dokumen lokasi ditimpa terus-menerus
 * (`setDocument(merge = true)`), jadi trigger di sana tidak bisa membedakan
 * "pelanggaran baru" dari "pelanggaran sama, posisi diperbarui". Pola append-only
 * ini mengikuti `attendances`.
 */
data class SpeedViolation(
    val driverUid: String,
    val speedKph: Double,
    val speedLimitKph: Double,
    val occurredAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Susun dokumen `speed_violations` — dipakai KEDUA platform.
 *
 * `null` bila `driverUid` tidak sama dengan user yang login: `firestore.rules` hanya
 * mengizinkan `create` untuk milik sendiri, jadi menahannya di sini mencegah tulisan yang
 * pasti ditolak — dan mencegah bug di mana pelanggaran tercatat atas nama driver lain.
 *
 * Koordinat opsional dan hanya disertakan bila ada; menuliskan `null` menambah kunci tanpa
 * membawa informasi.
 */
fun buildSpeedViolationPayload(
    violation: SpeedViolation,
    authUid: String
): Map<String, Any>? {
    if (authUid.isBlank() || violation.driverUid != authUid) return null
    val fields = mutableMapOf<String, Any>(
        "driverUid" to authUid,
        "speedKph" to violation.speedKph,
        "speedLimitKph" to violation.speedLimitKph,
        "occurredAtMillis" to violation.occurredAtMillis
    )
    violation.latitude?.let { fields["latitude"] = it }
    violation.longitude?.let { fields["longitude"] = it }
    return fields
}
