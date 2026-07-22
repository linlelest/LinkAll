package com.linkall.android.ui.screen.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.data.model.ConnectionMode
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.NavigationState

/**
 * 连接页：输入设备编号/密码，模式切换，同账号自动发现设备列表
 */
@Composable
fun ConnectScreen(
    vm: ControllerViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    var deviceId by remember { mutableStateOf("") }
    var deviceCode by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ConnectionMode.ANONYMOUS) }
    val discovered by vm.discoveredDevices.collectAsState()

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        ScreenHeader(title = stringResource(R.string.control_title))

        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it.uppercase() },
            label = { Text(stringResource(R.string.control_device_id)) },
            placeholder = { Text(stringResource(R.string.control_id_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        if (mode == ConnectionMode.DEVICE_CODE) {
            OutlinedTextField(
                value = deviceCode,
                onValueChange = { deviceCode = it },
                label = { Text(stringResource(R.string.control_device_code)) },
                placeholder = { Text(stringResource(R.string.control_code_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        // 模式切换
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(stringResource(R.string.control_mode_anonymous), mode == ConnectionMode.ANONYMOUS) {
                mode = ConnectionMode.ANONYMOUS
            }
            ModeChip(stringResource(R.string.control_mode_same_account), mode == ConnectionMode.SAME_ACCOUNT) {
                mode = ConnectionMode.SAME_ACCOUNT
            }
            ModeChip(stringResource(R.string.control_mode_device_code), mode == ConnectionMode.DEVICE_CODE) {
                mode = ConnectionMode.DEVICE_CODE
            }
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.connect(deviceId, deviceCode, mode) },
            enabled = deviceId.length == 12,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.control_connect))
        }

        Spacer(Modifier.height(24.dp))

        // 同账号设备列表
        if (mode == ConnectionMode.SAME_ACCOUNT) {
            Text(stringResource(R.string.control_discovered_devices), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(discovered) { device ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(device.deviceName.ifEmpty { device.deviceId }, style = MaterialTheme.typography.bodyMedium)
                                Text(device.deviceId, style = MaterialTheme.typography.labelSmall)
                            }
                            Button(onClick = { deviceId = device.deviceId; vm.connect(device.deviceId, "", mode) }) {
                                Text(stringResource(R.string.common_connect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
