package com.SoloSu.Crawler_tool.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.SoloSu.Crawler_tool.CrawlerEngine
import com.SoloSu.Crawler_tool.HistoryManager
import com.SoloSu.Crawler_tool.ResultItem
import com.SoloSu.Crawler_tool.repository.CrawlerRepository
import com.SoloSu.Crawler_tool.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 UI 状态。
 *
 * 全部字段不可变,Compose 通过 [StateFlow] 订阅:
 * 结构相等的数据不会触发重组,MutableLiveData 则每次 setValue 都会派发。
 */
data class MainUiState(
    val url: String = "",
    val expression: String = "",
    val matchMode: CrawlerEngine.MatchMode = CrawlerEngine.MatchMode.XPath,
    /** 当前页展示的切片 */
    val results: List<ResultItem> = emptyList(),
    /** 全量匹配结果,分页与导出都以它为准 */
    val allResults: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    /** 最近一次抓取到的 HTML,供结构树查看 */
    val rawHtml: String = "",
    val statusText: String = "",
    val isLoading: Boolean = false,
    /** 历史页数据,由 [MainViewModel.refreshHistory] 填充 */
    val history: List<HistoryManager.HistoryItem> = emptyList(),
    /** 非空时弹出导出格式选择 */
    val exportFormats: List<String>? = null,
    /**
     * 输入区的「外部改写」版本号。
     *
     * 输入框在 Compose 侧持有自己的本地状态(每次按键只重组输入框自身),
     * 只有历史回填 / 重置这类外部改写才需要回写,靠这个计数触发一次同步,
     * 避免把 url/expression 提升到顶层状态导致每敲一个字就重组整页。
     */
    val inputRevision: Int = 0,
) {
    val hasResults: Boolean get() = allResults.isNotEmpty()
    /** 结果区为空且不处于加载中时,显示空状态引导 */
    val showEmptyState: Boolean get() = results.isEmpty() && !isLoading
}

sealed class MainEvent {
    data class Message(val text: String) : MainEvent()

    /** 打开 HTML 结构树。对话框由宿主 Activity 弹出,状态里不留瞬时数据。 */
    data class OpenStructure(val html: String) : MainEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val crawlerRepo = CrawlerRepository(application)
    private val historyRepo = HistoryRepository(application)
    private val pageSize = 20

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** 一次性提示。Channel 保证只消费一次,不会在重组后重复弹出。 */
    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ─── 输入同步 ─────────────────────────────────────────────

    fun updateUrl(url: String) {
        _state.update { it.copy(url = url) }
    }

    fun updateExpression(expr: String) {
        _state.update { it.copy(expression = expr) }
    }

    /**
     * 外部写入 URL(如从剪贴板粘贴)。
     *
     * 与 [updateUrl] 的区别在于递增 [MainUiState.inputRevision]:
     * 抓取页的输入框持有本地状态,只有版本号变化才会从状态回写。
     */
    fun setUrlExternally(url: String) {
        _state.update { it.copy(url = url, inputRevision = it.inputRevision + 1) }
    }

    fun updateMatchMode(mode: CrawlerEngine.MatchMode) {
        _state.update { it.copy(matchMode = mode) }
    }

    /** 从历史记录回填,同时重置结果区,避免旧结果与新表达式错配。 */
    fun loadHistoryItem(item: HistoryManager.HistoryItem) {
        val mode = when (item.mode) {
            "CssSelector" -> CrawlerEngine.MatchMode.CssSelector
            "Regex" -> CrawlerEngine.MatchMode.Regex
            else -> CrawlerEngine.MatchMode.XPath
        }
        _state.update {
            it.copy(
                url = item.url,
                expression = item.expression,
                matchMode = mode,
                inputRevision = it.inputRevision + 1,
            ).clearedResults()
        }
    }

    // ─── 匹配 ────────────────────────────────────────────────

