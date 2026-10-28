package com.drivemate.showcase.core.util

private const val CHAT_THREAD_SEPARATOR = "__"

fun buildOneToOneChatThreadId(firstUid: String, secondUid: String): String {
    val left = firstUid.trim()
    val right = secondUid.trim()
    require(left.isNotBlank()) { "firstUid is blank" }
    require(right.isNotBlank()) { "secondUid is blank" }
    require(left != right) { "chat thread requires two distinct participants" }
    return listOf(left, right)
        .sorted()
        .joinToString(CHAT_THREAD_SEPARATOR)
}
