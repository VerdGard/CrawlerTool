package com.SoloSu.Crawler_tool

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * JavaScript 渲染引擎 (P2.11)
 * 使用 WebView 加载 SPA 页面，等待渲染完成后提取 HTML
 */
object JsRenderEngine {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastResult: String? = null

    /**
     * 使用 WebView 渲染页面并获取完整 HTML
     */
    suspend fun render(context: Context, url: String, waitSeconds: Int = 3): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                try {
                    val wv = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, urlStr: String?) {
                                // 页面加载完成后，等待 JS 渲染，再提取 HTML
                                view?.postDelayed({
                                    view.evaluateJavascript(
                                        "(function() { return document.documentElement.outerHTML; })()"
                                    ) { html ->
                                        val decoded = decodeUnicodeEscapes(html ?: "")
                                        lastResult = decoded
                                        webView?.destroy()
                                        webView = null
                                        if (decoded.isNotBlank()) {
                                            continuation.resume(Result.success(decoded))
                                        } else {
                                            continuation.resume(Result.failure(Exception("渲染结果为空")))
                                        }
                                    }
                                }, (waitSeconds * 1000).toLong())
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                // 即使有错误也尝试获取已加载的内容
                            }
                        }
                    }
                    webView = wv
                    wv.loadUrl(url)
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                }
            }

            // 超时保护
            mainHandler.postDelayed({
                if (continuation.isActive) {
                    val result = lastResult
                    webView?.destroy()
                    webView = null
                    if (result != null) {
                        continuation.resume(Result.success(result))
                    } else {
                        continuation.resume(Result.failure(Exception("JS渲染超时")))
                    }
                }
            }, TimeUnit.SECONDS.toMillis(waitSeconds.toLong() + 5))
        }
    }

    /**
     * 同步渲染（用于非协程环境）
     */
    fun renderSync(context: Context, url: String, waitSeconds: Int = 3): Result<String> {
        val latch = CountDownLatch(1)
        var result: Result<String>? = null

        mainHandler.post {
            try {
                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.domStorageEnabled = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, urlStr: String?) {
                            view?.postDelayed({
                                view.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML; })()"
                                ) { html ->
                                    val decoded = decodeUnicodeEscapes(html ?: "")
                                    result = Result.success(decoded)
                                    webView?.destroy()
                                    webView = null
                                    latch.countDown()
                                }
                            }, (waitSeconds * 1000).toLong())
                        }
                    }
                }
                webView = wv
                wv.loadUrl(url)
            } catch (e: Exception) {
                result = Result.failure(e)
                latch.countDown()
            }
        }

        latch.await(waitSeconds + 5L, TimeUnit.SECONDS)
        return result ?: Result.failure(Exception("JS渲染超时或无结果"))
    }

    /**
     * 解码 WebView evaluateJavascript 返回的 Unicode 转义序列
     */
    private fun decodeUnicodeEscapes(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\' && i + 5 < input.length && input[i + 1] == 'u') {
                val hex = input.substring(i + 2, i + 6)
                try {
                    sb.append(hex.toInt(16).toChar())
                    i += 6
                    continue
                } catch (_: Exception) {}
            }
            // 去除首尾引号
            if (i == 0 && (input[i] == '"' || input[i] == '\'')) {
                i++
                continue
            }
            if (i == input.length - 1 && (input[i] == '"' || input[i] == '\'')) {
                break
            }
            sb.append(input[i])
            i++
        }
        return sb.toString()
    }
}
