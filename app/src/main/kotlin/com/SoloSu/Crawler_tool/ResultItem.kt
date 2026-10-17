package com.SoloSu.Crawler_tool

/**
 * 一条匹配结果。
 *
 * [index] 是全局序号(跨分页连续),用作 Compose 列表的稳定 key;
 * 展开状态由 UI 侧按 index 记录,不放进数据模型,避免数据层持有视图状态。
 */
data class ResultItem(
    val index: Int,
    val content: String,
)
