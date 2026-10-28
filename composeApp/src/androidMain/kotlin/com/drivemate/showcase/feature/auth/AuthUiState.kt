package com.drivemate.showcase.feature.auth

import androidx.annotation.StringRes
import com.drivemate.showcase.core.model.User

enum class AuthErrorReason {
    EmailEmpty,
    EmailInvalid,
    PasswordEmpty,
    InvalidCredentials,
    Offline,
    NotRegistered,
    InvalidRole,
    Unknown
}

data class AuthErrorState(
    val reason: AuthErrorReason,
    @param:StringRes val messageRes: Int,
    @param:StringRes val emailErrorRes: Int? = null,
    @param:StringRes val passwordErrorRes: Int? = null
)

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val error: AuthErrorState) : AuthUiState
    data class Success(val user: User) : AuthUiState
}
