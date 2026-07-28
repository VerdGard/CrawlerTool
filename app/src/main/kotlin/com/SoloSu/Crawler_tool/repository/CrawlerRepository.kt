package com.SoloSu.Crawler_tool.repository

import android.content.Context
import com.SoloSu.Crawler_tool.CrawlerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CrawlerRepository(private val context: Context) {

    private val settingsRepo = SettingsRepository(context)

    suspend fun fetchAndMatch(
        url: String,
        expression: String,
        mode: CrawlerEngine.MatchMode,
        useJs: Boolean = false,
        jsDelay: Int = 3
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            if (useJs) {
                CrawlerEngine.fetchAndMatchWithJs(context, url, expression, mode, jsDelay)
            } else {
                CrawlerEngine.fetchAndMatch(context, url, expression, mode)
            }
        }
    }

    suspend fun fetchAndMatchBatch(
        urlPattern: String,
        expression: String,
        mode: CrawlerEngine.MatchMode,
        startPage: Int,
        endPage: Int,
        delayMs: Long = 0,
        retryCount: Int = 1,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val allResults = mutableListOf<String>()
                val totalPages = endPage - startPage + 1
                for (page in startPage..endPage) {
                    onProgress?.invoke(page - startPage + 1, totalPages)
                    val url = urlPattern.replace("{page}", page.toString())
                    if (delayMs > 0 && page > startPage) Thread.sleep(delayMs)

                    var result: Result<List<String>>? = null
                    for (attempt in 0..retryCount) {
                        result = CrawlerEngine.fetchAndMatch(context, url, expression, mode)
                        if (result.isSuccess) break
                        if (attempt < retryCount) Thread.sleep(1000)
                    }
                    result?.onSuccess { allResults.addAll(it) }
                }
                allResults
            }
        }
    }

    suspend fun fetchHtml(url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            CrawlerEngine.fetchHtml(context, url)
        }
    }


    fun exportResults(items: List<String>, format: String): String {
        return CrawlerEngine.exportResults(items, format)
    }

}
