package com.SoloSu.Crawler_tool

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * 爬虫引擎:支持 XPath / CSS 选择器 / 正则表达式三种匹配模式。
 */
object CrawlerEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 匹配模式
     */
    sealed class MatchMode {
        data object XPath : MatchMode()
        data object CssSelector : MatchMode()
        data object Regex : MatchMode()
    }

    /**
     * 获取页面内容并匹配
     */
    fun fetchAndMatch(
        url: String,
        expression: String,
        mode: MatchMode
    ): Result<List<String>> {
        return runCatching {
            val html = doGet(url)
            matchWithMode(html, expression, mode)
        }
    }

    /**
     * 仅使用已获取的HTML进行匹配(不重新请求)
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
     * 仅获取页面原始 HTML
     */
    fun fetchHtml(url: String): Result<String> {
        return runCatching {
            doGet(url)
        }
    }

    /**
     * 获取当前 OkHttpClient 实例,供设置页面更新后重建
     */
    fun getClient(): OkHttpClient = client

    private fun doGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            response.body?.string() ?: throw RuntimeException("响应体为空")
        }
    }

    private fun doPost(url: String, jsonParams: String): String {
        val body = jsonParams.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            response.body?.string() ?: throw RuntimeException("响应体为空")
        }
    }

    private fun matchWithMode(html: String, expression: String, mode: MatchMode): List<String> {
        return when (mode) {
            is MatchMode.XPath -> matchWithXPath(html, expression)
            is MatchMode.CssSelector -> matchWithCssSelector(html, expression)
            is MatchMode.Regex -> matchWithRegex(html, expression)
        }
    }

    /**
     * XPath 匹配
     */
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

    /**
     * CSS 选择器匹配 — 基于 Jsoup 原生 select
     */
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

    /**
     * 正则表达式匹配 — 基于 Kotlin Regex
     */
    private fun matchWithRegex(html: String, pattern: String): List<String> {
        try {
            val regex = try {
                Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
            } catch (e: Exception) {
                throw RuntimeException("正则表达式格式错误:${e.message}", e)
            }
            val matches = regex.findAll(html).toList()
            if (matches.isEmpty()) return emptyList()
            // 如果有捕获组,返回第一个捕获组;否则返回完整匹配
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

    /**
     * 导出结果
     */
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
            sb.appendLine("--- ${index + 1} ---")
            sb.appendLine(content)
            sb.appendLine()
        }
        return sb.toString()
    }
}
