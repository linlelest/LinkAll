package com.linkall.android.ui.screen.controller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.data.model.ConnectionMode
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ControllerScreen
import com.linkall.android.ui.navigation.NavigationState

/**
 * 控制页签根：根据连接状态切换连接页/控制会话页
 */
@Composable
fun ControllerTab(
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val vm: ControllerViewModel = org.koin.androidx.compose.koinViewModel()
    val state by vm.uiState.collectAsState()
    if (state.isConnected || state.isConnecting) {
        ControlSessionScreen(vm, nav, modifier)
    } else {
        ConnectScreen(vm, nav, modifier)
    }
}
