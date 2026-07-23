package com.linkall.android.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.repo.AuthRepository
import com.linkall.android.util.I18nHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 首次启动引导 ViewModel
 * 承载四步骤：语言、权限、服务器地址、登录
 */
class OnboardingViewModel(
    val settings: AppSettings,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // 加载已持久化的语言与服务器地址作为初始值
        viewModelScope.launch {
            val lang = settings.language.first()
            val server = settings.serverAddress.first()
            _uiState.value = _uiState.value.copy(selectedLanguage = lang, serverAddress = server)
        }
    }

    /** 登录状态：检查 SecureStorage 中是否存在 Token */
    fun isLoggedIn(): Boolean = authRepo.isLoggedIn()

    /** 设置语言并立即应用 */
    fun setLanguage(lang: String) = viewModelScope.launch {
        settings.setLanguage(lang)
        I18nHelper.setLanguage(lang)
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
    }

    /** 保存自定义服务器地址 */
    fun setServerAddress(addr: String) = viewModelScope.launch {
        settings.setServerAddress(addr)
        _uiState.value = _uiState.value.copy(serverAddress = addr)
    }

    /** 调用 /api/auth/login 登录，成功后同步持久化引导完成标记 */
    fun login(username: String, password: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        authRepo.login(username, password)
            .onSuccess {
                // 登录成功后立即持久化引导完成标记，确保 MainActivity 切换到主界面前已写入
                settings.setOnboardingCompleted(true)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    error = null
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "unknown error"
                )
            }
    }

    /** 标记引导完成（持久化），通常由 login 成功后自动调用 */
    fun completeOnboarding() = viewModelScope.launch {
        settings.setOnboardingCompleted(true)
    }
}

data class OnboardingUiState(
    val selectedLanguage: String = "system",
    val serverAddress: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)
