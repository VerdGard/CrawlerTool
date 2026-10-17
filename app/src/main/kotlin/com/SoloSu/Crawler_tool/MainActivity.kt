package com.SoloSu.Crawler_tool

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.SoloSu.Crawler_tool.ui.dialogs.AboutDialog
import com.SoloSu.Crawler_tool.ui.dialogs.BatchSheet
import com.SoloSu.Crawler_tool.ui.dialogs.ConfirmDialog
import com.SoloSu.Crawler_tool.ui.dialogs.ExportChooserDialog
import com.SoloSu.Crawler_tool.ui.pages.FetchPage
import com.SoloSu.Crawler_tool.ui.pages.HistoryPage
import com.SoloSu.Crawler_tool.ui.pages.ResultsPage
import com.SoloSu.Crawler_tool.ui.pages.SettingsPage
import com.SoloSu.Crawler_tool.ui.theme.CrawlerTheme
import com.SoloSu.Crawler_tool.viewmodel.MainEvent
import com.SoloSu.Crawler_tool.viewmodel.MainViewModel
import com.SoloSu.Crawler_tool.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 主界面。
 *
 * 整棵树由 Compose 驱动,唯一的 Android View 是 [BaseActivity] 提供的窗口。
 * 四个页面放在 [AnimatedContent] 里做交叉淡入淡出,并用
 * [rememberSaveableStateHolder] 保住各页的滚动位置与本地输入状态。
 */
