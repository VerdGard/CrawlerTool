package com.SoloSu.Crawler_tool.repository

import android.content.Context
import com.SoloSu.Crawler_tool.CrawlerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 爬虫数据仓库。
 *
 * 职责:把「网络 + 解析」的重活挪到 IO 线程,并统一读取设置。
 * 对外只暴露 suspend 接口,调用方(ViewModel)不必关心线程切换。
 */
class CrawlerRepository(private val context: Context) {

    private val settingsRepo = SettingsRepository(context)

    /** 单页抓取结果:原始 HTML 与匹配结果一并返回,避免二次请求。 */
    data class PageResult(val html: String, val matches: List<String>)

    /** 批量抓取进度 */
    data class BatchProgress(val page: Int, val total: Int)

    suspend fun fetchAndMatch(
        url: String,
        expression: String,
        mode: CrawlerEngine.MatchMode
    ): Result<PageResult> = withContext(Dispatchers.IO) {
        if (settingsRepo.isJsRenderEnabled()) {
            // JS 渲染走 WebView,无法同时拿到「请求后」的原始 HTML,只能渲染后回传
            CrawlerEngine.fetchAndMatchWithJs(
                context, url, expression, mode, settingsRepo.getJsRenderDelay()
            ).map { PageResult("", it) }
        } else {
            CrawlerEngine.fetchHtmlAndMatch(context, url, expression, mode).map {
                PageResult(it.first, it.second)
            }
        }
    }

    /**
     * 批量抓取:逐页请求并合并结果。
     * 请求间隔与失败重试次数取自设置,且每页失败不影响整体流程。
     */
    suspend fun fetchAndMatchBatch(
        urlPattern: String,
        expression: String,
        mode: CrawlerEngine.MatchMode,
        startPage: Int,
        endPage: Int,
        onProgress: ((BatchProgress) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val delayMs = settingsRepo.getBatchDelay()
            val retryCount = settingsRepo.getBatchRetry().coerceAtLeast(0)
            val total = endPage - startPage + 1
            val all = mutableListOf<String>()
            val errors = mutableListOf<String>()

            for (page in startPage..endPage) {
                onProgress?.invoke(BatchProgress(page - startPage + 1, total))
                val url = urlPattern.replace("{page}", page.toString())

                var lastError: Throwable? = null
                var done = false
                for (attempt in 0..retryCount) {
                    // 首次请求前不延迟,重试之间短暂退避
                    val wait = if (attempt == 0) (if (page > startPage) delayMs else 0L)
                    else 1000L
                    if (wait > 0) Thread.sleep(wait)

                    val result = CrawlerEngine.fetchAndMatch(context, url, expression, mode)
                    if (result.isSuccess) {
                        all.addAll(result.getOrThrow())
                        done = true
                        break
                    }
                    lastError = result.exceptionOrNull()
                }
                if (!done) errors.add("第 $page 页: ${lastError?.message ?: "未知错误"}")
            }

            if (all.isEmpty() && errors.isNotEmpty()) {
                throw IllegalStateException("全部页面抓取失败\n" + errors.take(3).joinToString("\n"))
            }
            all
        }
    }

    fun exportResults(items: List<String>, format: String): String =
        CrawlerEngine.exportResults(items, format)
}
