package com.SoloSu.Crawler_tool.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.SoloSu.Crawler_tool.CrawlerEngine
import com.SoloSu.Crawler_tool.HistoryManager
import com.SoloSu.Crawler_tool.ResultItem
import com.SoloSu.Crawler_tool.repository.CrawlerRepository
import com.SoloSu.Crawler_tool.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主界面 UI 状态。所有可变字段集中在此,Activity 只负责渲染。
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
) {
    val hasResults: Boolean get() = results.isNotEmpty()
    /** 结果区为空且不处于加载中时,显示空状态引导 */
    val showEmptyState: Boolean get() = results.isEmpty() && !isLoading
}

sealed class MainEvent {
    data class Message(val text: String) : MainEvent()
    data class ShowHistory(val items: List<HistoryManager.HistoryItem>) : MainEvent()
    data class ShowExportChooser(val formats: List<String>) : MainEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val crawlerRepo = CrawlerRepository(application)
    private val historyRepo = HistoryRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pageSize = 20

    private val _state = MutableLiveData(MainUiState())
    val state: LiveData<MainUiState> = _state

    private val _events = MutableLiveData<MainEvent>()
    val events: LiveData<MainEvent> = _events

    // ─── 输入同步 ─────────────────────────────────────────────

    fun updateUrl(url: String) {
        _state.value = _state.value?.copy(url = url)
    }

    fun updateExpression(expr: String) {
        _state.value = _state.value?.copy(expression = expr)
    }

    fun updateMatchMode(mode: CrawlerEngine.MatchMode) {
        _state.value = _state.value?.copy(matchMode = mode)
    }

    /** 从历史记录回填,同时重置结果区,避免旧结果与新表达式错配。 */
    fun loadHistoryItem(item: HistoryManager.HistoryItem) {
        val mode = when (item.mode) {
            "CssSelector" -> CrawlerEngine.MatchMode.CssSelector
            "Regex" -> CrawlerEngine.MatchMode.Regex
            else -> CrawlerEngine.MatchMode.XPath
        }
        _state.value = (_state.value ?: MainUiState()).copy(
            url = item.url,
            expression = item.expression,
            matchMode = mode,
        )
        clearResults()
    }

    // ─── 匹配 ────────────────────────────────────────────────

    fun performMatch() {
        val s = _state.value ?: return
        if (s.isLoading) return

        val url = s.url.trim()
        val expr = s.expression.trim()
        when {
            url.isBlank() || !url.contains(".") ->
                return emit("请输入有效的 URL")
            expr.isBlank() ->
                return emit("请输入匹配表达式")
        }

        val previousHtml = s.rawHtml
        _state.value = s.copy(isLoading = true, statusText = "正在请求并匹配...")

        scope.launch {
            crawlerRepo.fetchAndMatch(url, expr, s.matchMode).fold(
                onSuccess = { page ->
                    val results = page.matches
                    _state.value = _state.value?.copy(
                        // JS 渲染模式拿不到原始 HTML,此时保留上一次的快照
                        rawHtml = page.html.ifBlank { previousHtml },
                        allResults = results,
                        isLoading = false,
                        statusText = if (results.isEmpty()) "匹配完成,未找到结果"
                        else "匹配完成,共 ${results.size} 条结果",
                        currentPage = 0,
                    )
                    applyPage(0)
                    if (results.isNotEmpty()) {
                        historyRepo.save(url, expr, s.matchMode.toString(), results.size)
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        statusText = "匹配失败",
                        results = emptyList(),
                        totalPages = 0,
                    )
                    emit("匹配失败:${e.message?.lineSequence()?.firstOrNull() ?: "未知错误"}")
                }
            )
        }
    }

    fun performBatchMatch(urlPattern: String, startPage: Int, endPage: Int) {
        val s = _state.value ?: return
        if (s.isLoading) return
        val expr = s.expression.trim()
        if (expr.isBlank()) return emit("请先输入匹配表达式")
        if (urlPattern.isBlank() || !urlPattern.contains("{page}")) {
            return emit("URL 模板需包含 {page} 占位符")
        }
        if (endPage < startPage) return emit("结束页不能小于起始页")

        _state.value = s.copy(isLoading = true, statusText = "正在批量抓取...")

        scope.launch {
            val result = crawlerRepo.fetchAndMatchBatch(
                urlPattern, expr, s.matchMode, startPage, endPage
            ) { progress ->
                val text = "正在抓取第 ${progress.page}/${progress.total} 页..."
                if (_state.value?.statusText != text) {
                    _state.value = _state.value?.copy(statusText = text)
                }
            }
            result.fold(
                onSuccess = { items ->
                    _state.value = _state.value?.copy(
                        rawHtml = "",
                        allResults = items,
                        isLoading = false,
                        statusText = "批量抓取完成,共 ${items.size} 条结果",
                        currentPage = 0,
                    )
                    applyPage(0)
                    if (items.isNotEmpty()) {
                        historyRepo.save(urlPattern, expr, s.matchMode.toString(), items.size)
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        statusText = "批量抓取失败",
                        results = emptyList(),
                        totalPages = 0,
                    )
                    emit("批量抓取失败:${e.message?.lineSequence()?.firstOrNull() ?: "未知错误"}")
                }
            )
        }
    }

