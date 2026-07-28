package com.SoloSu.Crawler_tool.repository

import android.content.Context
import com.SoloSu.Crawler_tool.HistoryManager

class HistoryRepository(private val context: Context) {

    fun loadHistory(): List<HistoryManager.HistoryItem> = HistoryManager.loadAll(context)
    fun save(url: String, expression: String, mode: String, resultCount: Int) =
        HistoryManager.save(context, url, expression, mode, resultCount)
    fun delete(itemId: Long) = HistoryManager.delete(context, itemId)
    fun clearAll() = HistoryManager.clearAll(context)
}
