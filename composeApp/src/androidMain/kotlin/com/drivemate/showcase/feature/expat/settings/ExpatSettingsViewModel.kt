package com.drivemate.showcase.feature.expat.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.util.safeDriverLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Profil Expat untuk header Pengaturan dan layar Profil. Tidak membawa uid atau token. */
@Stable
data class ExpatProfileUi(val name: String?, val email: String?)

/**
 * Memuat nama (data pengguna, lalu awalan email) dan email pengguna aktif. `null` selama memuat atau bila gagal:
 * layar tetap berfungsi (bahasa, notifikasi, Keluar) dan header memakai label peran, tanpa data palsu.
 */
class ExpatSettingsViewModel(
    private val authRepo: AuthRepository,
    private val getUserDisplayName: GetUserDisplayNameUseCase
) : ViewModel() {

    private val _profile = MutableStateFlow<ExpatProfileUi?>(null)
    val profile: StateFlow<ExpatProfileUi?> = _profile

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val user = (authRepo.currentUser() as? AppResult.Success)?.data ?: return@launch
            val stored = (getUserDisplayName(user.uid) as? AppResult.Success)?.data
            _profile.value = ExpatProfileUi(
                name = profileDisplayName(stored, user.email),
                email = user.email.takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Nama tampilan Expat: nama pada data pengguna, lalu awalan email (title case), lalu `null`.
 * Rantai yang sama dengan sapaan Beranda (PR "sapaan Expat memakai nama"); disatukan setelah kedua PR merge.
 */
internal fun profileDisplayName(storedName: String?, email: String?): String? =
    safeDriverLabel(storedName, email, placeholder = "").takeIf { it.isNotBlank() }
