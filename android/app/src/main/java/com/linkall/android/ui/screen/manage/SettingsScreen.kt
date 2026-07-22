package com.linkall.android.ui.screen.manage

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState
import com.linkall.android.util.I18nHelper
import kotlinx.coroutines.launch

/**
 * 设置页：账号、邀请码、安全、高级设置、语言
 */
@Composable
fun SettingsScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val lang by vm.settings.language.collectAsState(initial = "system")
    val server by vm.settings.serverAddress.collectAsState(initial = "")
    val timeout by vm.settings.connectionTimeout.collectAsState(initial = 15)

    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var serverInput by remember { mutableStateOf(server) }
    var timeoutInput by remember { mutableStateOf(timeout.toString()) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.settings_title)) {
            IconButton(onClick = { nav.navigateManage(ManageScreen.DASHBOARD) }) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }

        // 账号与安全
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_change_password), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = oldPwd, onValueChange = { oldPwd = it }, label = { Text(stringResource(R.string.settings_old_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newPwd, onValueChange = { newPwd = it }, label = { Text(stringResource(R.string.settings_new_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirmPwd, onValueChange = { confirmPwd = it }, label = { Text(stringResource(R.string.settings_confirm_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { /* vm.auth.changePassword */ }) { Text(stringResource(R.string.common_save)) }
            }
        }

        // 语言切换
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    LangChip(stringResource(R.string.settings_language_zh), lang == I18nHelper.LANG_ZH) { I18nHelper.setLanguage(I18nHelper.LANG_ZH) }
                    Spacer(Modifier.padding(4.dp))
                    LangChip(stringResource(R.string.settings_language_en), lang == I18nHelper.LANG_EN) { I18nHelper.setLanguage(I18nHelper.LANG_EN) }
                    Spacer(Modifier.padding(4.dp))
                    LangChip(stringResource(R.string.settings_language_system), lang == I18nHelper.LANG_SYSTEM) { I18nHelper.setLanguage(I18nHelper.LANG_SYSTEM) }
                }
            }
        }

        // 高级设置
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_advanced_section), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = serverInput, onValueChange = { serverInput = it }, label = { Text(stringResource(R.string.settings_server_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = timeoutInput, onValueChange = { timeoutInput = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(R.string.settings_connection_timeout)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        vm.settings.setServerAddress(serverInput)
                        vm.settings.setConnectionTimeout(timeoutInput.toIntOrNull() ?: 15)
                    }
                }) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
