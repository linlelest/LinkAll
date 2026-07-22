package com.linkall.android.ui.screen.manage

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.linkall.android.R
import com.linkall.android.BuildConfig
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.ManageScreen
import com.linkall.android.ui.navigation.NavigationState
import java.io.File
import java.net.URL

/**
 * OTA 更新：后台静默检查，前台弹窗下载，引导系统安装器
 */
@Composable
fun OtaScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.ota.state.collectAsState()
    val context = LocalContext.current
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { vm.ota.checkUpdate(BuildConfig.VERSION_NAME) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = stringResource(R.string.ota_title)) {
            IconButton(onClick = { nav.navigateManage(ManageScreen.DASHBOARD) }) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        }

        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${stringResource(R.string.ota_current_version)}: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                state.latest?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("${stringResource(R.string.ota_latest_version)}: ${it.version}", style = MaterialTheme.typography.bodyMedium)
                    if (state.hasUpdate) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ota_update_available, it.version), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ota_up_to_date), color = MaterialTheme.colorScheme.primary)
                    }
                    if (it.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ota_release_notes), style = MaterialTheme.typography.titleSmall)
                        Text(it.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.forceUpdate) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ota_force_update), color = MaterialTheme.colorScheme.error)
                    }
                } ?: Text(stringResource(R.string.ota_checking), style = MaterialTheme.typography.bodyMedium)
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (state.hasUpdate) {
                    Spacer(Modifier.height(16.dp))
                    if (downloadProgress > 0f && downloadProgress < 1f) {
                        Text(stringResource(R.string.ota_downloading, (downloadProgress * 100).toInt()))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    } else if (downloadProgress >= 1f) {
                        Text(stringResource(R.string.ota_download_complete))
                        Button(onClick = { /* TODO: 触发 APK 安装器 */ }) {
                            Text(stringResource(R.string.ota_install_now))
                        }
                    } else {
                        Button(onClick = {
                            // TODO: 实际下载逻辑（OkHttp 下载到 cache，调起安装器）
                            downloadProgress = 1f
                        }) { Text(stringResource(R.string.ota_download)) }
                    }
                }
            }
        }
    }
}
