package com.SoloSu.Crawler_tool

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多标签页管理器 (P3.14)
 * 每个标签页保存独立的 URL、表达式、模式、结果
 */
object TabManager {

    private const val PREF_NAME = "crawler_tabs"
    private const val KEY_TABS = "tab_list"
    private const val KEY_ACTIVE = "active_tab"
    private const val MAX_TABS = 8

    data class TabData(
        val id: Int,
        val title: String = "新标签",
        val url: String = "",
        val expression: String = "",
        val mode: String = "XPath",
        val results: List<String> = emptyList(),
        val resultCount: Int = 0
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadTabs(context: Context): List<TabData> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_TABS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<TabData>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TabData(
                id = obj.optInt("id", 0),
                title = obj.optString("title", "新标签"),
                url = obj.optString("url", ""),
                expression = obj.optString("expression", ""),
                mode = obj.optString("mode", "XPath"),
                resultCount = obj.optInt("resultCount", 0)
            ))
        }
        return list
    }

    fun getActiveTabId(context: Context): Int {
        return getPrefs(context).getInt(KEY_ACTIVE, 0)
    }

    fun setActiveTabId(context: Context, id: Int) {
        getPrefs(context).edit().putInt(KEY_ACTIVE, id).apply()
    }

    fun createTab(context: Context): TabData {
        val tabs = loadTabs(context).toMutableList()
        val newId = (tabs.maxOfOrNull { it.id } ?: 0) + 1
        val tab = TabData(id = newId, title = "标签 $newId")
        tabs.add(tab)
        saveTabs(context, tabs)
        setActiveTabId(context, newId)
        return tab
    }

    fun updateTab(context: Context, tab: TabData) {
        val tabs = loadTabs(context).toMutableList()
        val index = tabs.indexOfFirst { it.id == tab.id }
        if (index != -1) {
            tabs[index] = tab
            saveTabs(context, tabs)
        }
    }

    fun closeTab(context: Context, tabId: Int) {
        val tabs = loadTabs(context).toMutableList()
        tabs.removeAll { it.id == tabId }
        saveTabs(context, tabs)
        // 如果关闭的是当前标签，切换到最后一个
        if (getActiveTabId(context) == tabId) {
            val lastId = tabs.lastOrNull()?.id ?: 0
            setActiveTabId(context, lastId)
        }
    }

    fun canCreateTab(context: Context): Boolean {
        return loadTabs(context).size < MAX_TABS
    }

    private fun saveTabs(context: Context, tabs: List<TabData>) {
        val prefs = getPrefs(context)
        val arr = JSONArray()
        tabs.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("url", t.url)
            obj.put("expression", t.expression)
            obj.put("mode", t.mode)
            obj.put("resultCount", t.resultCount)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_TABS, arr.toString()).apply()
    }
}
