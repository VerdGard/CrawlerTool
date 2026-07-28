package com.SoloSu.Crawler_tool

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * HTTP 请求头 & Cookie 管理器 (P1.6)
 * 支持自定义 Header、Cookie 管理、预设模板
 */
object HeaderManager {

    private const val PREF_NAME = "crawler_headers"
    private const val KEY_HEADERS = "custom_headers"
    private const val KEY_COOKIES = "saved_cookies"
    private const val KEY_ENABLED = "headers_enabled"

    data class HeaderEntry(
        val key: String,
        val value: String,
        val enabled: Boolean = true,
        val isBuiltin: Boolean = false
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)


    // ─── CRUD ──────────────────────────────────────────────────

    fun getHeaders(context: Context): List<HeaderEntry> {
        val prefs = getPrefs(context)
        return loadHeaders(prefs, KEY_HEADERS)
    }

    fun addHeader(context: Context, entry: HeaderEntry) {
        val prefs = getPrefs(context)
        val headers = getHeaders(context).toMutableList()
        headers.add(entry)
        saveHeaders(prefs, KEY_HEADERS, headers)
    }

    fun updateHeader(context: Context, index: Int, entry: HeaderEntry) {
        val prefs = getPrefs(context)
        val headers = getHeaders(context).toMutableList()
        if (index in headers.indices) {
            headers[index] = entry
            saveHeaders(prefs, KEY_HEADERS, headers)
        }
    }

    fun removeHeader(context: Context, index: Int) {
        val prefs = getPrefs(context)
        val headers = getHeaders(context).toMutableList()
        if (index in headers.indices) {
            headers.removeAt(index)
            saveHeaders(prefs, KEY_HEADERS, headers)
        }
    }


    // ─── 启用/禁用 ──────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ─── Cookie 快捷操作 ───────────────────────────────────────


    fun getCookie(context: Context): String {
        return getPrefs(context).getString(KEY_COOKIES, "") ?: ""
    }

    // ─── 构建 OkHttp Headers ───────────────────────────────────

    fun buildHeaderMap(context: Context): Map<String, String> {
        if (!isEnabled(context)) return emptyMap()
        val map = mutableMapOf<String, String>()
        getHeaders(context).forEach { entry ->
            if (entry.enabled && entry.key.isNotBlank()) {
                map[entry.key] = entry.value
            }
        }
        // Cookie 单独处理
        val cookie = getCookie(context)
        if (cookie.isNotBlank()) {
            map["Cookie"] = cookie
        }
        return map
    }

    // ─── 序列化 ────────────────────────────────────────────────

    private fun saveHeaders(prefs: SharedPreferences, key: String, headers: List<HeaderEntry>) {
        val arr = JSONArray()
        headers.forEach { h ->
            val obj = JSONObject()
            obj.put("key", h.key)
            obj.put("value", h.value)
            obj.put("enabled", h.enabled)
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadHeaders(prefs: SharedPreferences, key: String): List<HeaderEntry> {
        val json = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<HeaderEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(HeaderEntry(
                key = obj.optString("key", ""),
                value = obj.optString("value", ""),
                enabled = obj.optBoolean("enabled", true)
            ))
        }
        return list
    }
}
