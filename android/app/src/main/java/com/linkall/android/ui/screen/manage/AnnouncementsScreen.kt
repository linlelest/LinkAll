package com.linkall.android.ui.screen.manage

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkall.android.R
import com.linkall.android.data.model.Announcement
import com.linkall.android.ui.components.EmptyView
import com.linkall.android.ui.components.LoadingView
import com.linkall.android.ui.components.ScreenHeader
import com.linkall.android.ui.navigation.NavigationState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 公告中心：下拉刷新列表，Markwon 渲染 Markdown 正文，显示 Ed25519 签名状态
 */
@Composable
fun AnnouncementsScreen(
    vm: ManageViewModel,
    nav: NavigationState,
    modifier: Modifier = Modifier
) {
    val state by vm.announcements.state.collectAsState()
    val context = LocalContext.current

    // Markwon 实例（带表格、删除线插件）
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }

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
                    AnnouncementCard(
                        item = item,
                        markwon = markwon,
                        onMarkRead = { vm.announcements.markRead(item.id) }
                    )
                }
            }
        }
    }
}

/**
 * 单条公告卡片：标题、时间、平台标签、Markdown 正文、签名徽章
 */
@Composable
private fun AnnouncementCard(
    item: Announcement,
    markwon: Markwon,
    onMarkRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行：标题 + 置顶标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (item.pinned) {
                    Spacer(Modifier.size(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.announcements_pinned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 元信息行：发布时间 + 平台 + 签名状态
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.announcements_published_at, formatAnnouncementDate(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.platform != "all") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = item.platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                if (item.signature.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x1F16A34A)
                    ) {
                        Text(
                            text = "✓ ${stringResource(R.string.announcements_signed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Markdown 正文渲染（使用 Markwon + AndroidView/TextView）
            if (item.contentMd.isNotEmpty()) {
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            // 允许链接点击
                            movementMethod = LinkMovementMethod.getInstance()
                            textSize = 13f
                            setTextColor(ctx.getColor(android.R.color.tab_indicator_text))
                        }
                    },
                    update = { tv ->
                        markwon.setMarkdown(tv, item.contentMd)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 格式化 Unix 时间戳（秒）为本地日期字符串
 */
private fun formatAnnouncementDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampSeconds * 1000))
}
