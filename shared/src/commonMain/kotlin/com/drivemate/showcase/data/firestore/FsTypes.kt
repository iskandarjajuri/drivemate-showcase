// shared/commonMain/.../data/firestore/FsTypes.kt
package com.drivemate.showcase.data.firestore

/** Snapshot doc ringan agar KMM-friendly. */
data class FsDoc(
    val id: String,
    val data: Map<String, Any?>?
)

/** Pegangan untuk melepas listener realtime. */
fun interface ListenerRegistration {
    fun remove()
}