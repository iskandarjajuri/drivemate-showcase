package com.drivemate.showcase.feature.chat

import com.drivemate.showcase.core.model.ChatMessage
import java.time.Instant
import java.time.ZoneId

/** Posisi bubble dalam satu kelompok pesan berurutan dari pengirim yang sama. */
enum class BubblePosition { Single, First, Middle, Last }

/** Penanda hari pada pemisah tanggal. */
enum class ChatDayKind { Today, Yesterday, Date }

sealed interface ChatListItem {
    val key: String

    data class DateSeparator(val epochDay: Long, val kind: ChatDayKind) : ChatListItem {
        override val key get() = "day-$epochDay"
    }

    /** [showTime] = true hanya pada bubble terakhir sebuah kelompok (waktu kecil HH:mm per grup). */
    data class Bubble(
        val message: ChatMessage,
        val isMine: Boolean,
        val position: BubblePosition,
        val showTime: Boolean
    ) : ChatListItem {
        override val key get() = "msg-${message.id}"
    }
}

/** Jeda maksimum antar pesan agar tetap satu kelompok. */
const val CHAT_GROUP_GAP_MILLIS = 5 * 60 * 1000L

fun chatEpochDay(millis: Long, zoneId: String): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.of(zoneId)).toLocalDate().toEpochDay()

fun chatDayKind(epochDay: Long, todayEpochDay: Long): ChatDayKind = when (epochDay) {
    todayEpochDay -> ChatDayKind.Today
    todayEpochDay - 1 -> ChatDayKind.Yesterday
    else -> ChatDayKind.Date
}

/**
 * Mengubah daftar pesan (urut naik waktu) menjadi item tampilan: pemisah tanggal per hari dan
 * bubble berkelompok (pengirim sama, hari sama, jeda ≤ [CHAT_GROUP_GAP_MILLIS]).
 */
fun buildChatListItems(
    messages: List<ChatMessage>,
    currentUserUid: String?,
    zoneId: String,
    nowMillis: Long
): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()
    val today = chatEpochDay(nowMillis, zoneId)
    val days = messages.map { chatEpochDay(it.createdAtMillis, zoneId) }

    fun sameGroup(a: Int, b: Int): Boolean =
        messages[a].senderUid == messages[b].senderUid &&
            days[a] == days[b] &&
            messages[b].createdAtMillis - messages[a].createdAtMillis <= CHAT_GROUP_GAP_MILLIS

    val out = mutableListOf<ChatListItem>()
    messages.forEachIndexed { i, message ->
        if (i == 0 || days[i] != days[i - 1]) {
            out += ChatListItem.DateSeparator(days[i], chatDayKind(days[i], today))
        }
        val joinsPrev = i > 0 && sameGroup(i - 1, i)
        val joinsNext = i < messages.lastIndex && sameGroup(i, i + 1)
        val position = when {
            joinsPrev && joinsNext -> BubblePosition.Middle
            joinsPrev -> BubblePosition.Last
            joinsNext -> BubblePosition.First
            else -> BubblePosition.Single
        }
        out += ChatListItem.Bubble(
            message = message,
            isMine = message.senderUid == currentUserUid,
            position = position,
            showTime = !joinsNext
        )
    }
    return out
}
