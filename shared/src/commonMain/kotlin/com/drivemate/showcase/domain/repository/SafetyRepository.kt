package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.util.AppResult

interface SafetyRepository {
    suspend fun submitSafetyDeclaration(payload: SafetyDeclaration): AppResult<Unit>
    suspend fun hasSafetyDeclaration(driverUid: String, dateEpochDay: Int): AppResult<Boolean>
}
