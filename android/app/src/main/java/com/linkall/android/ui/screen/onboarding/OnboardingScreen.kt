package com.linkall.android.ui.screen.onboarding

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.linkall.android.R
import com.linkall.android.service.LinkALLAccessibilityService
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.util.I18nHelper
import com.linkall.android.util.PermissionHelper

/**
 * 首次启动引导界面：四步骤（语言 -> 权限 -> 服务器地址 -> 登录）
 * @param alreadyCompleted 引导是否已完成（已完成则跳过前三步，仅显示登录步骤）
 * @param onComplete 全部完成后的回调（已写入持久化标记并登录成功）
 */
@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel,
    alreadyCompleted: Boolean,
    onComplete: () -> Unit
) {
    var currentStep by rememberSaveable {
        mutableStateOf(if (alreadyCompleted) 4 else 1)
    }
    val state by vm.uiState.collectAsState()
    val ctx = LocalContext.current
    // 权限检测刷新键：在 Activity 回到前台或用户手动刷新时递增
    var permCheckKey by remember { mutableStateOf(0) }

    // 当 Activity 回到前台时自动刷新权限状态（用户从系统设置返回）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permCheckKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // 重新计算必需权限是否全部授权（依赖 permCheckKey 触发重组）
    val allPermissionsGranted = remember(permCheckKey) {
        checkAllRequiredPermissions(ctx)
    }

    // 登录成功后：标记引导完成并通知宿主切换主界面
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            vm.completeOnboarding()
            onComplete()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部进度指示（已完成模式不显示，仅显示登录步骤）
        if (!alreadyCompleted) {
            LinearProgressIndicator(
                progress = { currentStep / 4f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 步骤内容区
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (currentStep) {
                1 -> LanguageStep(vm, state)
                2 -> PermissionsStep(permCheckKey) { permCheckKey++ }
                3 -> ServerStep(vm, state)
                4 -> LoginStep(vm, state)
            }
        }

        // 底部导航按钮
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 仅在完整四步流程中允许返回上一步
            if (!alreadyCompleted && currentStep > 1) {
                OutlinedButton(onClick = { currentStep-- }) {
                    Text(stringResource(R.string.common_prev))
                }
            }
            Spacer(Modifier.weight(1f))
            if (currentStep < 4) {
                val canNext = when (currentStep) {
                    1 -> true // 语言步骤始终允许下一步（有默认）
                    2 -> allPermissionsGranted // 必需权限全部授权
                    3 -> true // 服务器地址留空表示使用默认
                    else -> false
                }
                Button(onClick = { currentStep++ }, enabled = canNext) {
                    Text(stringResource(R.string.common_next))
                }
            }
        }
    }
}

/** 检查可检测的必需权限是否全部授权 */
private fun checkAllRequiredPermissions(ctx: Context): Boolean {
    return PermissionHelper.isAccessibilityEnabled(ctx, LinkALLAccessibilityService::class.java) &&
        PermissionHelper.canDrawOverApps(ctx) &&
        PermissionHelper.isBatteryOptimizationIgnored(ctx) &&
        PermissionHelper.hasNotificationPermission(ctx)
}

// ============================ 步骤一：语言 ============================

@Composable
private fun LanguageStep(vm: OnboardingViewModel, state: OnboardingUiState) {
    val options = listOf(
        I18nHelper.LANG_ZH to stringResource(R.string.settings_language_zh),
        I18nHelper.LANG_EN to stringResource(R.string.settings_language_en),
        I18nHelper.LANG_SYSTEM to stringResource(R.string.settings_language_system)
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenHeader(title = stringResource(R.string.onboarding_step_language))
        Text(
            stringResource(R.string.onboarding_step_language_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        options.forEach { (code, label) ->
            val selected = state.selectedLanguage == code
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .selectable(selected = selected, onClick = { vm.setLanguage(code) })
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { vm.setLanguage(code) })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// ============================ 步骤二：权限授权 ============================

@Composable
private fun PermissionsStep(refreshKey: Int, onRefresh: () -> Unit) {
    val ctx = LocalContext.current

    val permissions = remember(refreshKey) {
        listOf(
            PermissionItem(
                title = ctx.getString(R.string.permission_accessibility),
                description = ctx.getString(R.string.permission_accessibility_desc),
                granted = PermissionHelper.isAccessibilityEnabled(ctx, LinkALLAccessibilityService::class.java),
                onOpen = { PermissionHelper.openAccessibilitySettings(ctx) }
            ),
            PermissionItem(
                title = ctx.getString(R.string.permission_display_over_other),
                description = ctx.getString(R.string.permission_display_over_other_desc),
                granted = PermissionHelper.canDrawOverApps(ctx),
                onOpen = { PermissionHelper.openOverlaySettings(ctx) }
            ),
            PermissionItem(
                title = ctx.getString(R.string.permission_battery),
                description = ctx.getString(R.string.permission_battery_desc),
                granted = PermissionHelper.isBatteryOptimizationIgnored(ctx),
                onOpen = { PermissionHelper.openBatteryOptimizationSettings(ctx) }
            ),
            PermissionItem(
                title = ctx.getString(R.string.permission_notification),
                description = ctx.getString(R.string.permission_notification_desc),
                granted = PermissionHelper.hasNotificationPermission(ctx),
                onOpen = { PermissionHelper.openNotificationSettings(ctx) }
            ),
            // 开机自启无法程序化检测，仅提供跳转入口
            PermissionItem(
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenHeader(title = stringResource(R.string.onboarding_step_permissions))
        Text(
            stringResource(R.string.onboarding_step_permissions_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        permissions.forEach { item -> PermissionCard(item) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.common_refresh))
            }
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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

private data class PermissionItem(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onOpen: () -> Unit
)

// ============================ 步骤三：服务器地址 ============================

@Composable
private fun ServerStep(vm: OnboardingViewModel, state: OnboardingUiState) {
    // 依赖 state.serverAddress 重新初始化，确保 VM 异步加载已保存地址后能回填
    var input by remember(state.serverAddress) { mutableStateOf(state.serverAddress) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenHeader(title = stringResource(R.string.onboarding_step_server))
        Text(
            stringResource(R.string.onboarding_step_server_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
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
            onClick = { vm.setServerAddress(input.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.common_save))
        }
    }
}

// ============================ 步骤四：登录 ============================

@Composable
private fun LoginStep(vm: OnboardingViewModel, state: OnboardingUiState) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(title = stringResource(R.string.onboarding_step_login))
        Text(
            stringResource(R.string.onboarding_step_login_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.login_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.login(username.trim(), password) },
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.login_submit))
            }
        }
    }
}
