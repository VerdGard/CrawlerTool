package com.SoloSu.Crawler_tool

/**
 * 设置项的展示文案。
 *
 * 原先挂在 SettingsActivity 的 companion 上,设置页并入底部导航后
 * Activity 不再存在,改为独立对象,供设置页与批量面板共用。
 */
object SettingsActivityLabels {

    /** 请求间隔的展示文案。 */
    fun formatDelay(ms: Long): String = when (ms) {
        0L -> "无延迟"
        500L -> "0.5秒"
        1000L -> "1秒"
        2000L -> "2秒"
        5000L -> "5秒"
        else -> "${ms}ms"
    }
}
