package com.linkall.android.ui.screen.controlled

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.service.MediaProjectionService
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.components.StatusDotColored
import com.linkall.android.ui.navigation.ControlledScreen
import com.linkall.android.ui.navigation.NavigationState
import com.linkall.android.ui.theme.OnlineColor
import com.linkall.android.ui.theme.OfflineColor
import com.linkall.android.util.Util

/**
 * 被控端主页：服务状态、设备编号/码展示、启停服务、入口
 */
@Composable
fun ControlledHomeScreen(
    vm: ControlledViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.controlled_title)) {
            OutlinedButton(onClick = { nav.navigateControlled(ControlledScreen.PERMISSIONS) }) {
                Text(stringResource(R.string.permission_title))
            }
        }

        // 服务状态卡片
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDotColored(
                        color = if (state.isServiceRunning) OnlineColor else OfflineColor,
                        size = 10.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isServiceRunning) stringResource(R.string.controlled_running)
                               else stringResource(R.string.controlled_stopped),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isServiceRunning) OnlineColor else OfflineColor
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isServiceRunning) {
                        OutlinedButton(onClick = {
                            context.startService(Intent(context, MediaProjectionService::class.java).setAction(MediaProjectionService.ACTION_DISCONNECT))
                        }) { Text(stringResource(R.string.controlled_stop)) }
                    } else {
                        // 启动需要 MediaProjection 授权，由 Activity 发起
                        ElevatedButton(onClick = { /* 触发 MediaProjection 授权 */ }) {
                            Text(stringResource(R.string.controlled_start))
                        }
                    }
                }
            }
        }

        // 设备编号卡片
        InfoCard(
            title = stringResource(R.string.controlled_device_id),
            value = state.deviceId,
            onCopy = { Util.copyToClipboard(context, state.deviceId) },
            onReset = { vm.resetDeviceId() },
            customInput = true,
            onCustom = { vm.setCustomDeviceId(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // 设备码卡片
        InfoCard(
            title = stringResource(R.string.controlled_device_code),
            value = state.deviceCode,
            onCopy = { Util.copyToClipboard(context, state.deviceCode) },
            onReset = { vm.resetDeviceCode() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // 安全与设置入口
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { nav.navigateControlled(ControlledScreen.SETTINGS) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Settings, null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_title))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 可复制/可重置的信息卡片
 */
@Composable
private fun InfoCard(
    title: String,
    value: String,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    customInput: Boolean = false,
    onCustom: (String) -> Boolean = { false }
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.common_copy)) }
                IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, stringResource(R.string.controlled_reset_id)) }
            }
            if (customInput) {
                var customId by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = customId,
                    onValueChange = { customId = it.uppercase() },
                    label = { Text(stringResource(R.string.controlled_custom_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = {
                    if (onCustom(customId)) customId = ""
                }) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}
