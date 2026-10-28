package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetChatContactsUseCase(
    private val userDirectoryRepository: UserDirectoryRepository
) {
    suspend operator fun invoke(roles: Set<Role>) =
        userDirectoryRepository.getUsersByRoles(roles)
}
