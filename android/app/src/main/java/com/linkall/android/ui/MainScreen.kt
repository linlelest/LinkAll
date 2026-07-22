package com.linkall.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dvr
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.linkall.android.R
import com.linkall.android.ui.navigation.MainTab
import com.linkall.android.ui.navigation.NavigationState
import com.linkall.android.ui.screen.controlled.ControlledTab
import com.linkall.android.ui.screen.controller.ControllerTab
import com.linkall.android.ui.screen.manage.ManageTab

/**
 * 主屏幕：底部三页签导航（被控 | 控制 | 管理）
 * 禁用 Jetpack Navigation，使用简单状态管理
 */
@Composable
fun MainScreen(nav: NavigationState) {
    Scaffold(
        bottomBar = { BottomTabs(nav) }
    ) { padding ->
        when (nav.currentTab) {
            MainTab.CONTROLLED -> ControlledTab(nav, Modifier.padding(padding))
            MainTab.CONTROLLER -> ControllerTab(nav, Modifier.padding(padding))
            MainTab.MANAGE -> ManageTab(nav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun BottomTabs(nav: NavigationState) {
    val tabs = listOf(
        TabItem(MainTab.CONTROLLED, Icons.Filled.Devices, R.string.nav_controlled),
        TabItem(MainTab.CONTROLLER, Icons.Filled.Dvr, R.string.nav_controller),
        TabItem(MainTab.MANAGE, Icons.Filled.ManageAccounts, R.string.nav_manage)
    )
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = nav.currentTab == tab.tab,
                onClick = { nav.selectTab(tab.tab) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes), style = MaterialThemeLocal()) }
            )
        }
    }
}

private data class TabItem(val tab: MainTab, val icon: ImageVector, val labelRes: Int)

@Composable
private fun MaterialThemeLocal() = androidx.compose.material3.MaterialTheme.typography.labelSmall
