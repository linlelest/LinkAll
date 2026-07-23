package com.linkall.android.ui.screen.controlled

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.service.LinkALLAccessibilityService
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.NavigationState
import com.linkall.android.util.PermissionHelper

/**
 * 权限引导页：首次进入引导开启各类保活权限
 */
@Composable
fun PermissionsScreen(
    vm: ControlledViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.permission_title)) {
            IconButton(onClick = { nav.navigateControlled(com.linkall.android.ui.navigation.ControlledScreen.HOME) }) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }

        // 无障碍服务
        PermissionCard(
            title = stringResource(R.string.permission_accessibility),
            description = stringResource(R.string.permission_accessibility_desc),
            granted = remember(refreshKey) {
                PermissionHelper.isAccessibilityEnabled(context, LinkALLAccessibilityService::class.java)
            },
            onOpen = { PermissionHelper.openAccessibilitySettings(context) }
        )

        // 后台弹出界面
        PermissionCard(
            title = stringResource(R.string.permission_display_over_other),
            description = stringResource(R.string.permission_display_over_other_desc),
            granted = remember(refreshKey) { PermissionHelper.canDrawOverApps(context) },
            onOpen = { PermissionHelper.openOverlaySettings(context) }
        )

        // 电池优化白名单
        PermissionCard(
            title = stringResource(R.string.permission_battery),
            description = stringResource(R.string.permission_battery_desc),
            granted = remember(refreshKey) { PermissionHelper.isBatteryOptimizationIgnored(context) },
            onOpen = { PermissionHelper.openBatteryOptimizationSettings(context) }
        )

        // 开机自启
        PermissionCard(
            title = stringResource(R.string.permission_autostart),
            description = stringResource(R.string.permission_autostart_desc),
            granted = false, // 各厂商无法程序化检测
            onOpen = {
                PermissionHelper.autostartIntent()?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }.onFailure {
                        PermissionHelper.openAppDetailSettings(context)
                    }
                } ?: PermissionHelper.openAppDetailSettings(context)
            }
        )

        // 屏幕录制（MediaProjection 授权在启动服务时发起）
        PermissionCard(
            title = stringResource(R.string.permission_capture),
            description = stringResource(R.string.permission_capture_desc),
            granted = vm.uiState.value.isServiceRunning,
            onOpen = { /* 由启动服务时触发 */ }
        )

        // 通知权限
        PermissionCard(
            title = stringResource(R.string.permission_notification),
            description = stringResource(R.string.permission_notification_desc),
            granted = remember(refreshKey) { PermissionHelper.hasNotificationPermission(context) },
            onOpen = { PermissionHelper.openNotificationSettings(context) }
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_refresh)) }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onOpen: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (granted) stringResource(R.string.permission_granted)
                       else stringResource(R.string.permission_not_granted),
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            if (!granted) {
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.permission_open_setting)) }
            }
        }
    }
}
