package com.SoloSu.Crawler_tool

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 通知辅助工具 (用于定时任务通知)
 */
object NotificationHelper {

    private const val CHANNEL_ID = "crawler_schedule"
    private const val CHANNEL_NAME = "定时抓取"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "定时抓取任务完成通知"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

}
