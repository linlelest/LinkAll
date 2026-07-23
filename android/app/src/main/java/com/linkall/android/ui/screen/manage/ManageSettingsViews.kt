package com.linkall.android.ui.screen.manage

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.linkall.android.R
import com.linkall.android.data.local.AppSettings
import com.linkall.android.service.LinkALLAccessibilityService
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.util.I18nHelper
import com.linkall.android.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * 管理端底部设置弹层：权限引导 / 语言 / 自定义服务器地址 三选项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSettingsSheet(
    onDismiss: () -> Unit,
    onPermissions: () -> Unit,
    onLanguage: () -> Unit,
    onServer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsMenuItem(
                title = stringResource(R.string.permission_title),
                onClick = onPermissions
            )
            SettingsMenuItem(
                title = stringResource(R.string.settings_language),
                onClick = onLanguage
            )
            SettingsMenuItem(
                title = stringResource(R.string.settings_server_address),
                onClick = onServer
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsMenuItem(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

// ============================ 权限引导子屏幕 ============================

/**
 * 管理端权限引导页：跳转各种系统权限设置（与登录状态无关）
 */
@Composable
fun ManagePermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    // 从系统设置返回时自动刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    val permissions = remember(refreshKey) {
        listOf(
            PermItem(
                title = ctx.getString(R.string.permission_accessibility),
                description = ctx.getString(R.string.permission_accessibility_desc),
                granted = PermissionHelper.isAccessibilityEnabled(ctx, LinkALLAccessibilityService::class.java),
                onOpen = { PermissionHelper.openAccessibilitySettings(ctx) }
            ),
            PermItem(
                title = ctx.getString(R.string.permission_display_over_other),
                description = ctx.getString(R.string.permission_display_over_other_desc),
                granted = PermissionHelper.canDrawOverApps(ctx),
                onOpen = { PermissionHelper.openOverlaySettings(ctx) }
            ),
            PermItem(
                title = ctx.getString(R.string.permission_battery),
                description = ctx.getString(R.string.permission_battery_desc),
                granted = PermissionHelper.isBatteryOptimizationIgnored(ctx),
                onOpen = { PermissionHelper.openBatteryOptimizationSettings(ctx) }
            ),
            PermItem(
                title = ctx.getString(R.string.permission_notification),
                description = ctx.getString(R.string.permission_notification_desc),
                granted = PermissionHelper.hasNotificationPermission(ctx),
                onOpen = { PermissionHelper.openNotificationSettings(ctx) }
            ),
            PermItem(
                title = ctx.getString(R.string.permission_autostart),
                description = ctx.getString(R.string.permission_autostart_desc),
                granted = false,
                onOpen = {
                    PermissionHelper.autostartIntent()?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(it) }.onFailure {
                            PermissionHelper.openAppDetailSettings(ctx)
                        }
                    } ?: PermissionHelper.openAppDetailSettings(ctx)
                }
            )
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.permission_title)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }
        permissions.forEach { item -> PermCard(item) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = { refreshKey++ }) {
                Text(stringResource(R.string.common_refresh))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermCard(item: PermItem) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.granted) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (item.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.granted) stringResource(R.string.permission_granted)
                       else stringResource(R.string.permission_not_granted),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            if (!item.granted) {
                OutlinedButton(onClick = item.onOpen) {
                    Text(stringResource(R.string.permission_open_setting))
                }
            }
        }
    }
}

private data class PermItem(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onOpen: () -> Unit
)

// ============================ 语言设置子屏幕 ============================

/**
 * 管理端语言设置页：中文/English/跟随系统
 */
@Composable
fun ManageLanguageScreen(settings: AppSettings, onBack: () -> Unit) {
    val lang by settings.language.collectAsState(initial = "system")
    val options = listOf(
        I18nHelper.LANG_ZH to stringResource(R.string.settings_language_zh),
        I18nHelper.LANG_EN to stringResource(R.string.settings_language_en),
        I18nHelper.LANG_SYSTEM to stringResource(R.string.settings_language_system)
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.settings_language)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }
        options.forEach { (code, label) ->
            val selected = lang == code
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .selectable(selected = selected, onClick = {
                        scope.launch {
                            settings.setLanguage(code)
                            I18nHelper.setLanguage(code)
                        }
                    })
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = {
                        scope.launch {
                            settings.setLanguage(code)
                            I18nHelper.setLanguage(code)
                        }
                    })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================ 服务器地址设置子屏幕 ============================

/**
 * 管理端自定义服务器地址设置页
 */
@Composable
fun ManageServerScreen(settings: AppSettings, onBack: () -> Unit) {
    val server by settings.serverAddress.collectAsState(initial = "")
    // 依赖 server 重新初始化，确保异步加载的已保存地址能回填
    var input by remember(server) { mutableStateOf(server) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.settings_server_address)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.settings_server_address)) },
                placeholder = { Text(stringResource(R.string.onboarding_server_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_server_address_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch { settings.setServerAddress(input.trim()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