class MainActivity : BaseActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 画面延伸到状态栏/导航栏,insets 由 Scaffold 统一处理,避免二次留白
        enableEdgeToEdge()
        NotificationHelper.createChannel(this)

        val mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setContent {
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (settingsState.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            CrawlerTheme(
                darkTheme = darkTheme,
                dynamicColor = settingsState.monetEnabled,
            ) {
                CrawlerApp(
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    onShowStructure = { html ->
                        HtmlTreeDialog.show(this, html)
                    },
                    onOpenXpathRules = {
                        startActivity(
                            android.content.Intent(this, XPathRulesActivity::class.java)
                        )
                    },
                    onRequestNotificationPermission = {
                        requestNotificationPermissionIfNeeded()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回(或主题重建后)需要重新读一次偏好
        ViewModelProvider(this)[SettingsViewModel::class.java].reload()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}

private enum class Page(val label: String, val iconRes: Int) {
    FETCH("抓取", R.drawable.ic_nav_fetch),
    RESULTS("结果", R.drawable.ic_nav_results),
    HISTORY("历史", R.drawable.ic_nav_history),
    SETTINGS("设置", R.drawable.ic_nav_settings),
}

@Composable
private fun CrawlerApp(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onShowStructure: (String) -> Unit,
    onOpenXpathRules: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentPage by rememberSaveable { mutableStateOf(Page.FETCH.name) }
    val page = Page.valueOf(currentPage)

    // 各页的滚动位置与本地输入状态靠它跨切页保留
    val stateHolder = rememberSaveableStateHolder()

    var showBatch by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // 一次性消息:Snackbar 排队显示,不覆盖也不丢失
    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            when (event) {
                is MainEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is MainEvent.OpenStructure -> onShowStructure(event.html)
            }
        }
    }

    LaunchedEffect(Unit) {
        onRequestNotificationPermission()
    }

    // 结果与历史只在对应页可见时才读,避免无谓的状态读取
    LaunchedEffect(page) {
        if (page == Page.HISTORY) mainViewModel.refreshHistory()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Page.entries.forEach { target ->
                    NavigationBarItem(
                        selected = page == target,
                        onClick = { currentPage = target.name },
                        icon = {
                            Icon(
                                painter = painterResource(target.iconRes),
                                contentDescription = target.label,
                            )
                        },
                        label = {
                            Text(
                                text = target.label,
                                fontWeight = if (page == target) FontWeight.Medium
                                else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface,
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "page",
            ) { target ->
                stateHolder.SaveableStateProvider(target.name) {
                    when (target) {
                        Page.FETCH -> FetchPage(
                            state = state,
                            scrollState = androidx.compose.foundation.rememberScrollState(),
                            onUrlChange = mainViewModel::updateUrl,
                            onExpressionChange = mainViewModel::updateExpression,
                            onModeChange = mainViewModel::updateMatchMode,
                            onMatch = mainViewModel::performMatch,
                            onBatch = { showBatch = true },
                            onCopyRule = mainViewModel::copyExpression,
                            onReset = mainViewModel::resetInputs,
                            onPasteUrl = { pasteUrl(context, mainViewModel, scope, snackbarHostState) },
                        )

                        Page.RESULTS -> ResultsPage(
                            state = state,
                            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                            onExport = mainViewModel::requestExport,
                            onCopyAll = mainViewModel::copyAllResults,
                            onShowStructure = { mainViewModel.showStructure() },
                            onClear = mainViewModel::clearResults,
                            onPrevPage = mainViewModel::prevPage,
                            onNextPage = mainViewModel::nextPage,
                            onCopyItem = mainViewModel::copyOneResult,
                            // 单条内容本身是 HTML 就展开这一条,否则回退到整页快照
                            onItemLongPress = { item ->
                                val looksLikeHtml =
                                    item.content.contains("<") && item.content.contains(">")
                                mainViewModel.showStructure(
                                    item.content.takeIf { looksLikeHtml }
                                )
                            },
                        )

                        Page.HISTORY -> HistoryPage(
                            items = state.history,
                            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                            onClick = { item ->
                                mainViewModel.loadHistoryItem(item)
                                currentPage = Page.FETCH.name
                                scope.launch {
                                    snackbarHostState.showSnackbar("已回填历史记录")
                                }
                            },
                            onDelete = mainViewModel::deleteHistoryItem,
                            onClearAll = { showClearHistory = true },
                        )

                        Page.SETTINGS -> SettingsPage(
                            state = settingsState,
                            scrollState = androidx.compose.foundation.rememberScrollState(),
                            onThemeChange = settingsViewModel::updateTheme,
                            onMonetChange = settingsViewModel::updateMonetEnabled,
                            onUserAgentChange = settingsViewModel::updateUserAgent,
                            onConnectTimeoutChange = settingsViewModel::updateConnectTimeout,
                            onReadTimeoutChange = settingsViewModel::updateReadTimeout,
                            onProxyEnabledChange = settingsViewModel::updateProxyEnabled,
                            onProxyHostChange = settingsViewModel::updateProxyHost,
                            onProxyPortChange = settingsViewModel::updateProxyPort,
                            onJsRenderChange = settingsViewModel::updateJsRenderEnabled,
                            onJsRenderDelayChange = settingsViewModel::updateJsRenderDelay,
                            onBatchDelayChange = settingsViewModel::updateBatchDelay,
                            onBatchRetryChange = settingsViewModel::updateBatchRetry,
                            onHeadersEnabledChange = settingsViewModel::setHeadersEnabled,
                            onAddHeader = settingsViewModel::addHeader,
                            onUpdateHeader = { index, key, value ->
                                settingsViewModel.updateHeader(
                                    index,
                                    HeaderManager.HeaderEntry(key, value),
                                )
                            },
                            onRemoveHeader = settingsViewModel::removeHeader,
                            onOpenXpathRules = onOpenXpathRules,
                            onShowAbout = { showAbout = true },
                        )
                    }
                }
            }
        }
    }

    // ─── 弹层 ───
    if (showBatch) {
        val delay = settingsState.batchDelay
        val retry = settingsState.batchRetry
        BatchSheet(
            initialPattern = state.url.takeIf { it.isNotBlank() }
                ?.let { if (it.contains("{page}")) it else "$it?page={page}" }
                .orEmpty(),
            hint = if (delay > 0) "间隔 ${delay}ms,失败重试 $retry 次"
            else "无间隔,失败重试 $retry 次",
            onDismiss = { showBatch = false },
            onStart = { pattern, start, end ->
                showBatch = false
                mainViewModel.performBatchMatch(pattern, start, end)
                currentPage = Page.RESULTS.name
            },
        )
    }

    state.exportFormats?.let { formats ->
        ExportChooserDialog(
            formats = formats,
            onPick = { mainViewModel.exportResults(it) },
            onDismiss = mainViewModel::dismissExport,
        )
    }

    if (showClearHistory) {
        ConfirmDialog(
            title = "清空历史",
            message = "将删除全部 ${state.history.size} 条历史记录,无法恢复。",
            confirmText = "清空",
            onConfirm = {
                showClearHistory = false
                mainViewModel.clearHistory()
                scope.launch { snackbarHostState.showSnackbar("历史记录已清空") }
            },
            onDismiss = { showClearHistory = false },
        )
    }

    if (showAbout) {
        AboutDialog(
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showAbout = false },
        )
    }
}

/** 从剪贴板粘贴 URL,只在看起来像 URL 时才写入。 */
private fun pasteUrl(
    context: Context,
    viewModel: MainViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState,
) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = cb?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
    if (clip.startsWith("http://") || clip.startsWith("https://")) {
        viewModel.setUrlExternally(clip)
        scope.launch { snackbar.showSnackbar("已粘贴 URL") }
    } else {
        scope.launch { snackbar.showSnackbar("剪贴板中没有有效 URL") }
    }
}