    fun performMatch() {
        val s = _state.value
        if (s.isLoading) return

        val url = s.url.trim()
        val expr = s.expression.trim()
        when {
            url.isBlank() || !url.contains(".") -> return emit("请输入有效的 URL")
            expr.isBlank() -> return emit("请输入匹配表达式")
        }

        val previousHtml = s.rawHtml
        _state.update { it.copy(isLoading = true, statusText = "正在请求并匹配...") }

        viewModelScope.launch {
            crawlerRepo.fetchAndMatch(url, expr, s.matchMode).fold(
                onSuccess = { page ->
                    val results = page.matches
                    _state.update {
                        it.copy(
                            // JS 渲染模式拿不到原始 HTML,此时保留上一次的快照
                            rawHtml = page.html.ifBlank { previousHtml },
                            allResults = results,
                            isLoading = false,
                            statusText = if (results.isEmpty()) "匹配完成,未找到结果"
                            else "匹配完成,共 ${results.size} 条结果",
                            currentPage = 0,
                            results = sliceOf(results, 0),
                            totalPages = pageCountOf(results),
                        )
                    }
                    if (results.isNotEmpty()) {
                        historyRepo.save(url, expr, s.matchMode.toString(), results.size)
                        refreshHistory()
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusText = "匹配失败",
                            results = emptyList(),
                            allResults = emptyList(),
                            totalPages = 0,
                        )
                    }
                    emit("匹配失败:${e.message?.lineSequence()?.firstOrNull() ?: "未知错误"}")
                }
            )
        }
    }

    fun performBatchMatch(urlPattern: String, startPage: Int, endPage: Int) {
        val s = _state.value
        if (s.isLoading) return
        val expr = s.expression.trim()
        if (expr.isBlank()) return emit("请先输入匹配表达式")
        if (urlPattern.isBlank() || !urlPattern.contains("{page}")) {
            return emit("URL 模板需包含 {page} 占位符")
        }
        if (endPage < startPage) return emit("结束页不能小于起始页")

        _state.update { it.copy(isLoading = true, statusText = "正在批量抓取...") }

        viewModelScope.launch {
            val result = crawlerRepo.fetchAndMatchBatch(
                urlPattern, expr, s.matchMode, startPage, endPage
            ) { progress ->
                val text = "正在抓取第 ${progress.page}/${progress.total} 页..."
                _state.update { if (it.statusText == text) it else it.copy(statusText = text) }
            }
            result.fold(
                onSuccess = { items ->
                    _state.update {
                        it.copy(
                            rawHtml = "",
                            allResults = items,
                            isLoading = false,
                            statusText = "批量抓取完成,共 ${items.size} 条结果",
                            currentPage = 0,
                            results = sliceOf(items, 0),
                            totalPages = pageCountOf(items),
                        )
                    }
                    if (items.isNotEmpty()) {
                        historyRepo.save(urlPattern, expr, s.matchMode.toString(), items.size)
                        refreshHistory()
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusText = "批量抓取失败",
                            results = emptyList(),
                            allResults = emptyList(),
                            totalPages = 0,
                        )
                    }
                    emit("批量抓取失败:${e.message?.lineSequence()?.firstOrNull() ?: "未知错误"}")
                }
            )
        }
    }

    /** 重置抓取页:清空输入与结果,但保留历史记录。 */
    fun resetInputs() {
        _state.update {
            it.copy(
                url = "",
                expression = "",
                matchMode = CrawlerEngine.MatchMode.XPath,
                inputRevision = it.inputRevision + 1,
                rawHtml = "",
            ).clearedResults()
        }
    }

    fun clearResults() {
        _state.update { it.clearedResults() }
    }

    // ─── 分页 ────────────────────────────────────────────────

    fun goToPage(page: Int) {
        val s = _state.value
        if (page < 0 || page >= s.totalPages || page == s.currentPage) return
        _state.update { it.copy(currentPage = page, results = sliceOf(it.allResults, page)) }
    }

    fun nextPage() = goToPage(_state.value.currentPage + 1)

    fun prevPage() = goToPage(_state.value.currentPage - 1)

    private fun pageCountOf(all: List<String>): Int =
        if (all.isEmpty()) 0 else (all.size + pageSize - 1) / pageSize

    /** 把全量结果切成一页,并带上全局序号。 */
    private fun sliceOf(all: List<String>, page: Int): List<ResultItem> {
        if (all.isEmpty()) return emptyList()
        val total = pageCountOf(all)
        val safe = page.coerceIn(0, total - 1)
        val start = safe * pageSize
        val end = minOf(start + pageSize, all.size)
        return all.subList(start, end).mapIndexed { i, content ->
            ResultItem(start + i + 1, content)
        }
    }

    private fun MainUiState.clearedResults(): MainUiState = copy(
        allResults = emptyList(),
        results = emptyList(),
        currentPage = 0,
        totalPages = 0,
        statusText = "",
    )

    // ─── 复制 / 历史 ──────────────────────────────────────────

    fun copyExpression() {
        val expr = _state.value.expression
        if (expr.isBlank()) return emit("表达式为空")
        copyToClipboard(expr)
        emit("已复制规则表达式")
    }

    fun copyAllResults() {
        val all = _state.value.allResults
        if (all.isEmpty()) return emit("没有可复制的结果")
        copyToClipboard(all.joinToString("\n"))
        emit("已复制 ${all.size} 条结果")
    }

    fun copyOneResult(item: ResultItem) {
        copyToClipboard(item.content)
        emit("已复制第 ${item.index} 条")
    }

    private fun copyToClipboard(text: String) {
        val manager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("Crawler Tool", text))
    }

    /** 历史页是常驻页面,直接刷进状态而不是走一次性事件。 */
    fun refreshHistory() {
        val items = historyRepo.loadHistory()
        _state.update { it.copy(history = items) }
    }

    fun deleteHistoryItem(item: HistoryManager.HistoryItem) {
        historyRepo.delete(item.id)
        refreshHistory()
    }

    fun clearHistory() {
        historyRepo.clearAll()
        refreshHistory()
    }

    // ─── 结构树 / 导出对话框 ─────────────────────────────────

    fun showStructure(html: String? = null) {
        val target = html?.takeIf { it.isNotBlank() } ?: _state.value.rawHtml
        if (target.isBlank()) return emit("当前没有可查看的 HTML")
        _events.trySend(MainEvent.OpenStructure(target))
    }

    fun requestExport() {
        if (_state.value.allResults.isEmpty()) return emit("没有可导出的结果")
        _state.update { it.copy(exportFormats = listOf("JSON", "CSV", "纯文本")) }
    }

    fun dismissExport() {
        _state.update { it.copy(exportFormats = null) }
    }

    /** 导出到 Downloads;Crawler-Tool 子目录,Android 10+ 走 MediaStore,无需写权限。 */
    fun exportResults(format: String) {
        val items = _state.value.allResults
        _state.update { it.copy(exportFormats = null) }
        if (items.isEmpty()) return emit("没有可导出的结果")

        val key = when (format) {
            "JSON" -> "json"
            "CSV" -> "csv"
            else -> "text"
        }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val content = crawlerRepo.exportResults(items, key)
                    val ext = when (key) {
                        "json" -> "json"
                        "csv" -> "csv"
                        else -> "txt"
                    }
                    val mime = when (key) {
                        "json" -> "application/json"
                        "csv" -> "text/csv"
                        else -> "text/plain"
                    }
                    val fileName = "Crawler_Results_${System.currentTimeMillis()}.$ext"
                    writeExport(content, fileName, mime)
                }
            }
            outcome.fold(
                onSuccess = { emit("已导出:$it") },
                onFailure = { emit("导出失败:${it.message ?: "未知错误"}") }
            )
        }
    }

    private fun writeExport(content: String, fileName: String, mime: String): String {
        val app = getApplication<Application>()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Crawler-Tool"
            )
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建文件")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            ?: throw IllegalStateException("无法写入文件")
        return "Downloads/Crawler-Tool/$fileName"
    }

    private fun emit(text: String) {
        _events.trySend(MainEvent.Message(text))
    }
}
