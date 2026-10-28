package com.drivemate.showcase.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tujuan yang dibawa data push (`data{type,...}` dari function). */
sealed interface PushRoute {
    /** Buka thread chat dengan [senderUid] (lawan bicara pengirim pesan). */
    data class ChatThread(val threadId: String, val senderUid: String, val senderName: String) : PushRoute

    /** Pembaruan pekerjaan driver: [kind] = plan | announcement | decision | task; [refId] id dokumen terkait (boleh kosong). */
    data class Work(val kind: String, val refId: String) : PushRoute
}

/** Layar tujuan push pekerjaan. */
enum class WorkDestination { HOME, ANNOUNCEMENTS, HISTORY, TASKS }

fun PushRoute.Work.destination(): WorkDestination = when (kind) {
    "plan" -> WorkDestination.HOME // jadwal hari ini tampil di Beranda
    "announcement" -> WorkDestination.ANNOUNCEMENTS
    "decision" -> WorkDestination.HISTORY
    else -> WorkDestination.TASKS
}

private val WORK_KINDS = setOf("plan", "announcement", "decision", "task")

/** Kunci data push untuk jenis chat. */
const val PUSH_TYPE_CHAT = "chat"
const val PUSH_TYPE_WORK = "work"

// Extras Intent bisa dikirim aplikasi mana pun (MainActivity exported) — setiap nilai divalidasi bentuknya
// (tinjauan keamanan #5): `/` atau `..` pada id memecah rute navigasi (crash), nama bebas bisa menyamar jadi orang lain.
private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
private val THREAD_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,300}$")
private val REF_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,200}$")
private const val SENDER_NAME_MAX = 60

private fun sanitizeName(raw: String): String =
    // Karakter kontrol dan pengendali arah (bidi: U+200E/F, U+202A-E, U+2066-9) dibuang: dipakai memalsukan tampilan nama.
    raw.filterNot { it.isISOControl() || it.code in 0x200E..0x200F || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 }
        .trim().take(SENDER_NAME_MAX)

/**
 * Data push (extras Intent / `RemoteMessage.data`) -> [PushRoute]; null bila bukan push yang dikenal,
 * datanya tidak lengkap, atau id tidak berbentuk id Firebase. Nilai null/kosong dianggap hilang.
 */
fun parsePushRoute(data: Map<String, String?>): PushRoute? {
    fun v(key: String) = data[key]?.trim()?.takeIf { it.isNotEmpty() }
    return when (v("type")) {
        PUSH_TYPE_CHAT -> {
            val threadId = v("threadId")?.takeIf { THREAD_ID_PATTERN.matches(it) } ?: return null
            val senderUid = v("senderUid")?.takeIf { ID_PATTERN.matches(it) } ?: return null
            PushRoute.ChatThread(threadId, senderUid, v("senderName")?.let(::sanitizeName)?.takeIf { it.isNotEmpty() } ?: senderUid)
        }
        PUSH_TYPE_WORK -> {
            val kind = v("kind")?.takeIf { it in WORK_KINDS } ?: return null
            PushRoute.Work(kind, v("refId")?.takeIf { REF_ID_PATTERN.matches(it) }.orEmpty())
        }
        else -> null
    }
}

/**
 * Notifikasi chat di foreground disembunyikan HANYA bila thread yang sama sedang terbuka (badge
 * sudah cukup); thread lain tetap ditampilkan.
 */
fun shouldShowForegroundChat(openThreadId: String?, pushThreadId: String): Boolean =
    openThreadId != pushThreadId

/** Id notifikasi stabil per thread: pesan baru di thread sama menggantikan, thread berbeda tidak saling menimpa. */
fun chatNotificationId(threadId: String): Int = threadId.hashCode()

/** Id notifikasi pekerjaan stabil per (jenis, referensi): pembaruan sama menggantikan, yang lain tidak saling menimpa. */
fun workNotificationId(kind: String, refId: String): Int = "$kind:$refId".hashCode()

/**
 * Titipan rute dari ketukan notifikasi sampai sesi/navigasi siap. Satu rute tertunda; `null` dari
 * Intent biasa tidak menghapus rute yang sudah menunggu.
 */
class PushRouteBus {
    private val _pending = MutableStateFlow<PushRoute?>(null)
    val pending: StateFlow<PushRoute?> = _pending.asStateFlow()

    fun offer(route: PushRoute?) {
        if (route != null) _pending.value = route
    }

    fun consume() {
        _pending.value = null
    }
}

/** Satu-satunya bus dipakai MainActivity (menawarkan) dan root navigasi (mengonsumsi). */
object PushRoutes {
    val bus = PushRouteBus()
}

/** Thread chat yang sedang terbuka di foreground (diisi layar thread sesuai lifecycle RESUMED). */
object OpenChatThread {
    @Volatile
    var threadId: String? = null
}
