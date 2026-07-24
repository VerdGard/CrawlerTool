package com.SoloSu.Crawler_tool

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 定时任务管理器 (P3.16)
 * 基于 AlarmManager 实现定时抓取并通知
 */
object ScheduleManager {

    private const val PREF_NAME = "crawler_schedules"
    private const val KEY_SCHEDULES = "schedule_list"
    private const val ACTION_SCHEDULE = "com.SoloSu.Crawler_tool.SCHEDULE_FETCH"

    data class ScheduleTask(
        val id: Long,
        val url: String,
        val expression: String,
        val mode: String,
        val intervalMinutes: Int,
        val enabled: Boolean = true,
        val lastRunTime: Long = 0,
        val lastResultCount: Int = 0
    )

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadTasks(context: Context): List<ScheduleTask> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_SCHEDULES, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<ScheduleTask>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(ScheduleTask(
                id = obj.optLong("id", 0),
                url = obj.optString("url", ""),
                expression = obj.optString("expression", ""),
                mode = obj.optString("mode", "XPath"),
                intervalMinutes = obj.optInt("intervalMinutes", 30),
                enabled = obj.optBoolean("enabled", true),
                lastRunTime = obj.optLong("lastRunTime", 0),
                lastResultCount = obj.optInt("lastResultCount", 0)
            ))
        }
        return list
    }

    fun addTask(context: Context, task: ScheduleTask) {
        val tasks = loadTasks(context).toMutableList()
        tasks.add(task)
        saveTasks(context, tasks)
        if (task.enabled) {
            scheduleAlarm(context, task)
        }
    }

    fun updateTask(context: Context, task: ScheduleTask) {
        val tasks = loadTasks(context).toMutableList()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            tasks[index] = task
            saveTasks(context, tasks)
            cancelAlarm(context, task.id)
            if (task.enabled) {
                scheduleAlarm(context, task)
            }
        }
    }

    fun removeTask(context: Context, taskId: Long) {
        val tasks = loadTasks(context).toMutableList()
        tasks.removeAll { it.id == taskId }
        saveTasks(context, tasks)
        cancelAlarm(context, taskId)
    }

    fun toggleTask(context: Context, taskId: Long): Boolean {
        val tasks = loadTasks(context).toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return false
        val old = tasks[index]
        tasks[index] = old.copy(enabled = !old.enabled)
        saveTasks(context, tasks)
        if (tasks[index].enabled) {
            scheduleAlarm(context, tasks[index])
        } else {
            cancelAlarm(context, taskId)
        }
        return tasks[index].enabled
    }

    private fun saveTasks(context: Context, tasks: List<ScheduleTask>) {
        val prefs = getPrefs(context)
        val arr = JSONArray()
        tasks.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("url", t.url)
            obj.put("expression", t.expression)
            obj.put("mode", t.mode)
            obj.put("intervalMinutes", t.intervalMinutes)
            obj.put("enabled", t.enabled)
            obj.put("lastRunTime", t.lastRunTime)
            obj.put("lastResultCount", t.lastResultCount)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
    }

    private fun scheduleAlarm(context: Context, task: ScheduleTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_SCHEDULE).apply {
            putExtra("task_id", task.id)
            putExtra("url", task.url)
            putExtra("expression", task.expression)
            putExtra("mode", task.mode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val intervalMs = task.intervalMinutes * 60 * 1000L
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            intervalMs,
            pendingIntent
        )
    }

    private fun cancelAlarm(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_SCHEDULE)
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 定时任务广播接收器
     */
    class ScheduleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val taskId = intent.getLongExtra("task_id", 0)
            val url = intent.getStringExtra("url") ?: return
            val expression = intent.getStringExtra("expression") ?: return
            val mode = intent.getStringExtra("mode") ?: "XPath"

            val matchMode = when (mode) {
                "CssSelector" -> CrawlerEngine.MatchMode.CssSelector
                "Regex" -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }

            val result = CrawlerEngine.fetchAndMatch(url, expression, matchMode)
            result.onSuccess { items ->
                val tasks = loadTasks(context).toMutableList()
                val idx = tasks.indexOfFirst { it.id == taskId }
                if (idx != -1) {
                    tasks[idx] = tasks[idx].copy(
                        lastRunTime = System.currentTimeMillis(),
                        lastResultCount = items.size
                    )
                    saveTasks(context, tasks)

                    // 发送通知
                    if (items.isNotEmpty()) {
                        NotificationHelper.showScheduleNotification(
                            context, taskId, url, items.size
                        )
                    }
                }
            }
        }
    }
}
