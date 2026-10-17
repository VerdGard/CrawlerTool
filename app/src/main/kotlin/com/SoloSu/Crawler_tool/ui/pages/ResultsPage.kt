package com.SoloSu.Crawler_tool.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.R
import com.SoloSu.Crawler_tool.ResultItem
import com.SoloSu.Crawler_tool.ui.components.Dimens
import com.SoloSu.Crawler_tool.ui.components.EmptyState
import com.SoloSu.Crawler_tool.ui.components.PageHeader
import com.SoloSu.Crawler_tool.ui.components.ResultCard
import com.SoloSu.Crawler_tool.viewmodel.MainUiState

/**
 * 结果页。
 *
 * 布局固定为「页头 / 列表 / 分页条」三段,列表独占剩余高度并内部滚动:
 * 页头与分页条不随滚动移动,条数很多时也不会把操作挤走。
 */
@Composable
fun ResultsPage(
    state: MainUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onExport: () -> Unit,
    onCopyAll: () -> Unit,
    onShowStructure: () -> Unit,
    onClear: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onCopyItem: (ResultItem) -> Unit,
    onItemLongPress: (ResultItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 展开状态按全局序号记录,跨分页、跨滚动都保持一致
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    val hasResults = state.allResults.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(Dimens.HeaderGap))
        PageHeader(
            title = "结果",
            subtitle = if (hasResults) resultSummary(state) else "长按单条可查看结构树",
        )

        ActionRow(
            enabled = hasResults,
            structureEnabled = state.rawHtml.isNotBlank(),
            onExport = onExport,
            onCopyAll = onCopyAll,
            onShowStructure = onShowStructure,
            onClear = onClear,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.results.isEmpty()) {
                EmptyState(
                    icon = painterResource(R.drawable.ic_data_object),
                    title = if (state.isLoading) "正在抓取..." else "还没有结果",
                    hint = if (state.isLoading) "请稍候" else "到「抓取」页输入网址与表达式",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.PageHorizontal,
                        end = Dimens.PageHorizontal,
                        top = Dimens.ItemGap,
                        bottom = Dimens.ItemGap,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results, key = { it.index }) { item ->
                        ResultCard(
                            item = item,
                            expanded = expanded[item.index] == true,
                            onToggleExpand = {
                                expanded[item.index] = expanded[item.index] != true
                            },
                            onCopy = { onCopyItem(item) },
                            onLongClick = { onItemLongPress(item) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.totalPages > 1,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PaginationBar(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onPrev = onPrevPage,
                onNext = onNextPage,
            )
        }
    }
}

@Composable
private fun ActionRow(
    enabled: Boolean,
    structureEnabled: Boolean,
    onExport: () -> Unit,
    onCopyAll: () -> Unit,
    onShowStructure: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PageHorizontal, vertical = Dimens.ItemGap),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExport, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_export),
                contentDescription = "导出",
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onCopyAll, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = "复制全部",
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onShowStructure, enabled = structureEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_tree),
                contentDescription = "查看结构树",
                tint = if (structureEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear, enabled = enabled) {
            Text("清空")
        }
    }
}

/** 分页条:高度固定 52dp,空页时也不改变整体布局。 */
@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = Dimens.PageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onPrev, enabled = currentPage > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "上一页",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Text(
                text = "${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
            IconButton(onClick = onNext, enabled = currentPage < totalPages - 1) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "下一页",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun resultSummary(state: MainUiState): String = when {
    state.totalPages > 1 ->
        "共 ${state.allResults.size} 条 · 第 ${state.currentPage + 1}/${state.totalPages} 页"
    else -> "共 ${state.allResults.size} 条"
}