    fun clearResults() {
        val s = _state.value ?: return
        _state.value = s.copy(
            allResults = emptyList(),
            results = emptyList(),
            currentPage = 0,
            totalPages = 0,
            statusText = "",
        )
    }

    // ─── 分页 ────────────────────────────────────────────────

    fun goToPage(page: Int) {
        val s = _state.value ?: return
        if (page < 0 || page >= s.totalPages || page == s.currentPage) return
        _state.value = s.copy(currentPage = page)
        applyPage(page)
    }

    fun nextPage() = goToPage((_state.value?.currentPage ?: 0) + 1)

    fun prevPage() = goToPage((_state.value?.currentPage ?: 0) - 1)

    /** 按页码把 allResults 切成一页,并同步 totalPages。 */
    private fun applyPage(page: Int) {
        val s = _state.value ?: return
        if (s.allResults.isEmpty()) {
            _state.value = s.copy(results = emptyList(), currentPage = 0, totalPages = 0)
            return
        }
        val totalPages = (s.allResults.size + pageSize - 1) / pageSize
        val safePage = page.coerceIn(0, totalPages - 1)
        val start = safePage * pageSize
        val end = minOf(start + pageSize, s.allResults.size)
        val slice = s.allResults.subList(start, end).mapIndexed { i, content ->
            ResultItem(start + i + 1, content)
        }
        _state.value = s.copy(
            results = slice,
            currentPage = safePage,
            totalPages = totalPages,
        )
    }

    // ─── 复制 / 历史 ──────────────────────────────────────────

    fun copyExpression() {
        val expr = _state.value?.expression.orEmpty()
        if (expr.isBlank()) return emit("表达式为空")
        copyToClipboard(expr)
        emit("已复制规则表达式")
    }

    fun copyAllResults() {
        val all = _state.value?.allResults.orEmpty()
        if (all.isEmpty()) return emit("没有可复制的结果")
        copyToClipboard(all.joinToString("\n"))
        emit("已复制 ${all.size} 条结果")
    }

    private fun copyToClipboard(text: String) {
        // minSdk 21:不能使用 getSystemService(Class),改用字符串方式获取
        val manager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText("Crawler Tool", text))
    }

    fun showHistory() {
        val items = historyRepo.loadHistory()
        if (items.isEmpty()) return emit("暂无历史记录")
        _events.value = MainEvent.ShowHistory(items)
    }

    fun deleteHistoryItem(item: HistoryManager.HistoryItem) {
        historyRepo.delete(item.id)
    }

    fun clearHistory() {
        historyRepo.clearAll()
    }

    // ─── 导出 ────────────────────────────────────────────────

    fun requestExport() {
        if (_state.value?.allResults.isNullOrEmpty()) return emit("没有可导出的结果")
        _events.value = MainEvent.ShowExportChooser(listOf("JSON", "CSV", "纯文本"))
    }

    /** 导出到外部存储;Android 10+ 走 MediaStore,无需写权限。 */
    fun exportResults(format: String) {
        val items = _state.value?.allResults.orEmpty()
        if (items.isEmpty()) return emit("没有可导出的结果")

        val key = when (format) {
            "JSON" -> "json"
            "CSV" -> "csv"
            else -> "text"
        }

        scope.launch {
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

    @Suppress("DEPRECATION")
    private fun writeExport(content: String, fileName: String, mime: String): String {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Crawler-Tool")
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建文件")
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IllegalStateException("无法写入文件")
            return "Downloads/Crawler-Tool/$fileName"
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ), "Crawler-Tool")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建目录 ${dir.path}")
        val file = File(dir, fileName)
        file.writeText(content)
        return file.absolutePath
    }

    private fun emit(text: String) {
        _events.value = MainEvent.Message(text)
    }
}
