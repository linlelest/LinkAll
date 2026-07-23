package com.linkall.android.ui.screen.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState
import org.koin.androidx.compose.koinViewModel

/**
 * 管理端设置子视图状态
 */
enum class ManageSettingView { NONE, PERMISSIONS, LANGUAGE, SERVER }

/**
 * 管理页签根：根据登录状态和子屏幕路由渲染
 * 底部常驻设置按钮（不论登录与否均可点击），点开包含三选项：
 * 权限引导 / 语言设置 / 自定义服务器地址
 */
@Composable
fun ManageTab(
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val vm: ManageViewModel = koinViewModel()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingView by remember { mutableStateOf(ManageSettingView.NONE) }

    Column(modifier.fillMaxSize()) {
        // 内容区
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (settingView) {
                ManageSettingView.NONE -> {
                    if (!vm.isLoggedIn()) {
                        LoginScreen(vm, nav, Modifier.fillMaxSize())
                    } else {
                        when (nav.manageScreen) {
                            ManageScreen.LOGIN -> LoginScreen(vm, nav, Modifier.fillMaxSize())
                            ManageScreen.DASHBOARD -> DashboardScreen(vm, nav, Modifier.fillMaxSize())
                            ManageScreen.ANNOUNCEMENTS -> AnnouncementsScreen(vm, nav, Modifier.fillMaxSize())
                            ManageScreen.DEVICES -> DevicesScreen(vm, nav, Modifier.fillMaxSize())
                            ManageScreen.SETTINGS -> SettingsScreen(vm, nav, Modifier.fillMaxSize())
                            ManageScreen.OTA -> OtaScreen(vm, nav, Modifier.fillMaxSize())
                        }
                    }
                }
                ManageSettingView.PERMISSIONS -> ManagePermissionsScreen(
                    onBack = { settingView = ManageSettingView.NONE }
                )
                ManageSettingView.LANGUAGE -> ManageLanguageScreen(
                    settings = vm.settings,
                    onBack = { settingView = ManageSettingView.NONE }
                )
                ManageSettingView.SERVER -> ManageServerScreen(
                    settings = vm.settings,
                    onBack = { settingView = ManageSettingView.NONE }
                )
            }
        }

        // 底部常驻设置按钮（仅在 NONE 视图显示，子视图内部有自己的返回按钮）
        if (settingView == ManageSettingView.NONE) {
            Surface(
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_title))
                }
            }
        }
    }

    // 设置弹层
    if (showSettingsSheet) {
        ManageSettingsSheet(
            onDismiss = { showSettingsSheet = false },
            onPermissions = {
                showSettingsSheet = false
                settingView = ManageSettingView.PERMISSIONS
            },
            onLanguage = {
                showSettingsSheet = false
                settingView = ManageSettingView.LANGUAGE
            },
            onServer = {
                showSettingsSheet = false
                settingView = ManageSettingView.SERVER
            }
        )
    }
}
