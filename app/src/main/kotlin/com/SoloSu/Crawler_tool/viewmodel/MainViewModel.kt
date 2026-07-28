package com.SoloSu.Crawler_tool.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.SoloSu.Crawler_tool.CrawlerEngine
import com.SoloSu.Crawler_tool.HistoryManager
import com.SoloSu.Crawler_tool.ResultItem
import com.SoloSu.Crawler_tool.repository.CrawlerRepository
import com.SoloSu.Crawler_tool.repository.HistoryRepository
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class MainUiState(
    val rawHtml: String = "",
    val url: String = "",
    val expression: String = "",
    val matchMode: CrawlerEngine.MatchMode = CrawlerEngine.MatchMode.XPath,
    val results: List<ResultItem> = emptyList(),
    val allResults: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val statusText: String = "",
    val isLoading: Boolean = false,
    val batchVisible: Boolean = false,
)

sealed class MainEvent {
    data class ShowToast(val message: String) : MainEvent()
    data class ShowHistory(val items: List<HistoryManager.HistoryItem>) : MainEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val crawlerRepo = CrawlerRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val historyRepo = HistoryRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pageSize = 20

    private val _state = MutableLiveData(MainUiState())
    val state: LiveData<MainUiState> = _state

    private val _events = MutableLiveData<MainEvent>()
    val events: LiveData<MainEvent> = _events

    fun updateUrl(url: String) { _state.value = _state.value?.copy(url = url) }
    fun updateExpression(expr: String) { _state.value = _state.value?.copy(expression = expr) }
    fun updateMatchMode(mode: CrawlerEngine.MatchMode) { _state.value = _state.value?.copy(matchMode = mode) }

    fun performMatch() {
        val s = _state.value ?: return
        val url = s.url.trim()
        val expr = s.expression.trim()
        if (url.isBlank() || url == "https://") {
            _events.value = MainEvent.ShowToast("请输入有效的URL")
            return
        }
        if (expr.isBlank()) {
            _events.value = MainEvent.ShowToast("请输入匹配表达式")
            return
        }

        _state.value = s.copy(isLoading = true, statusText = "正在请求并匹配...")

        scope.launch {
            val useJs = settingsRepo.isJsRenderEnabled()
            val jsDelay = settingsRepo.getJsRenderDelay()
            val result = crawlerRepo.fetchAndMatch(url, expr, s.matchMode, useJs, jsDelay)

            val htmlResult = crawlerRepo.fetchHtml(url)
            result.fold(
                onSuccess = { items ->
                    _state.value = _state.value?.copy(
                        rawHtml = htmlResult.getOrDefault(""),
                        allResults = items,
                        isLoading = false,
                        statusText = if (items.isEmpty()) "匹配完成,未找到结果" else "匹配完成,共 ${items.size} 条结果"
                    )
                    showPage(0)
                    if (items.isNotEmpty()) {
                        historyRepo.save(url, expr, s.matchMode.toString(), items.size)
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        statusText = "匹配失败",
                        results = emptyList()
                    )
                    _events.value = MainEvent.ShowToast("匹配失败: ${e.message}")
                }
            )
        }
    }

    fun performBatchMatch(pattern: String, startPage: Int, endPage: Int) {
        _state.value = _state.value?.copy(isLoading = true, statusText = "正在批量抓取...")
        scope.launch {
            val result = crawlerRepo.fetchAndMatchBatch(
                pattern,
                _state.value?.expression ?: "",
                _state.value?.matchMode ?: CrawlerEngine.MatchMode.XPath,
                startPage, endPage
            )
            // val htmlResult = crawlerRepo.fetchHtml(url)  // removed: url not in scope
            result.fold(
                onSuccess = { items ->
                    _state.value = _state.value?.copy(
                        rawHtml = "",
                        allResults = items,
                        isLoading = false,
                        statusText = "批量抓取完成,共 ${items.size} 条结果"
                    )
                    showPage(0)
                },
                onFailure = { e ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        statusText = "批量抓取失败",
                        results = emptyList()
                    )
                    _events.value = MainEvent.ShowToast("批量抓取失败: ${e.message}")
                }
            )
        }
    }

    fun clearResults() {
        _state.value = _state.value?.copy(
            allResults = emptyList(),
            results = emptyList(),
            currentPage = 0,
            statusText = "已清空"
        )
    }

    fun goToPage(page: Int) {
        val s = _state.value ?: return
        val totalPages = (s.allResults.size + pageSize - 1) / pageSize
        if (page < 0 || page >= totalPages) return
        _state.value = s.copy(currentPage = page)
        showPage(page)
    }

    fun copyRuleExpression() {
        val expr = _state.value?.expression ?: ""
        if (expr.isBlank()) { _events.value = MainEvent.ShowToast("表达式为空"); return }
        val clipboard = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crawler Rule", expr))
        _events.value = MainEvent.ShowToast("已复制规则表达式")
    }

    fun showHistory() {
        val items = historyRepo.loadHistory()
        if (items.isEmpty()) { _events.value = MainEvent.ShowToast("暂无历史记录"); return }
        _events.value = MainEvent.ShowHistory(items)
    }

    fun loadHistoryItem(item: HistoryManager.HistoryItem) {
        _state.value = _state.value?.copy(
            url = item.url,
            expression = item.expression,
            matchMode = when (item.mode) {
                "CssSelector" -> CrawlerEngine.MatchMode.CssSelector
                "Regex" -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }
        )
    }

    fun getExportFormats() = arrayOf("JSON", "CSV", "纯文本")

    fun exportResults(format: String) {
        val items = _state.value?.allResults ?: emptyList()
        if (items.isEmpty()) { _events.value = MainEvent.ShowToast("没有可导出的结果"); return }

        val content = crawlerRepo.exportResults(items, format)
        val ext = when (format) { "json" -> "json"; "csv" -> "csv"; else -> "txt" }
        val fileName = "Crawler_Results_${System.currentTimeMillis()}.$ext"

        try {
            val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Crawler-Tool")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeText(content)
            _events.value = MainEvent.ShowToast("已导出到: ${file.absolutePath}")
        } catch (e: Exception) {
            // 若直接访问失败,尝试 MediaStore (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val context = getApplication<Application>()
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, when (ext) {
                            "json" -> "application/json"
                            "csv" -> "text/csv"
                            else -> "text/plain"
                        })
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Crawler-Tool")
                    }
                    val uri = context.contentResolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    )
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(content.toByteArray())
                        }
                        _events.value = MainEvent.ShowToast("已导出到: Crawler-Tool/$fileName")
                    } ?: throw Exception("无法创建文件")
                } catch (e2: Exception) {
                    _events.value = MainEvent.ShowToast("导出失败: ${e2.message}")
                }
            } else {
                _events.value = MainEvent.ShowToast("导出失败: 无写入权限")
            }
        }
    }

    private fun showPage(page: Int) {
        val s = _state.value ?: return
        if (s.allResults.isEmpty()) {
            _state.value = s.copy(results = emptyList(), totalPages = 0)
            return
        }
        val totalPages = (s.allResults.size + pageSize - 1) / pageSize
        val start = page * pageSize
        val end = minOf(start + pageSize, s.allResults.size)
        if (start >= s.allResults.size) {
            goToPage(0)
            return
        }
        val pageItems = s.allResults.subList(start, end).mapIndexed { i, c ->
            ResultItem(start + i + 1, c)
        }
        _state.value = s.copy(results = pageItems, currentPage = page, totalPages = totalPages)
    }
}
