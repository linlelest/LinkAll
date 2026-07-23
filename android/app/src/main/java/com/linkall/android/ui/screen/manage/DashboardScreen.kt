package com.linkall.android.ui.screen.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState

/**
 * 仪表盘：服务器信息概览 + 功能入口卡片
 */
@Composable
fun DashboardScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val serverInfo by vm.server.state.collectAsState()

    LaunchedEffect(Unit) { vm.server.refresh() }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.nav_dashboard)) {
            androidx.compose.material3.IconButton(onClick = { vm.auth.logout(); nav.navigateManage(ManageScreen.LOGIN) }) {
                Icon(Icons.Filled.Logout, stringResource(R.string.nav_logout))
            }
        }

        // 服务器信息卡片
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dashboard_server_info), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                serverInfo?.let { info ->
                    InfoRow(stringResource(R.string.dashboard_hostname), info.hostname)
                    InfoRow(stringResource(R.string.dashboard_online_devices), info.onlineDevices.toString())
                    InfoRow(stringResource(R.string.dashboard_active_sessions), info.activeSessions.toString())
                    InfoRow(stringResource(R.string.dashboard_uptime), com.linkall.android.util.Util.formatDuration(info.uptime))
                    InfoRow(stringResource(R.string.dashboard_go_version), info.goVersion)
                } ?: run {
                    Text(stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 功能入口网格
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuCard(stringResource(R.string.nav_announcements), Icons.Filled.Campaign, Modifier.weight(1f)) {
                nav.navigateManage(ManageScreen.ANNOUNCEMENTS)
            }
            MenuCard(stringResource(R.string.nav_devices), Icons.Filled.Devices, Modifier.weight(1f)) {
                nav.navigateManage(ManageScreen.DEVICES)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuCard(stringResource(R.string.nav_settings), Icons.Filled.Settings, Modifier.weight(1f)) {
                nav.navigateManage(ManageScreen.SETTINGS)
            }
            MenuCard(stringResource(R.string.nav_ota), Icons.Filled.SystemUpdate, Modifier.weight(1f)) {
                nav.navigateManage(ManageScreen.OTA)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MenuCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(modifier = modifier.padding(vertical = 4.dp), onClick = onClick) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}
