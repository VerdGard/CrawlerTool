package com.SoloSu.Crawler_tool.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SoloSu.Crawler_tool.CrawlerEngine
import com.SoloSu.Crawler_tool.R
import com.SoloSu.Crawler_tool.ui.components.Dimens
import com.SoloSu.Crawler_tool.ui.components.PageHeader
import com.SoloSu.Crawler_tool.viewmodel.MainUiState

/**
 * 抓取页。
 *
 * 输入框持有本地状态,只在 [MainUiState.inputRevision] 变化时(历史回填 / 重置)
 * 才从状态回写,因此逐字输入只重组输入框本身,不会带动整页。
 */
@Composable
fun FetchPage(
    state: MainUiState,
    scrollState: androidx.compose.foundation.ScrollState,
    onUrlChange: (String) -> Unit,
    onExpressionChange: (String) -> Unit,
    onModeChange: (CrawlerEngine.MatchMode) -> Unit,
    onMatch: () -> Unit,
    onBatch: () -> Unit,
    onCopyRule: () -> Unit,
    onReset: () -> Unit,
    onPasteUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlText by rememberSaveable(state.inputRevision) { mutableStateOf(state.url) }
    var exprText by rememberSaveable(state.inputRevision) { mutableStateOf(state.expression) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(bottom = Dimens.PageBottom),
    ) {
        Spacer(Modifier.height(Dimens.HeaderGap))
        PageHeader(
            title = "抓取",
            subtitle = "填入网址与表达式,一次拿到结构化结果",
        )

        SectionTitle("目标")
        OutlinedTextField(
            value = urlText,
            onValueChange = {
                urlText = it
                onUrlChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal),
            placeholder = { Text("输入网站 URL...") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                IconButton(onClick = onPasteUrl) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paste),
                        contentDescription = "从剪贴板粘贴",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        SectionTitle("匹配表达式")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip("XPath", state.matchMode == CrawlerEngine.MatchMode.XPath) {
                onModeChange(CrawlerEngine.MatchMode.XPath)
            }
            ModeChip("CSS", state.matchMode == CrawlerEngine.MatchMode.CssSelector) {
                onModeChange(CrawlerEngine.MatchMode.CssSelector)
            }
            ModeChip("正则", state.matchMode == CrawlerEngine.MatchMode.Regex) {
                onModeChange(CrawlerEngine.MatchMode.Regex)
            }
        }

        Spacer(Modifier.height(Dimens.ItemGap))
        OutlinedTextField(
            value = exprText,
            onValueChange = {
                exprText = it
                onExpressionChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal),
            placeholder = { Text(modeHint(state.matchMode)) },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            minLines = 2,
            maxLines = 5,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onMatch() }),
        )

        SectionTitle("快捷插入")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickSnippets(state.matchMode).forEach { snippet ->
                AssistChip(
                    onClick = {
                        exprText = exprText + snippet
                        onExpressionChange(exprText)
                    },
                    label = { Text(snippet, style = MaterialTheme.typography.labelMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = null,
                )
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))
        Button(
            onClick = onMatch,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal)
                .height(54.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("匹配中...", fontWeight = FontWeight.Medium)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("开始匹配", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(Dimens.ItemGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PageHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onBatch,
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) { Text("批量") }
            OutlinedButton(
                onClick = onCopyRule,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) { Text("复制规则") }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) { Text("重置") }
        }

        Spacer(Modifier.height(Dimens.SectionGap))
        StatusStrip(
            text = state.statusText.ifBlank { "尚未开始抓取" },
            loading = state.isLoading,
        )
        Spacer(Modifier.height(Dimens.PageBottom))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimens.PageHorizontal,
            end = Dimens.PageHorizontal,
            top = Dimens.SectionGap,
            bottom = Dimens.ItemGap,
        ),
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) },
    )
}

/** 状态条:固定高度,加载与完成都不改变页面结构,避免内容跳动。 */
@Composable
private fun StatusStrip(text: String, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PageHorizontal)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            Row {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun modeHint(mode: CrawlerEngine.MatchMode): String = when (mode) {
    CrawlerEngine.MatchMode.CssSelector -> "CSS 选择器(如 a.title)"
    CrawlerEngine.MatchMode.Regex -> "正则表达式(如 <h2>(.*?)</h2>)"
    else -> "XPath 表达式(如 //a/@href)"
}

private fun quickSnippets(mode: CrawlerEngine.MatchMode): List<String> = when (mode) {
    CrawlerEngine.MatchMode.CssSelector ->
        listOf("[title]", "[href]", "[data-original]", "[data-src]", ":contains(文本)")
    CrawlerEngine.MatchMode.Regex ->
        listOf("[^<]+?", "https?://[^\"']+", "[^\"']+", "[^\"']+", ".*?")
    else -> listOf("@title", "@href", "@data-original", "@data-src", "text()")
}
