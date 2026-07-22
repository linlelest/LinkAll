package com.linkall.android.ui.screen.manage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState
import org.koin.androidx.compose.koinViewModel

/**
 * 管理页签根：根据登录状态和子屏幕路由渲染
 */
@Composable
fun ManageTab(
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val vm: ManageViewModel = koinViewModel()

    // 未登录 → 登录页
    if (!vm.isLoggedIn()) {
        LoginScreen(vm, nav, modifier)
        return
    }

    when (nav.manageScreen) {
        ManageScreen.LOGIN -> LoginScreen(vm, nav, modifier)
        ManageScreen.DASHBOARD -> DashboardScreen(vm, nav, modifier)
        ManageScreen.ANNOUNCEMENTS -> AnnouncementsScreen(vm, nav, modifier)
        ManageScreen.DEVICES -> DevicesScreen(vm, nav, modifier)
        ManageScreen.SETTINGS -> SettingsScreen(vm, nav, modifier)
        ManageScreen.OTA -> OtaScreen(vm, nav, modifier)
    }
}
