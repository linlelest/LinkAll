package com.linkall.android.ui.screen.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkall.android.data.model.ConnectionMode
import com.linkall.android.data.model.DeviceInfo
import com.linkall.android.data.model.ScreenConfig
import com.linkall.android.data.repo.DeviceRepository
import com.linkall.android.data.local.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 控制端 ViewModel：连接管理、参数控制、虚拟工具状态
 */
class ControllerViewModel(
    private val storage: SecureStorage,
    private val deviceRepo: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    /** 同账号发现的设备列表 */
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    init { refreshDevices() }

    /** 刷新同账号设备列表 */
    fun refreshDevices() = viewModelScope.launch {
        deviceRepo.list().onSuccess { _discoveredDevices.value = it.items }
    }

    /** 发起连接 */
    fun connect(deviceId: String, deviceCode: String, mode: ConnectionMode) {
        _uiState.value = _uiState.value.copy(
            isConnecting = true,
            targetDeviceId = deviceId,
            connectionMode = mode,
            error = null
        )
        // TODO: 通过 SignalingClient + PeerConnectionManager 建立连接
    }

    /** 断开连接 */
    fun disconnect() {
        _uiState.value = ControllerUiState()
    }

    /** 更新屏幕缩放 */
    fun setScale(scale: Float) {
        _uiState.value = _uiState.value.copy(scale = scale)
        // TODO: sendScreenConfig
    }

    /** 更新码率 */
    fun setBitrate(bitrate: Long) {
        _uiState.value = _uiState.value.copy(maxBitrate = bitrate)
    }

    /** 更新帧率 */
    fun setFps(fps: Int) {
        _uiState.value = _uiState.value.copy(fps = fps)
    }

    /** 切换防窥屏 */
    fun togglePrivacyScreen() {
        _uiState.value = _uiState.value.copy(privacyScreen = !_uiState.value.privacyScreen)
        // TODO: 发送 privacy_screen 信令
    }

    /** 切换虚拟键盘显示 */
    fun toggleKeyboard() {
        _uiState.value = _uiState.value.copy(keyboardVisible = !_uiState.value.keyboardVisible)
    }

    /** 切换参数面板显示 */
    fun toggleSettingsPanel() {
        _uiState.value = _uiState.value.copy(settingsPanelOpen = !_uiState.value.settingsPanelOpen)
    }

    /** 设置鼠标透明度 */
    fun setMouseOpacity(opacity: Float) {
        _uiState.value = _uiState.value.copy(mouseOpacity = opacity)
    }
}

data class ControllerUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val targetDeviceId: String = "",
    val connectionMode: ConnectionMode = ConnectionMode.ANONYMOUS,
    val scale: Float = 1.0f,
    val maxBitrate: Long = 8_000_000,
    val fps: Int = 30,
    val privacyScreen: Boolean = false,
    val keyboardVisible: Boolean = false,
    val settingsPanelOpen: Boolean = false,
    val mouseOpacity: Float = 0.6f,
    val error: String? = null
)
