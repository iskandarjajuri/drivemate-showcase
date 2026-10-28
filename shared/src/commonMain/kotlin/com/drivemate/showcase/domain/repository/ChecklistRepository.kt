package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.util.AppResult

interface ChecklistRepository {
    suspend fun submitChecklist(checklist: Checklist): AppResult<Unit>
    suspend fun hasChecklist(driverUid: String, dateEpochDay: Int): AppResult<Boolean>
}
