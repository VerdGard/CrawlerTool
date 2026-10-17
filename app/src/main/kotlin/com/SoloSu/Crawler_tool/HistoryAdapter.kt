package com.SoloSu.Crawler_tool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

/**
 * 历史记录列表适配器。
 * 每条展示:模式徽标 + 结果数/相对时间 + 删除按钮 + URL + 表达式。
 */
class HistoryAdapter(
    private val onClick: (HistoryManager.HistoryItem) -> Unit,
    private val onDelete: (HistoryManager.HistoryItem) -> Unit
) : ListAdapter<HistoryManager.HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMode: TextView = itemView.findViewById(R.id.tvHistoryMode)
        private val tvCount: TextView = itemView.findViewById(R.id.tvHistoryCount)
        private val tvUrl: TextView = itemView.findViewById(R.id.tvHistoryUrl)
        private val tvExpr: TextView = itemView.findViewById(R.id.tvHistoryExpr)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteHistory)

        fun bind(item: HistoryManager.HistoryItem) {
            val ctx = itemView.context
            tvMode.text = item.mode
            tvCount.text = ctx.getString(
                R.string.history_count_suffix,
                item.resultCount,
                relativeTime(item.timestamp)
            )
            tvUrl.text = item.url
            tvExpr.text = item.expression
            itemView.setOnClickListener { onClick(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }

        private fun relativeTime(timestamp: Long): String {
            val ctx = itemView.context
            if (timestamp <= 0L) return ctx.getString(R.string.history_just_now)
            val diff = System.currentTimeMillis() - timestamp
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            return when {
                minutes < 1 -> ctx.getString(R.string.history_just_now)
                minutes < 60 -> ctx.getString(R.string.history_minutes_ago, minutes)
                hours < 24 -> ctx.getString(R.string.history_hours_ago, hours)
                else -> ctx.getString(R.string.history_days_ago, days)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryManager.HistoryItem>() {
        override fun areItemsTheSame(
            oldItem: HistoryManager.HistoryItem,
            newItem: HistoryManager.HistoryItem
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: HistoryManager.HistoryItem,
            newItem: HistoryManager.HistoryItem
        ) = oldItem == newItem
    }
}
