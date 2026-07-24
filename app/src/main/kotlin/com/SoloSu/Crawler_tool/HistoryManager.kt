package com.SoloSu.Crawler_tool

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 历史记录 & 收藏夹管理器 (P0.1)
 * 自动保存 URL + 表达式 + 匹配模式，支持星标收藏
 */
object HistoryManager {

    private const val PREF_NAME = "crawler_history"
    private const val KEY_HISTORY = "history_items"
    private const val KEY_FAVORITES = "favorite_items"
    private const val MAX_HISTORY = 100

    data class HistoryItem(
        val id: Long,
        val url: String,
        val expression: String,
        val mode: String,
        val timestamp: Long,
        val isFavorite: Boolean = false,
        val resultCount: Int = 0
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ─── 保存历史 ──────────────────────────────────────────────

    fun save(context: Context, url: String, expression: String, mode: String, resultCount: Int) {
        val prefs = getPrefs(context)
        val items = loadAll(context).toMutableList()

        // 移除完全相同的重复项
        items.removeAll { it.url == url && it.expression == expression && it.mode == mode }

        val item = HistoryItem(
            id = System.currentTimeMillis(),
            url = url,
            expression = expression,
            mode = mode,
            timestamp = System.currentTimeMillis(),
            isFavorite = false,
            resultCount = resultCount
        )
        items.add(0, item)

        // 限制最大数量
        val trimmed = items.take(MAX_HISTORY)
        saveItems(prefs, KEY_HISTORY, trimmed)
    }

    // ─── 加载 ──────────────────────────────────────────────────

    fun loadAll(context: Context): List<HistoryItem> {
        val prefs = getPrefs(context)
        return loadItems(prefs, KEY_HISTORY)
    }

    fun loadFavorites(context: Context): List<HistoryItem> {
        return loadAll(context).filter { it.isFavorite }
    }

    // ─── 收藏切换 ──────────────────────────────────────────────

    fun toggleFavorite(context: Context, itemId: Long): Boolean {
        val prefs = getPrefs(context)
        val items = loadAll(context).toMutableList()
        val index = items.indexOfFirst { it.id == itemId }
        if (index == -1) return false
        val old = items[index]
        items[index] = old.copy(isFavorite = !old.isFavorite)
        saveItems(prefs, KEY_HISTORY, items)
        return items[index].isFavorite
    }

    // ─── 清空 ──────────────────────────────────────────────────

    fun clearAll(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun clearFavorites(context: Context) {
        val prefs = getPrefs(context)
        val items = loadAll(context).filter { !it.isFavorite }
        saveItems(prefs, KEY_HISTORY, items)
    }

    // ─── 序列化 ────────────────────────────────────────────────

    private fun saveItems(prefs: SharedPreferences, key: String, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("url", item.url)
            obj.put("expression", item.expression)
            obj.put("mode", item.mode)
            obj.put("timestamp", item.timestamp)
            obj.put("isFavorite", item.isFavorite)
            obj.put("resultCount", item.resultCount)
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadItems(prefs: SharedPreferences, key: String): List<HistoryItem> {
        val json = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<HistoryItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(HistoryItem(
                id = obj.optLong("id", System.currentTimeMillis()),
                url = obj.optString("url", ""),
                expression = obj.optString("expression", ""),
                mode = obj.optString("mode", "XPath"),
                timestamp = obj.optLong("timestamp", 0),
                isFavorite = obj.optBoolean("isFavorite", false),
                resultCount = obj.optInt("resultCount", 0)
            ))
        }
        return list
    }

    // ─── 删除单条 ──────────────────────────────────────────────

    fun delete(context: Context, itemId: Long) {
        val prefs = getPrefs(context)
        val items = loadAll(context).toMutableList()
        items.removeAll { it.id == itemId }
        saveItems(prefs, KEY_HISTORY, items)
    }
}
