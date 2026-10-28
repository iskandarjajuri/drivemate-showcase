package com.drivemate.showcase.util

import androidx.annotation.StringRes

data class UiText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList()
)
