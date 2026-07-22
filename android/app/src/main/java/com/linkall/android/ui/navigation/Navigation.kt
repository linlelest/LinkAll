package com.linkall.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * 简单状态导航（禁用 Jetpack Navigation 重型组件）
 * 三页签主路由 + 子屏幕覆盖栈
 */
enum class MainTab(val titleKey: String) {
    CONTROLLED("nav_controlled"),
    CONTROLLER("nav_controller"),
    MANAGE("nav_manage")
}

/**
 * 管理页签下的子屏幕
 */
enum class ManageScreen {
    DASHBOARD, ANNOUNCEMENTS, DEVICES, SETTINGS, OTA, LOGIN
}

/**
 * 被控页签下的子屏幕
 */
enum class ControlledScreen {
    HOME, PERMISSIONS, SETTINGS
}

/**
 * 控制页签下的子屏幕
 */
enum class ControllerScreen {
    CONNECT, CONTROL_SESSION
}

/**
 * 导航状态：保存当前主页签 + 各页签内的子屏幕栈
 */
class NavigationState(
    initialTab: MainTab = MainTab.CONTROLLED,
    initialControlled: ControlledScreen = ControlledScreen.HOME,
    initialController: ControllerScreen = ControllerScreen.CONNECT,
    initialManage: ManageScreen = ManageScreen.DASHBOARD
) {
    var currentTab: MainTab by mutableStateOf(initialTab)
        private set

    var controlledScreen: ControlledScreen by mutableStateOf(initialControlled)
        private set

    var controllerScreen: ControllerScreen by mutableStateOf(initialController)
        private set

    var manageScreen: ManageScreen by mutableStateOf(initialManage)
        private set

    fun selectTab(tab: MainTab) { currentTab = tab }

    fun navigateControlled(screen: ControlledScreen) { controlledScreen = screen }
    fun navigateController(screen: ControllerScreen) { controllerScreen = screen }
    fun navigateManage(screen: ManageScreen) { manageScreen = screen }

    companion object {
        val Saver: Saver<NavigationState, *> = Saver(
            save = { listOf(it.currentTab.name, it.controlledScreen.name, it.controllerScreen.name, it.manageScreen.name) },
            restore = { list ->
                NavigationState(
                    MainTab.valueOf(list[0] as String),
                    ControlledScreen.valueOf(list[1] as String),
                    ControllerScreen.valueOf(list[2] as String),
                    ManageScreen.valueOf(list[3] as String)
                )
            }
        )
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    return rememberSaveable(saver = NavigationState.Saver) { NavigationState() }
}
