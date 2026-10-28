package com.drivemate.showcase.core.config

/**
 * Dev-only Firebase emulator redirect (Firestore + Auth), opted into per-machine via a gitignored
 * local flag (never committed). [enabled] can only ever be true in a debug
 * build — the release build type hardcodes it false regardless of the local
 * flag, on both platforms. Production is unaffected unless a developer
 * explicitly turns this on in their own debug build.
 */
data class FirestoreEmulatorConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    /** Port of the Firebase Auth emulator on the same [host]. */
    val authPort: Int = 0
) {
    companion object {
        val Disabled = FirestoreEmulatorConfig(enabled = false, host = "", port = 0, authPort = 0)
    }
}
