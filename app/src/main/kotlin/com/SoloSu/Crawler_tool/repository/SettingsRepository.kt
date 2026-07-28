package com.SoloSu.Crawler_tool.repository

import android.content.Context
import android.content.SharedPreferences
import com.SoloSu.Crawler_tool.HeaderManager

class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "crawler_settings"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_CONNECT_TIMEOUT = "connect_timeout"
        const val KEY_READ_TIMEOUT = "read_timeout"
        const val KEY_THEME = "theme"
        const val KEY_BATCH_DELAY = "batch_delay"
        const val KEY_BATCH_RETRY = "batch_retry"
        const val KEY_PROXY_ENABLED = "proxy_enabled"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_JS_RENDER = "js_render"
        const val KEY_JS_DELAY = "js_delay"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }

    // User-Agent
    fun getUserAgent(): String = prefs.getString(KEY_USER_AGENT, "") ?: ""
    fun setUserAgent(ua: String) = prefs.edit().putString(KEY_USER_AGENT, ua).apply()

    // Timeouts
    fun getConnectTimeout(): Long = prefs.getLong(KEY_CONNECT_TIMEOUT, 15L)
    fun setConnectTimeout(seconds: Long) = prefs.edit().putLong(KEY_CONNECT_TIMEOUT, seconds).apply()
    fun getReadTimeout(): Long = prefs.getLong(KEY_READ_TIMEOUT, 30L)
    fun setReadTimeout(seconds: Long) = prefs.edit().putLong(KEY_READ_TIMEOUT, seconds).apply()

    // Theme
    fun getTheme(): String = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    fun setTheme(theme: String) = prefs.edit().putString(KEY_THEME, theme).apply()

    // Batch
    fun getBatchDelay(): Long = prefs.getLong(KEY_BATCH_DELAY, 0L)
    fun setBatchDelay(ms: Long) = prefs.edit().putLong(KEY_BATCH_DELAY, ms).apply()
    fun getBatchRetry(): Int = prefs.getInt(KEY_BATCH_RETRY, 1)
    fun setBatchRetry(count: Int) = prefs.edit().putInt(KEY_BATCH_RETRY, count).apply()

    // Proxy
    fun isProxyEnabled(): Boolean = prefs.getBoolean(KEY_PROXY_ENABLED, false)
    fun setProxyEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PROXY_ENABLED, enabled).apply()
    fun getProxyHost(): String = prefs.getString(KEY_PROXY_HOST, "") ?: ""
    fun setProxyHost(host: String) = prefs.edit().putString(KEY_PROXY_HOST, host).apply()
    fun getProxyPort(): Int = prefs.getInt(KEY_PROXY_PORT, 8080)
    fun setProxyPort(port: Int) = prefs.edit().putInt(KEY_PROXY_PORT, port).apply()
    fun getProxySettings(): Pair<String, Int>? {
        if (!isProxyEnabled()) return null
        val host = getProxyHost()
        if (host.isBlank()) return null
        return Pair(host, getProxyPort())
    }

    // JS Render
    fun isJsRenderEnabled(): Boolean = prefs.getBoolean(KEY_JS_RENDER, false)
    fun setJsRenderEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_JS_RENDER, enabled).apply()
    fun getJsRenderDelay(): Int = prefs.getInt(KEY_JS_DELAY, 3)
    fun setJsRenderDelay(seconds: Int) = prefs.edit().putInt(KEY_JS_DELAY, seconds).apply()

    // Language


    // Custom headers
    fun getCustomHeaders(): List<HeaderManager.HeaderEntry> = HeaderManager.getHeaders(context)
    fun addCustomHeader(entry: HeaderManager.HeaderEntry) = HeaderManager.addHeader(context, entry)
    fun updateCustomHeader(index: Int, entry: HeaderManager.HeaderEntry) = HeaderManager.updateHeader(context, index, entry)
    fun removeCustomHeader(index: Int) = HeaderManager.removeHeader(context, index)
    fun getCustomHeaderCount(): Int = getCustomHeaders().size
}
