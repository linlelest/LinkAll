package com.linkall.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linkall.android.data.model.DeviceStatus
import com.linkall.android.ui.theme.BusyColor
import com.linkall.android.ui.theme.OfflineColor
import com.linkall.android.ui.theme.OnlineColor
import com.linkall.android.ui.theme.SleepingColor

/**
 * 在线状态指示灯（小圆点）
 */
@Composable
fun StatusDot(
    status: DeviceStatus,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        DeviceStatus.ONLINE -> OnlineColor
        DeviceStatus.OFFLINE -> OfflineColor
        DeviceStatus.BUSY -> BusyColor
        DeviceStatus.SLEEPING -> SleepingColor
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 颜色版状态指示灯
 */
@Composable
fun StatusDotColored(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
