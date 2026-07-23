package com.linkall.android.ui.screen.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.components.EmptyView
import com.linkall.android.ui.components.LoadingView
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.components.StatusDot
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState
import com.linkall.android.util.Util

/**
 * 设备列表：在线/离线状态，一键连接，远程踢出，查看设备信息
 */
@Composable
fun DevicesScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.devices.state.collectAsState()
    var onlineOnly by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(onlineOnly) { vm.devices.refresh(onlineOnly) }

    Scaffold(
        topBar = {
            ScreenHeader(title = stringResource(R.string.devices_title)) {
                IconButton(onClick = { nav.navigateManage(ManageScreen.DASHBOARD) }) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
                }
                IconButton(onClick = { vm.devices.refresh(onlineOnly) }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.common_refresh))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.devices_filter_online), Modifier.weight(1f))
                Switch(checked = onlineOnly, onCheckedChange = { onlineOnly = it })
            }
            when {
                state.isLoading -> LoadingView()
                state.items.isEmpty() && state.error == null ->
                    EmptyView(stringResource(R.string.devices_empty))
                state.error != null ->
                    com.linkall.android.ui.components.ErrorView(state.error!!, onRetry = { vm.devices.refresh(onlineOnly) })
                else -> LazyColumn {
                    items(state.items) { device ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(device.status, size = 8.dp)
                                    Spacer(Modifier.height(0.dp))
                                    Spacer(Modifier.padding(start = 8.dp))
                                    Text(device.deviceName.ifEmpty { device.deviceId }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    OutlinedButton(onClick = { vm.devices.kick(device.deviceId) }) {
                                        Text(stringResource(R.string.common_kick))
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text(device.deviceId, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { Util.copyToClipboard(context, device.deviceId) }) {
                                        Icon(Icons.Filled.ContentCopy, stringResource(R.string.devices_copy_id))
                                    }
                                }
                                Text("${stringResource(R.string.devices_platform)}: ${device.platform}  ${stringResource(R.string.devices_status)}: ${device.status.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
