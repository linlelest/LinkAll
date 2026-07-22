package com.linkall.android.ui.screen.controlled

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ControlledScreen
import com.linkall.android.ui.navigation.NavigationState
import org.koin.androidx.compose.koinViewModel

/**
 * 被控页签根：根据子屏幕状态渲染对应内容
 */
@Composable
fun ControlledTab(
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val vm: ControlledViewModel = koinViewModel()
    when (nav.controlledScreen) {
        ControlledScreen.HOME -> ControlledHomeScreen(vm, nav, modifier)
        ControlledScreen.PERMISSIONS -> PermissionsScreen(vm, nav, modifier)
        ControlledScreen.SETTINGS -> ControlledSettingsScreen(vm, nav, modifier)
    }
}
