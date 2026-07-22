package com.linkall.android.ui.screen.controlled

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ControlledScreen
import com.linkall.android.ui.navigation.NavigationState

/**
 * 被控端本地设置：登录/退出账号、安全设置、退出软件
 */
@Composable
fun ControlledSettingsScreen(
    vm: ControlledViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.uiState.collectAsState()

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.settings_title)) {
            IconButton(onClick = { nav.navigateControlled(ControlledScreen.HOME) }) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }

        // 账号信息
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_account_section), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.username?.let {
                    Text("${stringResource(R.string.login_username)}: $it", style = MaterialTheme.typography.bodyMedium)
                } ?: Text(stringResource(R.string.nav_logout), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        // 安全设置卡片
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.security_allow_anonymous),
                    checked = state.allowAnonymous,
                    onChange = { vm.setAllowAnonymous(it) }
                )
                SwitchRow(
                    title = stringResource(R.string.security_allow_device_code),
                    checked = state.allowDeviceCode,
                    onChange = { vm.setAllowDeviceCode(it) }
                )
                SwitchRow(
                    title = stringResource(R.string.security_allow_remote_control),
                    checked = state.allowRemoteControl,
                    onChange = { vm.setAllowRemoteControl(it) }
                )
            }
        }

        // 退出软件
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.controlled_logout), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.controlled_logout_confirm), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    vm.logoutApp()
                    nav.navigateControlled(ControlledScreen.HOME)
                }) { Text(stringResource(R.string.common_confirm)) }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
