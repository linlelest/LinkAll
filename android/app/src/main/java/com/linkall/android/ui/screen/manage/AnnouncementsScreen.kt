package com.linkall.android.ui.screen.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linkall.android.R
import com.linkall.android.ui.components.EmptyView
import com.linkall.android.ui.components.LoadingView
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.NavigationState
import io.noties.markwon.Markwon

/**
 * 公告中心：下拉刷新列表，MD 渲染
 */
@Composable
fun AnnouncementsScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.announcements.state.collectAsState()

    LaunchedEffect(Unit) { vm.announcements.refresh() }

    Scaffold(
        topBar = {
            ScreenHeader(title = stringResource(R.string.announcements_title)) {
                IconButton(onClick = { nav.navigateManage(com.linkall.android.ui.navigation.ManageScreen.DASHBOARD) }) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back))
                }
                IconButton(onClick = { vm.announcements.refresh() }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.common_refresh))
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(Modifier.padding(padding))
            state.items.isEmpty() && state.error == null ->
                EmptyView(stringResource(R.string.announcements_empty), Modifier.padding(padding))
            state.error != null ->
                com.linkall.android.ui.components.ErrorView(state.error!!, { vm.announcements.refresh() }, Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding)) {
                items(state.items) { item ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (item.pinned) Text(stringResource(R.string.announcements_pinned), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.announcements_published_at, item.publishedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            // TODO: 使用 Markwon 渲染 Markdown（content）
                            Text(item.content, style = MaterialTheme.typography.bodySmall, maxLines = 6)
                        }
                    }
                }
            }
        }
    }
}
