package com.SoloSu.Crawler_tool

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 爬虫引擎 v1.3: 支持 XPath / CSS / 正则 + 延迟 / Cookie / 代理 / 统计
 */
object CrawlerEngine {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // 统计信息
    data class FetchStats(
        var totalRequests: Int = 0,
        var successRequests: Int = 0,
        var failedRequests: Int = 0,
        var totalResults: Int = 0,
        var startTime: Long = 0,
        var endTime: Long = 0
    ) {
        val elapsedMs: Long get() = endTime - startTime
        val elapsedStr: String get() {
            val ms = elapsedMs
            return when {
                ms < 1000 -> "${ms}ms"
                ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
                else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
            }
        }
    }

    /**
     * 构建 OkHttpClient (支持动态配置)
     */
    fun buildClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(SettingsActivity.getConnectTimeout(context), TimeUnit.SECONDS)
            .readTimeout(SettingsActivity.getReadTimeout(context), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val reqBuilder = original.newBuilder()

                // 自定义 User-Agent
                val ua = SettingsActivity.getUserAgent(context)
                reqBuilder.header("User-Agent",
                    if (ua.isNotBlank()) ua
                    else "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
                )
                reqBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                reqBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

                // 自定义 Header
                val customHeaders = HeaderManager.buildHeaderMap(context)
                customHeaders.forEach { (key, value) ->
                    reqBuilder.header(key, value)
                }

                chain.proceed(reqBuilder.build())
            }

        // 代理支持
        val proxySettings = SettingsActivity.getProxySettings(context)
        if (proxySettings != null) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxySettings.first, proxySettings.second)))
        }

        return builder.build()
    }

    /**
     * 匹配模式
     */
    sealed class MatchMode {
        data object XPath : MatchMode()
        data object CssSelector : MatchMode()
        data object Regex : MatchMode()
    }

    /**
     * 获取页面并匹配（支持延迟）
     */
    fun fetchAndMatch(
        context: Context,
        url: String,
        expression: String,
        mode: MatchMode,
        delayMs: Long = 0,
        stats: FetchStats? = null
    ): Result<List<String>> {
        return runCatching {
            stats?.let { it.totalRequests++; if (it.startTime == 0L) it.startTime = System.currentTimeMillis() }

            if (delayMs > 0) {
                Thread.sleep(delayMs)
            }

            val html = doGet(context, url)
            val results = matchWithMode(html, expression, mode)

            stats?.let { it.successRequests++; it.totalResults += results.size }
            results
        }.onFailure {
            stats?.let { it.failedRequests++ }
        }
    }

    /**
     * 使用 JS 渲染后匹配
     */
    suspend fun fetchAndMatchWithJs(
        context: Context,
        url: String,
        expression: String,
        mode: MatchMode,
        waitSeconds: Int = 3
    ): Result<List<String>> {
        return runCatching {
            val renderResult = JsRenderEngine.render(context, url, waitSeconds)
            val html = renderResult.getOrThrow()
            matchWithMode(html, expression, mode)
        }
    }

    /**
     * 仅匹配（不重新请求）
     */
    fun matchOnly(
        html: String,
        expression: String,
        mode: MatchMode
    ): Result<List<String>> {
        return runCatching {
            matchWithMode(html, expression, mode)
        }
    }

    /**
     * 仅获取 HTML
     */
    fun fetchHtml(context: Context, url: String): Result<String> {
        return runCatching {
            doGet(context, url)
        }
    }

    private fun doGet(context: Context, url: String): String {
        val client = buildClient(context)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw RuntimeException("响应体为空")
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.message}\n$body")
            }
            body
        }
    }

    private fun matchWithMode(html: String, expression: String, mode: MatchMode): List<String> {
        return when (mode) {
            is MatchMode.XPath -> matchWithXPath(html, expression)
            is MatchMode.CssSelector -> matchWithCssSelector(html, expression)
            is MatchMode.Regex -> matchWithRegex(html, expression)
        }
    }

    private fun matchWithXPath(html: String, xpathExpr: String): List<String> {
        try {
            return JsoupXPathEngine.evaluate(html, xpathExpr)
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            throw when (e) {
                is org.jsoup.UncheckedIOException ->
                    RuntimeException("HTML解析失败,页面内容可能为空或格式异常:$msg", e)
                else -> RuntimeException("XPath匹配失败:$msg\n\n请检查表达式格式,例如:\n• //a — 匹配所有a标签\n• //a/@href — 匹配所有链接的href属性\n• //div[@class='title'] — 匹配class为title的div", e)
            }
        }
    }

    private fun matchWithCssSelector(html: String, cssQuery: String): List<String> {
        try {
            val doc = Jsoup.parse(html)
            val elements = doc.select(cssQuery)
            if (elements.isEmpty()) return emptyList()
            return elements.map { it.outerHtml() }
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            throw RuntimeException("CSS选择器匹配失败:$msg\n\n请检查选择器格式,例如:\n• a — 匹配所有a标签\n• a[href] — 匹配有href属性的a标签\n• div.title — 匹配class为title的div\n• #content — 匹配id为content的元素", e)
        }
    }

    private fun matchWithRegex(html: String, pattern: String): List<String> {
        try {
            val regex = try {
                Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
            } catch (e: Exception) {
                throw RuntimeException("正则表达式格式错误:${e.message}", e)
            }
            val matches = regex.findAll(html).toList()
            if (matches.isEmpty()) return emptyList()
            return matches.map { match ->
                if (match.groupValues.size > 1) {
                    match.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString(", ")
                } else {
                    match.value
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            throw RuntimeException("正则匹配失败:$msg\n\n请检查表达式格式,例如:\n• href=\"([^\"]+)\" — 提取所有链接\n• <title>([^<]+)</title> — 提取标题", e)
        }
    }

    // ─── 导出 ──────────────────────────────────────────────────

    fun exportResults(items: List<String>, format: String): String {
        return when (format.lowercase()) {
            "json" -> exportAsJson(items)
            "csv" -> exportAsCsv(items)
            else -> exportAsText(items)
        }
    }

    private fun exportAsJson(items: List<String>): String {
        val arr = JSONArray()
        items.forEachIndexed { index, content ->
            val obj = JSONObject()
            obj.put("index", index + 1)
            obj.put("content", content)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun exportAsCsv(items: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("index,content")
        items.forEachIndexed { index, content ->
            val escaped = content.replace("\"", "\"\"")
            sb.appendLine("${index + 1},\"${escaped.replace("\n", "\\n")}\"")
        }
        return sb.toString()
    }

    private fun exportAsText(items: List<String>): String {
        val sb = StringBuilder()
        items.forEachIndexed { index, content ->
            sb.appendLine("${index + 1}. $content")
            sb.appendLine()
        }
        return sb.toString()
    }

    // ─── 持久化 ────────────────────────────────────────────────

    fun saveResults(context: Context, items: List<String>, fileName: String? = null): String {
        val dir = java.io.File(context.cacheDir, "results")
        if (!dir.exists()) dir.mkdirs()
        val name = fileName ?: "results_${System.currentTimeMillis()}.json"
        val file = java.io.File(dir, name)
        file.writeText(exportResults(items, "json"))
        return file.absolutePath
    }

    fun loadSavedResults(context: Context): List<java.io.File> {
        val dir = java.io.File(context.cacheDir, "results")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.sortedByDescending { it.lastModified() }?.take(50) ?: emptyList()
    }

    fun clearSavedResults(context: Context) {
        val dir = java.io.File(context.cacheDir, "results")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}
