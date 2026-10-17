package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * 结果列表适配器。
 *
 * 视觉结构(自上而下):序号徽标 + 属性摘要 → 等宽内容块 → 展开/收起。
 * 内容块默认折叠为 3 行,长内容才出现展开入口。
 */
class ResultAdapter(
    private val onLongClickItem: ((item: ResultItem) -> Unit)? = null
) : ListAdapter<ResultItem, ResultAdapter.ViewHolder>(DiffCallback()) {

    companion object {
        private const val COLLAPSED_LINES = 3

        /** 语法高亮结果缓存:避免滚动时对同一内容重复跑正则。 */
        private val highlightCache =
            object : LinkedHashMap<String, WeakReference<CharSequence>>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, WeakReference<CharSequence>>
                ): Boolean = size > 32
            }

        private val TAG_BLOCK = Pattern.compile("<[^>]+>")
        private val TAG_TOKEN = Pattern.compile(
            "(</?)" +
                "|(/?>)" +
                "|([a-zA-Z][\\w-]*)" +
                "|(=\")" +
                "|(=\')" +
                "|(=\"[^\"]*\")" +
                "|(='[^']*')" +
                "|([\\w./#:-]+)"
        )
        private val ATTR_PATTERN = Pattern.compile("@(\\w[\\w-]*)")
        private val COMMON_ATTRS =
            listOf("href", "src", "title", "data-src", "data-original", "alt", "class", "id")

        fun clearHighlightCache() = synchronized(highlightCache) { highlightCache.clear() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val ctx: Context = itemView.context
        private val tagColor = ContextCompat.getColor(ctx, R.color.html_tag_color)
        private val attrColor = ContextCompat.getColor(ctx, R.color.html_attr_color)
        private val valueColor = ContextCompat.getColor(ctx, R.color.html_value_color)
        private val textColor = ContextCompat.getColor(ctx, R.color.html_text_color)

        private val cardItem: MaterialCardView = itemView.findViewById(R.id.cardItem)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvAttrTags: TextView = itemView.findViewById(R.id.tvAttrTags)
        private val btnExpand: TextView = itemView.findViewById(R.id.btnExpand)
        private val btnCopyItem: MaterialButton = itemView.findViewById(R.id.btnCopyItem)

        init {
            // 序号徽标:主色容器圆角底
            val badgeColor = MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorPrimaryContainer, 0
            )
            tvIndex.background = rounded(badgeColor, 8f)

            cardItem.setOnLongClickListener {
                onLongClickItem?.invoke(
                    getItem(bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: 0)
                )
                true
            }
        }

        private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = radiusDp * ctx.resources.displayMetrics.density
            }

        fun bind(item: ResultItem) {
            tvIndex.text = item.index.toString()
            tvContent.text = highlight(ctx, item.content)
            tvAttrTags.text = extractAttrTags(item.content)
            tvAttrTags.visibility = if (tvAttrTags.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val collapsible = estimateLineCount(item.content) > COLLAPSED_LINES
            if (collapsible) {
                btnExpand.visibility = View.VISIBLE
                applyExpanded(item.expanded)
                btnExpand.setOnClickListener {
                    item.expanded = !item.expanded
                    applyExpanded(item.expanded)
                }
            } else {
                btnExpand.visibility = View.GONE
                tvContent.maxLines = Int.MAX_VALUE
                tvContent.ellipsize = null
            }

            btnCopyItem.setOnClickListener {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Crawler Result", item.content))
                Toast.makeText(ctx, "已复制第 ${item.index} 条", Toast.LENGTH_SHORT).show()
            }
        }

        private fun applyExpanded(expanded: Boolean) {
            if (expanded) {
                tvContent.maxLines = Int.MAX_VALUE
                tvContent.ellipsize = null
                btnExpand.text = ctx.getString(R.string.result_collapse)
            } else {
                tvContent.maxLines = COLLAPSED_LINES
                tvContent.ellipsize = android.text.TextUtils.TruncateAt.END
                btnExpand.text = ctx.getString(R.string.result_expand)
            }
        }

        /** 粗略估算换行后的视觉行数,用于决定是否显示展开入口。 */
        private fun estimateLineCount(text: String): Int {
            if (text.isEmpty()) return 0
            return text.split('\n').sumOf { line ->
                if (line.isEmpty()) 1 else line.length / 40 + 1
            }
        }

        private fun extractAttrTags(content: String): String {
            val tags = LinkedHashSet<String>()
            val matcher = ATTR_PATTERN.matcher(content)
            while (matcher.find() && tags.size < 6) tags.add("@${matcher.group(1)}")

            // 表达式里含 text() 时补一个文本标记,与属性标签并列展示
            val mentionsText = content.contains("text()", true) ||
                content.contains("text=\"", true)
            if (mentionsText) tags.add("text()")

            if (tags.isEmpty()) {
                for (attr in COMMON_ATTRS) {
                    if (content.contains(attr, true)) {
                        tags.add("@$attr")
                        if (tags.size >= 6) break
                    }
                }
            }
            return tags.joinToString("  ")
        }

        private fun highlight(context: Context, raw: String): CharSequence {
            synchronized(highlightCache) {
                highlightCache[raw]?.get()?.let { return it }
            }
            val sb = SpannableStringBuilder()
            val matcher = TAG_BLOCK.matcher(raw)
            var lastEnd = 0
            while (matcher.find()) {
                if (matcher.start() > lastEnd) sb.append(raw, lastEnd, matcher.start())
                sb.append(colorizeTag(matcher.group()))
                lastEnd = matcher.end()
            }
            if (lastEnd < raw.length) sb.append(raw, lastEnd, raw.length)
            synchronized(highlightCache) { highlightCache[raw] = WeakReference(sb) }
            return sb
        }

        /** 对单个 `<...>` 片段着色:标签名=蓝、属性名=红、值=绿、括号=正文色。 */
        private fun colorizeTag(tag: String): CharSequence {
            val out = SpannableStringBuilder()
            val m = TAG_TOKEN.matcher(tag)
            var last = 0
            var pendingValue = false
            while (m.find()) {
                if (m.start() > last) out.append(tag, last, m.start())
                val bracketOpen = m.group(1)
                val bracketClose = m.group(2)
                val nameToken = m.group(3)
                val equalsQuote = m.group(4)
                val equalsApos = m.group(5)
                val quotedValue = m.group(6)
                val aposValue = m.group(7)
                val bareValue = m.group(8)

                when {
                    bracketOpen != null -> out.appendColored(bracketOpen, textColor)
                    bracketClose != null -> out.appendColored(bracketClose, textColor)
                    equalsQuote != null || equalsApos != null -> {
                        out.appendColored(if (equalsQuote != null) "=\"" else "='", textColor)
                        pendingValue = true
                    }
                    quotedValue != null -> {
                        out.appendColored(quotedValue, valueColor); pendingValue = false
                    }
                    aposValue != null -> {
                        out.appendColored(aposValue, valueColor); pendingValue = false
                    }
                    nameToken != null -> {
                        if (pendingValue) {
                            out.appendColored(nameToken, valueColor)
                            pendingValue = false
                        } else {
                            val before = tag.substring(0, m.start())
                            val isTagName = before.contains("<") &&
                                before.substringAfter('<').isBlank()
                            out.appendColored(nameToken, if (isTagName) tagColor else attrColor)
                        }
                    }
                    bareValue != null -> out.appendColored(bareValue, valueColor)
                }
                last = m.end()
            }
            if (last < tag.length) out.append(tag, last, tag.length)
            return out
        }

        private fun SpannableStringBuilder.appendColored(text: String, color: Int) {
            val start = length
            append(text)
            setSpan(ForegroundColorSpan(color), start, length, 0)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ResultItem>() {
        override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem) =
            oldItem.index == newItem.index

        override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem) =
            oldItem.content == newItem.content
    }
}
