package com.SoloSu.Crawler_tool.ui.pages

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.SoloSu.Crawler_tool.BuildConfig
import com.SoloSu.Crawler_tool.R
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.SoloSu.Crawler_tool.ui.components.Dimens
import com.SoloSu.Crawler_tool.ui.components.PageHeader
import com.SoloSu.Crawler_tool.ui.components.SurfaceCard
import com.SoloSu.Crawler_tool.viewmodel.SettingsUiState

/**
 * 设置页。
 *
 * 分组顺序按「改完立刻能感知」排列:外观 → 网络 → 抓取 → 其它。
 * 每个分组是独立卡片,组内条目共用同一行高与左右内边距,
 * 文字基线因此天然对齐,不依赖各条目的 padding 各自调。
 */
@Composable
fun SettingsPage(
    state: SettingsUiState,
    scrollState: ScrollState,
    onThemeChange: (String) -> Unit,
    onMonetChange: (Boolean) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onConnectTimeoutChange: (Long) -> Unit,
    onReadTimeoutChange: (Long) -> Unit,
    onProxyEnabledChange: (Boolean) -> Unit,
    onProxyHostChange: (String) -> Unit,
    onProxyPortChange: (Int) -> Unit,
    onJsRenderChange: (Boolean) -> Unit,
    onJsRenderDelayChange: (Int) -> Unit,
    onBatchDelayChange: (Long) -> Unit,
    onBatchRetryChange: (Int) -> Unit,
    onHeadersEnabledChange: (Boolean) -> Unit,
    onAddHeader: () -> Unit,
    onUpdateHeader: (Int, String, String) -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onOpenXpathRules: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = Dimens.PageBottom),
    ) {
        Spacer(Modifier.height(Dimens.HeaderGap))
        PageHeader(title = "设置", subtitle = "主题、网络与抓取行为")

        // ─── 外观 ───
        SectionLabelText("外观")
        Column(modifier = Modifier.padding(horizontal = Dimens.PageHorizontal)) {
            SurfaceCard {
                ThemeOption("跟随系统", SettingsRepository.THEME_SYSTEM, state.theme, onThemeChange)
                ThemeOption("浅色", SettingsRepository.THEME_LIGHT, state.theme, onThemeChange)
                ThemeOption("深色", SettingsRepository.THEME_DARK, state.theme, onThemeChange)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                SwitchRow(
                    title = "莫奈取色",
                    summary = if (supportsMonet) "从壁纸提取配色"
                    else "当前系统不支持壁纸取色(需 Android 12 及以上)",
                    checked = state.monetEnabled && supportsMonet,
                    enabled = supportsMonet,
                    onCheckedChange = onMonetChange,
                )
            }
        }

        // ─── 网络 ───
        SectionLabelText("网络")
        Column(
            modifier = Modifier.padding(horizontal = Dimens.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        ) {
            SurfaceCard {
                Text("User-Agent", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Chrome" to UA_CHROME,
                        "Edge" to UA_EDGE,
                        "iPhone" to UA_IPHONE,
                    ).forEach { (label, ua) ->
                        AssistChip(
                            onClick = { onUserAgentChange(ua) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (state.userAgent == ua)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            border = null,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumberField(
                    label = "User-Agent 字符串",
                    value = state.userAgent,
                    onValueChange = onUserAgentChange,
                )
            }

            SurfaceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "连接超时(秒)",
                        value = state.connectTimeout.toString(),
                        onValueChange = { onConnectTimeoutChange(it.toLongOrNull() ?: 15L) },
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "读取超时(秒)",
                        value = state.readTimeout.toString(),
                        onValueChange = { onReadTimeoutChange(it.toLongOrNull() ?: 30L) },
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SurfaceCard {
                SwitchRow(
                    title = "启用代理",
                    summary = "HTTP 代理,仅影响抓取请求",
                    checked = state.proxyEnabled,
                    onCheckedChange = onProxyEnabledChange,
                )
                if (state.proxyEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "主机",
                            value = state.proxyHost,
                            onValueChange = onProxyHostChange,
                            modifier = Modifier.weight(2f),
                        )
                        NumberField(
                            label = "端口",
                            value = state.proxyPort.toString(),
                            onValueChange = { onProxyPortChange(it.toIntOrNull() ?: 8080) },
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SurfaceCard {
                SwitchRow(
                    title = "自定义请求头",
                    summary = "已设置 ${state.headerCount} 个",
                    checked = state.headersEnabled,
                    onCheckedChange = onHeadersEnabledChange,
                )
                if (state.headersEnabled) {
                    Spacer(Modifier.height(8.dp))
                    state.headers.forEachIndexed { index, header ->
                        HeaderRow(
                            index = index,
                            key = header.key,
                            value = header.value,
                            onCommit = { k, v -> onUpdateHeader(index, k, v) },
                            onRemove = { onRemoveHeader(index) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onAddHeader,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("添加请求头") }
                }
            }
        }

        // ─── 抓取 ───
        SectionLabelText("抓取")
        Column(
            modifier = Modifier.padding(horizontal = Dimens.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        ) {
            SurfaceCard {
                StepperRow(
                    title = "批量请求间隔",
                    valueText = delayLabel(state.batchDelay),
                    options = DELAY_OPTIONS.map { delayLabel(it) },
                    selectedIndex = DELAY_OPTIONS.indexOf(state.batchDelay).coerceAtLeast(0),
                    onSelect = { onBatchDelayChange(DELAY_OPTIONS[it]) },
                )
                Spacer(Modifier.height(12.dp))
                StepperRow(
                    title = "失败重试次数",
                    valueText = "${state.batchRetry} 次",
                    options = (0..5).map { "$it 次" },
                    selectedIndex = state.batchRetry.coerceIn(0, 5),
                    onSelect = { onBatchRetryChange(it) },
                )
            }

            SurfaceCard {
                SwitchRow(
                    title = "JavaScript 渲染",
                    summary = "用 WebView 渲染 SPA 后再提取",
                    checked = state.jsRenderEnabled,
                    onCheckedChange = onJsRenderChange,
                )
                if (state.jsRenderEnabled) {
                    Spacer(Modifier.height(12.dp))
                    StepperRow(
                        title = "渲染等待时间",
                        valueText = "${state.jsRenderDelay} 秒",
                        options = (1..10).map { "$it 秒" },
                        selectedIndex = state.jsRenderDelay.coerceIn(1, 10) - 1,
                        onSelect = { onJsRenderDelayChange(it + 1) },
                    )
                }
            }
        }

        // ─── 其它 ───
        SectionLabelText("其它")
        Column(
            modifier = Modifier.padding(horizontal = Dimens.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        ) {
            SurfaceCard {
                ClickableRow(
                    iconRes = R.drawable.ic_layers,
                    title = "XPath 语法规则",
                    summary = "离线速查手册",
                    onClick = onOpenXpathRules,
                )
                Spacer(Modifier.height(6.dp))
                ClickableRow(
                    iconRes = R.drawable.ic_info,
                    title = "关于",
                    summary = "版本 ${BuildConfig.VERSION_NAME}",
                    onClick = onShowAbout,
                )
            }
        }
    }
}

private val DELAY_OPTIONS = listOf(0L, 500L, 1000L, 2000L, 5000L)

private fun delayLabel(ms: Long): String = when (ms) {
    0L -> "无延迟"
    500L -> "0.5 秒"
    1000L -> "1 秒"
    2000L -> "2 秒"
    5000L -> "5 秒"
    else -> "${ms}ms"
}

@Composable
private fun SectionLabelText(text: String) {
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

/** 单选项行高固定 48dp,三项叠在一起时文字基线严格对齐。 */
@Composable
private fun ThemeOption(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ClickableRow(
    iconRes: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("查看") }
    }
}

/**
 * 输入行。
 *
 * 文本在本地状态里编辑,只在失焦时提交:
 * 一是避免每敲一个字符就写一次 SharedPreferences,
 * 二是避免父状态立即回灌导致光标跳动或吞字。
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    var hadFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    onValueChange(text)
                }
            },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
    )
}

@Composable
private fun HeaderRow(
    index: Int,
    key: String,
    value: String,
    onCommit: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    // key 用 index:条目在列表中的位置稳定,父状态回灌不会重置输入
    var k by rememberSaveable(index) { mutableStateOf(key) }
    var v by rememberSaveable(index) { mutableStateOf(value) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = k,
            onValueChange = { k = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) onCommit(k, v) },
            label = { Text("名称", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            modifier = Modifier
                .weight(1.4f)
                .onFocusChanged { if (!it.isFocused) onCommit(k, v) },
            label = { Text("值", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_clear),
                contentDescription = "删除该请求头",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 选项较少时用横向胶囊代替滑杆:点击即选,没有拖拽的精度问题。 */
@Composable
private fun StepperRow(
    title: String,
    valueText: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                AssistChip(
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    border = null,
                )
            }
        }
    }
}

private const val UA_CHROME =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
private const val UA_EDGE =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.6778.135 Safari/537.36 Edg/131.0.2903.70"
private const val UA_IPHONE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
