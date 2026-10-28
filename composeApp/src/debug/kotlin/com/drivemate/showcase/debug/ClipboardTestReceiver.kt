package com.drivemate.showcase.debug

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log

/**
 * HANYA build debug. Menerima `SET_CLIPBOARD` dengan extra `b64` (teks UTF-8 yang di-base64) lalu menaruhnya di clipboard,
 * supaya flow Maestro dapat `pasteText` teks non-ASCII (Maestro tak bisa mengetik Unicode). Isi teks tidak pernah dilog.
 *
 *   adb shell am broadcast -n <pkg>/.debug.ClipboardTestReceiver \
 *     -a com.drivemate.showcase.debug.SET_CLIPBOARD --es b64 <base64>
 */
class ClipboardTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_CLIPBOARD) return
        val b64 = intent.getStringExtra(EXTRA_B64)
        val text = try {
            b64?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
        } catch (_: IllegalArgumentException) {
            null
        }
        if (text.isNullOrEmpty()) {
            Log.w(TAG, "SET_CLIPBOARD ditolak: b64 kosong atau tidak valid")
            resultCode = RESULT_REJECTED
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("drivemate-test", text))
        Log.i(TAG, "clipboard diisi (${text.length} karakter)")
        resultCode = RESULT_OK_FILLED
    }

    companion object {
        const val ACTION_SET_CLIPBOARD = "com.drivemate.showcase.debug.SET_CLIPBOARD"
        const val EXTRA_B64 = "b64"
        private const val TAG = "DM/DebugClipboard"
        private const val RESULT_OK_FILLED = 1
        private const val RESULT_REJECTED = 2
    }
}
