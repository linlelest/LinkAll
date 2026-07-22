package com.linkall.android.ui.screen.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkall.android.data.repo.AnnouncementRepository
import com.linkall.android.data.repo.AuthRepository
import com.linkall.android.data.repo.DeviceRepository
import com.linkall.android.data.repo.InviteRepository
import com.linkall.android.data.repo.OtaRepository
import com.linkall.android.data.repo.SecurityRepository
import com.linkall.android.data.repo.ServerRepository
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.local.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 管理端 ViewModel：统一承载登录、用户、公告、设备、邀请码、安全、OTA、服务器信息
 */
class ManageViewModel(
    private val authRepo: AuthRepository,
    private val deviceRepo: DeviceRepository,
    private val announceRepo: AnnouncementRepository,
    private val inviteRepo: InviteRepository,
    private val securityRepo: SecurityRepository,
    private val serverRepo: ServerRepository,
    private val otaRepo: OtaRepository,
    val settings: AppSettings,
    val storage: SecureStorage
) : ViewModel() {

    val auth = AuthStateHolder(authRepo)
    val devices = DevicesStateHolder(deviceRepo)
    val announcements = AnnouncementsStateHolder(announceRepo)
    val invites = InvitesStateHolder(inviteRepo)
    val security = SecurityStateHolder(securityRepo)
    val server = ServerStateHolder(serverRepo)
    val ota = OtaStateHolder(otaRepo)

    fun isLoggedIn(): Boolean = authRepo.isLoggedIn()
}

/** 登录状态持有者 */
class AuthStateHolder(private val repo: AuthRepository) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(username: String, password: String) = kotlinx.coroutines.MainScope().launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.login(username, password)
            .onSuccess { _state.value = AuthUiState(isLoggedIn = true, user = it) }
            .onFailure { _state.value = AuthUiState(error = it.message) }
    }

    fun register(username: String, password: String, inviteCode: String) = kotlinx.coroutines.MainScope().launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repo.register(username, password, inviteCode)
            .onSuccess { _state.value = AuthUiState(isLoggedIn = true, user = it) }
            .onFailure { _state.value = AuthUiState(error = it.message) }
    }

    fun logout() {
        repo.logout()
        _state.value = AuthUiState()
    }

    fun checkLogin() = kotlinx.coroutines.MainScope().launch {
        if (repo.isLoggedIn()) {
            repo.getMe().onSuccess { _state.value = AuthUiState(isLoggedIn = true, user = it) }
        }
    }
}

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val user: com.linkall.android.data.model.UserInfo? = null,
    val error: String? = null
)

class DevicesStateHolder(private val repo: DeviceRepository) {
    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    fun refresh(onlineOnly: Boolean = false) = kotlinx.coroutines.MainScope().launch {
        _state.value = _state.value.copy(isLoading = true)
        repo.list(onlineOnly).onSuccess {
            _state.value = DevicesUiState(items = it.items, total = it.total)
        }.onFailure {
            _state.value = DevicesUiState(error = it.message)
        }
    }

    fun kick(deviceId: String) = kotlinx.coroutines.MainScope().launch {
        repo.kick(deviceId)
        refresh()
    }
}

data class DevicesUiState(
    val isLoading: Boolean = false,
    val items: List<com.linkall.android.data.model.DeviceInfo> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

class AnnouncementsStateHolder(private val repo: AnnouncementRepository) {
    private val _state = MutableStateFlow(AnnouncementsUiState())
    val state: StateFlow<AnnouncementsUiState> = _state.asStateFlow()

    fun refresh() = kotlinx.coroutines.MainScope().launch {
        _state.value = _state.value.copy(isLoading = true)
        repo.list().onSuccess {
            _state.value = AnnouncementsUiState(items = it.items, total = it.total)
        }.onFailure {
            _state.value = AnnouncementsUiState(error = it.message)
        }
    }

    fun markRead(id: Long) = kotlinx.coroutines.MainScope().launch {
        repo.markRead(id)
        refresh()
    }
}

data class AnnouncementsUiState(
    val isLoading: Boolean = false,
    val items: List<com.linkall.android.data.model.Announcement> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

class InvitesStateHolder(private val repo: InviteRepository) {
    private val _state = MutableStateFlow(InvitesUiState())
    val state: StateFlow<InvitesUiState> = _state.asStateFlow()

    fun refresh() = kotlinx.coroutines.MainScope().launch {
        repo.list().onSuccess { _state.value = InvitesUiState(items = it.items, total = it.total) }
    }

    fun generate(count: Int, ttlHours: Int, note: String) = kotlinx.coroutines.MainScope().launch {
        repo.generate(count, ttlHours, note).onSuccess { refresh() }
    }
}

data class InvitesUiState(
    val isLoading: Boolean = false,
    val items: List<com.linkall.android.data.model.InviteCode> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

class SecurityStateHolder(private val repo: SecurityRepository) {
    private val _state = MutableStateFlow(SecurityUiState())
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    fun refresh() = kotlinx.coroutines.MainScope().launch {
        repo.get().onSuccess { _state.value = _state.value.copy(settings = it) }
    }

    fun update(settings: com.linkall.android.data.model.SecuritySettings) = kotlinx.coroutines.MainScope().launch {
        repo.update(settings).onSuccess { _state.value = _state.value.copy(settings = settings) }
    }
}

data class SecurityUiState(
    val settings: com.linkall.android.data.model.SecuritySettings = com.linkall.android.data.model.SecuritySettings(),
    val error: String? = null
)

class ServerStateHolder(private val repo: ServerRepository) {
    private val _state = MutableStateFlow<com.linkall.android.data.model.ServerInfo?>(null)
    val state: StateFlow<com.linkall.android.data.model.ServerInfo?> = _state.asStateFlow()

    fun refresh() = kotlinx.coroutines.MainScope().launch {
        repo.getInfo().onSuccess { _state.value = it }
    }
}

class OtaStateHolder(private val repo: OtaRepository) {
    private val _state = MutableStateFlow(OtaUiState())
    val state: StateFlow<OtaUiState> = _state.asStateFlow()

    fun checkUpdate(currentVersion: String) = kotlinx.coroutines.MainScope().launch {
        _state.value = _state.value.copy(isChecking = true)
        repo.checkUpdate(currentVersion)
            .onSuccess {
                _state.value = OtaUiState(
                    latest = it,
                    hasUpdate = repo.isNewer(currentVersion, it.version),
                    forceUpdate = it.forceUpdate
                )
            }
            .onFailure { _state.value = OtaUiState(error = it.message) }
    }
}

data class OtaUiState(
    val isChecking: Boolean = false,
    val latest: com.linkall.android.data.model.OtaRelease? = null,
    val hasUpdate: Boolean = false,
    val forceUpdate: Boolean = false,
    val error: String? = null
)
