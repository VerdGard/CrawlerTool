package com.SoloSu.Crawler_tool

/**
 * 数据清洗与过滤引擎 (P2.10)
 * 关键词过滤、去重、排序
 */
object DataFilter {

    data class FilterOptions(
        val keyword: String = "",
        val filterMode: FilterMode = FilterMode.OFF,
        val dedup: Boolean = false,
        val sortMode: SortMode = SortMode.DEFAULT
    )

    enum class FilterMode { OFF, INCLUDE, EXCLUDE }
    enum class SortMode { DEFAULT, ALPHA, LENGTH }

    fun apply(items: List<String>, options: FilterOptions): List<String> {
        var result = items

        // 关键词过滤
        if (options.keyword.isNotBlank()) {
            result = when (options.filterMode) {
                FilterMode.INCLUDE -> result.filter { it.contains(options.keyword, ignoreCase = true) }
                FilterMode.EXCLUDE -> result.filter { !it.contains(options.keyword, ignoreCase = true) }
                FilterMode.OFF -> result
            }
        }

        // 去重
        if (options.dedup) {
            val seen = mutableSetOf<String>()
            result = result.filter { seen.add(it.trim()) }
        }

        // 排序
        result = when (options.sortMode) {
            SortMode.ALPHA -> result.sorted()
            SortMode.LENGTH -> result.sortedBy { it.length }
            SortMode.DEFAULT -> result
        }

        return result
    }

    fun dedupCount(items: List<String>): Int {
        return items.size - items.distinct().size
    }

    fun stats(items: List<String>): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        items.forEach { item ->
            val domain = UrlToolkit.extractDomain(item)
            stats[domain] = (stats[domain] ?: 0) + 1
        }
        return stats
    }
}
