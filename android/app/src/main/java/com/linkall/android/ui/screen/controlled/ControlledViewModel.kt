package com.linkall.android.ui.screen.controlled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkall.android.data.local.AppSettings
import com.linkall.android.data.local.SecureStorage
import com.linkall.android.service.LinkALLAccessibilityService
import com.linkall.android.service.MediaProjectionService
import com.linkall.android.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 被控端 ViewModel：管理服务状态、设备编号/码、安全设置
 */
class ControlledViewModel(
    private val storage: SecureStorage,
    private val settings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlledUiState())
    val uiState: StateFlow<ControlledUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
            // 订阅安全设置流
            settings.allowAnonymous.collect { _uiState.value = _uiState.value.copy(allowAnonymous = it) }
        }
        viewModelScope.launch {
            settings.allowDeviceCode.collect { _uiState.value = _uiState.value.copy(allowDeviceCode = it) }
        }
        viewModelScope.launch {
            settings.allowRemoteControl.collect { _uiState.value = _uiState.value.copy(allowRemoteControl = it) }
        }
    }

    /** 刷新所有状态 */
    fun refresh() {
        val deviceId = storage.getDeviceId() ?: com.linkall.android.util.Util.generateDeviceId().also {
            storage.saveDeviceId(it)
        }
        val deviceCode = storage.getDeviceCode() ?: com.linkall.android.util.Util.generateDeviceCode().also {
            storage.saveDeviceCode(it)
        }
        _uiState.value = _uiState.value.copy(
            deviceId = deviceId,
            deviceCode = deviceCode,
            isServiceRunning = MediaProjectionService.isRunning,
            privacyScreenOn = MediaProjectionService.privacyScreenEnabled,
            username = storage.getUsername()
        )
    }

    /** 重置设备编号（12 位随机） */
    fun resetDeviceId() {
        val newId = com.linkall.android.util.Util.generateDeviceId()
        storage.saveDeviceId(newId)
        _uiState.value = _uiState.value.copy(deviceId = newId)
    }

    /** 自定义设备编号 */
    fun setCustomDeviceId(id: String): Boolean {
        if (!com.linkall.android.util.Util.isValidDeviceId(id)) return false
        storage.saveDeviceId(id)
        _uiState.value = _uiState.value.copy(deviceId = id)
        return true
    }

    /** 重置设备码 */
    fun resetDeviceCode() {
        val newCode = com.linkall.android.util.Util.generateDeviceCode()
        storage.saveDeviceCode(newCode)
        _uiState.value = _uiState.value.copy(deviceCode = newCode)
    }

    /** 退出软件：彻底停止服务 */
    fun logoutApp() {
        storage.clearAll()
        _uiState.value = ControlledUiState()
    }

    fun setAllowAnonymous(v: Boolean) = viewModelScope.launch { settings.setAllowAnonymous(v) }
    fun setAllowDeviceCode(v: Boolean) = viewModelScope.launch { settings.setAllowDeviceCode(v) }
    fun setAllowRemoteControl(v: Boolean) = viewModelScope.launch { settings.setAllowRemoteControl(v) }

    /** 检查无障碍服务状态 */
    fun checkAccessibilityEnabled(context: android.content.Context): Boolean =
        PermissionHelper.isAccessibilityEnabled(context, LinkALLAccessibilityService::class.java)
}

data class ControlledUiState(
    val deviceId: String = "",
    val deviceCode: String = "",
    val isServiceRunning: Boolean = false,
    val privacyScreenOn: Boolean = false,
    val username: String? = null,
    val allowAnonymous: Boolean = false,
    val allowDeviceCode: Boolean = true,
    val allowRemoteControl: Boolean = true
)
