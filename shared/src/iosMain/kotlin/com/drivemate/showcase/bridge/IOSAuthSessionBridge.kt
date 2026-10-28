package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.repository.IOSFirebaseAuthRepository
import com.drivemate.showcase.domain.usecase.auth.LoginUseCase
import com.drivemate.showcase.domain.usecase.auth.LogoutUseCase
import com.drivemate.showcase.domain.usecase.auth.ObserveUserSessionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class IOSAuthSessionBridge(
    private val scope: CoroutineScope,
    private val authRepository: IOSFirebaseAuthRepository,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val observeUserSession: ObserveUserSessionUseCase,
    private val onSessionChanged: (UserSession?) -> Unit
) {
    private var sessionJob: Job? = null
    private var roleResolutionJob: Job? = null

    fun startObserving(
        sessionChanged: (IOSSessionSnapshot?) -> Unit,
        roleResolutionChanged: (IOSRoleResolutionSnapshot) -> Unit
    ) {
        sessionJob?.cancel()
        roleResolutionJob?.cancel()
        sessionJob = scope.launch {
            observeUserSession().collect { session ->
                onSessionChanged(session)
                sessionChanged(session?.toIosSnapshot())
            }
        }
        roleResolutionJob = scope.launch {
            authRepository.roleResolutionState.collect { state ->
                roleResolutionChanged(state.toIosSnapshot())
            }
        }
    }

    fun stopObserving() {
        sessionJob?.cancel()
        sessionJob = null
        roleResolutionJob?.cancel()
        roleResolutionJob = null
    }

    fun retryRoleResolution() {
        val current = authRepository.currentUserSession()
        if (current != null) {
            authRepository.retryRoleResolution(current.uid)
        }
    }

    fun login(
        email: String,
        password: String,
        completion: (String?) -> Unit
    ) {
        scope.launch {
            when (val result = loginUseCase(email, password)) {
                is AppResult.Success -> completion(null)
                is AppResult.Failure -> completion(result.throwable.message ?: "Login failed.")
            }
        }
    }

    fun logout(completion: (String?) -> Unit) {
        scope.launch {
            when (val result = logoutUseCase()) {
                is AppResult.Success -> completion(null)
                is AppResult.Failure -> completion(result.throwable.message ?: "Sign-out failed.")
            }
        }
    }
}
