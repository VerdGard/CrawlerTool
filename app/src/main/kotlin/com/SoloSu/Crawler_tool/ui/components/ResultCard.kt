package com.SoloSu.Crawler_tool.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.R
import com.SoloSu.Crawler_tool.ResultItem

/**
 * 单条结果卡片。
 *
 * 展开状态由调用方按 index 持有,卡片本身无状态,
 * 因此滚动出屏再回来不会丢掉已展开的内容。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultCard(
    item: ResultItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 摘要与是否可折叠都只依赖内容本身,跟着 item 一起缓存,滚动时不重算
    val attrTags = remember(item.content) { extractAttrTags(item.content) }
    val collapsible = remember(item.content) { estimateLineCount(item.content) > COLLAPSED_LINES }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = item.index.toString())
                Spacer(Modifier.width(8.dp))
                if (attrTags.isNotEmpty()) {
                    Text(
                        text = attrTags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "复制本条",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SyntaxCodeBlock(
                content = item.content,
                expanded = expanded,
                collapsible = collapsible,
                onToggle = onToggleExpand,
            )
        }
    }
}

private const val COLLAPSED_LINES = 3

/** 卡片之间的统一垂直间距。 */
val ResultCardSpacing = 8.dp
