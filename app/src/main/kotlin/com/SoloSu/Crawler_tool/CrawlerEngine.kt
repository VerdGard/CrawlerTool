package com.SoloSu.Crawler_tool

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 爬虫引擎：基于 Jsoup 解析 HTML，使用 JsoupXPathEngine 进行 XPath 匹配。
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
     * 匹配模式（仅保留 XPath）
     */
    sealed class MatchMode {
        data object XPath : MatchMode()
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
            matchWithXPath(html, expression)
        }
    }

    /**
     * 仅使用已获取的HTML进行匹配（不重新请求）
     */
    fun matchOnly(
        html: String,
        expression: String,
        mode: MatchMode
    ): Result<List<String>> {
        return runCatching {
            matchWithXPath(html, expression)
        }
    }

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

    /**
     * XPath 匹配 — 基于 Jsoup 解析 HTML + JsoupXPathEngine 求值。
     *
     * 与旧方案 (javax.xml.xpath + 正则转 XML) 相比：
     * - Jsoup 能处理真实世界中的不规范 HTML（自动补全、容错）
     * - 无需脆弱的正则转换
     * - 支持更丰富的 XPath 谓词
     * - 错误信息更清晰
     */
    private fun matchWithXPath(html: String, xpathExpr: String): List<String> {
        try {
            return JsoupXPathEngine.evaluate(html, xpathExpr)
        } catch (e: Exception) {
            // 重新包装为友好错误信息
            val msg = e.message ?: "未知错误"
            throw when (e) {
                is org.jsoup.UncheckedIOException ->
                    RuntimeException("HTML解析失败，页面内容可能为空或格式异常：$msg", e)
                else -> RuntimeException("XPath匹配失败：$msg\n\n请检查表达式格式，例如：\n• //a — 匹配所有a标签\n• //a/@href — 匹配所有链接的href属性\n• //div[@class='title'] — 匹配class为title的div", e)
            }
        }
    }
}
