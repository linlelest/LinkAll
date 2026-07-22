package com.linkall.android.ui.screen.controlled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkall.android.R

/**
 * 连接确认弹窗：收到匿名控制请求时全屏半透明遮罩显示
 * 显示请求端 IP/ID/设备名，按钮 [允许一次] [永久允许] [拒绝] [输入设备码放行]
 */
@Composable
fun ConnectionConfirmDialog(
    requesterIp: String,
    requesterId: String,
    requesterName: String,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
    onDeviceCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCodeInput by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.confirm_body, requesterIp, requesterId, requesterName),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (!showCodeInput) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAllowOnce) { Text(stringResource(R.string.confirm_once)) }
                    Button(onClick = onAllowAlways) { Text(stringResource(R.string.confirm_always)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCodeInput = true }) {
                        Text(stringResource(R.string.confirm_device_code))
                    }
                    OutlinedButton(onClick = onDeny) { Text(stringResource(R.string.confirm_deny)) }
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.control_device_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDeviceCode(code); code = "" }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                    OutlinedButton(onClick = { showCodeInput = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}
