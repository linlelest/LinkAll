package com.linkall.android.ui.screen.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.navigation.NavigationState

/**
 * 控制会话页（全沉浸式）：
 * - SurfaceView 渲染视频流，双指捏合缩放
 * - 虚拟工具层覆盖（虚拟鼠标悬浮球、滚轮侧边条）
 * - 边缘滑动呼出设置面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlSessionScreen(
    vm: ControllerViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.uiState.collectAsState()
    var zoom by remember { mutableFloatStateOf(1f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.5f, 3f)
                    vm.setScale(zoom)
                }
            }
    ) {
        // 视频流渲染（SurfaceView 由 WebRTC VideoTrack attach）
        // TODO: SurfaceViewRenderer 由 PeerConnectionManager.remoteVideoTrack 提供
        AndroidView(
            factory = { ctx ->
                org.webrtc.SurfaceViewRenderer(ctx).apply {
                    init(com.linkall.android.webrtc.WebRtcInitializer.getEglBase().eglBaseContext, null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部状态栏 + 断开按钮
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.targetDeviceId,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            if (state.isConnecting) {
                Text(stringResource(R.string.control_connecting), color = Color.White)
            } else if (state.isConnected) {
                Text(stringResource(R.string.control_connected), color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.disconnect() }) {
                Icon(Icons.Filled.Close, stringResource(R.string.control_disconnect), tint = Color.White)
            }
        }

        // 右侧虚拟鼠标悬浮球（左/右键）
        Column(
            Modifier.align(Alignment.CenterEnd).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VirtualMouseButton(
                label = stringResource(R.string.control_mouse_left),
                alpha = state.mouseOpacity,
                onTap = { /* TODO: 发送左键点击 */ }
            )
            Spacer(Modifier.height(12.dp))
            VirtualMouseButton(
                label = stringResource(R.string.control_mouse_right),
                alpha = state.mouseOpacity,
                onTap = { /* TODO: 发送右键点击 */ }
            )
        }

        // 左侧滚轮侧边条
        WheelSlider(
            modifier = Modifier.align(Alignment.CenterStart).padding(4.dp),
            onScroll = { delta -> /* TODO: 发送 wheel 事件 */ }
        )

        // 底部工具栏：虚拟键盘 / 设置面板 / 文件
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(onClick = { vm.toggleKeyboard() }) {
                Icon(Icons.Filled.Keyboard, stringResource(R.string.control_keyboard))
            }
            FloatingActionButton(onClick = { vm.toggleSettingsPanel() }) {
                Icon(Icons.Filled.Settings, stringResource(R.string.control_settings))
            }
            FloatingActionButton(onClick = { /* TODO: 打开文件管理 */ }) {
                Icon(Icons.Filled.Mouse, stringResource(R.string.control_files))
            }
        }

        // 参数面板（底部抽屉）
        if (state.settingsPanelOpen) {
            ModalBottomSheet(
                onDismissRequest = { vm.toggleSettingsPanel() },
                sheetState = sheetState
            ) {
                SettingsPanelContent(vm)
            }
        }
    }
}

/**
 * 虚拟鼠标按钮悬浮球
 */
@Composable
private fun VirtualMouseButton(label: String, alpha: Float, onTap: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = alpha),
        onClick = onTap
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(12.dp),
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * 滚轮侧边条
 */
@Composable
private fun WheelSlider(modifier: Modifier = Modifier, onScroll: (deltaY: Int) -> Unit) {
    var sliderValue by remember { mutableFloatStateOf(0.5f) }
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.3f)
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                val delta = ((it - sliderValue) * 100).toInt()
                if (delta != 0) onScroll(delta)
                sliderValue = it
            },
            modifier = Modifier.height(120.dp).width(24.dp)
        )
    }
}

/**
 * 参数面板内容：缩放/码率/帧率/防窥屏/文件入口
 */
@Composable
private fun SettingsPanelContent(vm: ControllerViewModel) {
    val state by vm.uiState.collectAsState()
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.control_settings), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        // 缩放
        Text("${stringResource(R.string.control_scale)}: ${(state.scale * 100).toInt()}%")
        Slider(
            value = state.scale,
            onValueChange = { vm.setScale(it) },
            valueRange = 0.1f..3f
        )

        // 码率
        Text("${stringResource(R.string.control_bitrate)}: ${state.maxBitrate / 1000}kbps")
        Slider(
            value = state.maxBitrate.toFloat(),
            onValueChange = { vm.setBitrate(it.toLong()) },
            valueRange = 512000f..50000000f
        )

        // 帧率
        Text("${stringResource(R.string.control_fps)}: ${state.fps}")
        Slider(
            value = state.fps.toFloat(),
            onValueChange = { vm.setFps(it.toInt()) },
            valueRange = 15f..144f,
            steps = 8
        )

        // 防窥屏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.control_privacy_screen), Modifier.weight(1f))
            Switch(checked = state.privacyScreen, onCheckedChange = { vm.togglePrivacyScreen() })
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { /* TODO: 打开文件传输 */ }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.control_files))
        }
    }
}
