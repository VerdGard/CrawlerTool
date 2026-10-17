package com.SoloSu.Crawler_tool.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.HistoryManager
import com.SoloSu.Crawler_tool.R
import com.SoloSu.Crawler_tool.ui.components.Dimens
import com.SoloSu.Crawler_tool.ui.components.EmptyState
import com.SoloSu.Crawler_tool.ui.components.PageHeader
import com.SoloSu.Crawler_tool.ui.components.Pill
import java.util.concurrent.TimeUnit

/**
 * 历史页。
 *
 * 与结果页保持同一套骨架:页头(含条数与清空) + 滚动列表 / 空状态,
 * 两个页面切换时首屏元素位置一致,不会产生「跳一下」的观感。
 */
@Composable
fun HistoryPage(
    items: List<HistoryManager.HistoryItem>,
    listState: LazyListState,
    onClick: (HistoryManager.HistoryItem) -> Unit,
    onDelete: (HistoryManager.HistoryItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(Dimens.HeaderGap))
        PageHeader(
            title = "历史",
            subtitle = if (items.isEmpty()) "点击任意一条即可回填到抓取页"
            else "共 ${items.size} 条 · 点击任意一条即可回填",
            trailing = {
                TextButton(onClick = onClearAll, enabled = items.isNotEmpty()) {
                    Text("清空")
                }
            },
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = painterResource(R.drawable.ic_history),
                    title = "暂无历史记录",
                    hint = "每次成功抓取后会自动记录",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Dimens.PageHorizontal,
                    end = Dimens.PageHorizontal,
                    top = Dimens.ItemGap,
                    bottom = Dimens.PageBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    HistoryCard(
                        item = item,
                        onClick = { onClick(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryManager.HistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = modeLabel(item.mode),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${item.resultCount} 条 · ${relativeTime(item.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear),
                        contentDescription = "删除这条记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.expression,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 存储里的 mode 是枚举 toString(),这里折成界面上惯用的短名。 */
private fun modeLabel(mode: String): String = when (mode) {
    "CssSelector" -> "CSS"
    "Regex" -> "正则"
    else -> "XPath"
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "刚刚"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        else -> "${days} 天前"
    }
}
