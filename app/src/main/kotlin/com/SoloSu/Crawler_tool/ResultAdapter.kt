package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference
import java.util.regex.Pattern

class ResultAdapter(
    private val onLongClickHtml: ((htmlContent: String) -> Unit)? = null
) : ListAdapter<ResultItem, ResultAdapter.ViewHolder>(DiffCallback()) {

    companion object {
        // 内容高亮缓存：对相同 HTML 内容缓存 SpannableString，避免每次 bind 重新解析正则
        private val highlightCache = object : LinkedHashMap<String, WeakReference<CharSequence>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeakReference<CharSequence>>): Boolean {
                return size > 32
            }
        }
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
        // 预解析颜色值，避免每次 bind 重复调用 ContextCompat.getColor
        private val tagColor: Int
        private val attrColor: Int
        private val valueColor: Int
        private val textColor: Int

        private val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val tvAttrTags: TextView = itemView.findViewById(R.id.tvAttrTags)
        private val btnExpand: TextView = itemView.findViewById(R.id.btnExpand)
        private val btnCopyItem: MaterialButton = itemView.findViewById(R.id.btnCopyItem)
        private val cardItem: MaterialCardView = itemView.findViewById(R.id.cardItem)

        init {
            val ctx = itemView.context
            tagColor = ContextCompat.getColor(ctx, R.color.html_tag_color)
            attrColor = ContextCompat.getColor(ctx, R.color.html_attr_color)
            valueColor = ContextCompat.getColor(ctx, R.color.html_value_color)
            textColor = ContextCompat.getColor(ctx, R.color.html_text_color)
        }

        fun bind(item: ResultItem) {
            tvIndex.text = "${item.index}"
            tvContent.text = highlightHtmlContent(tvContent.context, item.content)

            val attrTags = extractAttrTags(item.content)
            tvAttrTags.text = attrTags

            val contentLineCount = estimateLineCount(item.content)
            val needsExpand = contentLineCount > 3

            if (needsExpand) {
                btnExpand.visibility = View.VISIBLE
                if (item.expanded) {
                    tvContent.maxLines = Int.MAX_VALUE
                    btnExpand.text = "收起"
                } else {
                    tvContent.maxLines = 3
                    btnExpand.text = "展开更多内容"
                }
            } else {
                btnExpand.visibility = View.GONE
                tvContent.maxLines = Int.MAX_VALUE
            }

            btnExpand.setOnClickListener {
                item.expanded = !item.expanded
                if (item.expanded) {
                    tvContent.maxLines = Int.MAX_VALUE
                    btnExpand.text = "收起"
                } else {
                    tvContent.maxLines = 3
                    btnExpand.text = "展开更多内容"
                }
            }

            btnCopyItem.setOnClickListener {
                copyToClipboard(itemView.context, item.content, "[${item.index}]")
            }

            cardItem.setOnLongClickListener {
                val content = item.content
                if (isProbablyHtml(content)) {
                    HtmlTreeDialog.show(cardItem.context, content)
                } else {
                    onLongClickHtml?.invoke(content)
                }
                true
            }
        }

        private fun copyToClipboard(context: Context, text: String, label: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Crawler Result", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已复制: $label", Toast.LENGTH_SHORT).show()
        }

        private fun estimateLineCount(text: String): Int {
            if (text.isEmpty()) return 0
            val lines = text.split("\n")
            var totalLines = 0
            for (line in lines) {
                if (line.isEmpty()) {
                    totalLines += 1
                } else {
                    totalLines += (line.length / 40) + 1
                }
            }
            return totalLines
        }

        private fun extractAttrTags(content: String): String {
            val tags = mutableSetOf<String>()
            val attrPattern = Pattern.compile("@(\\w[\\w-]*)")
            val attrMatcher = attrPattern.matcher(content)
            while (attrMatcher.find()) {
                tags.add("@${attrMatcher.group(1)}")
            }
            if (content.contains("text()", ignoreCase = true) ||
                content.contains("text=\"", ignoreCase = true)
            ) {
                tags.add("text()")
            }
            if (tags.isEmpty()) {
                val commonAttrs = listOf("title", "href", "src", "data-src", "data-original", "alt", "class", "id")
                for (attr in commonAttrs) {
                    if (content.contains(attr, ignoreCase = true)) {
                        tags.add("@$attr")
                        if (tags.size >= 6) break
                    }
                }
            }
            return if (tags.isEmpty()) "" else tags.take(6).joinToString("  ")
        }

        private fun highlightHtmlContent(context: Context, rawContent: String): CharSequence {
            // 使用 ViewHolder 初始化时预解析的颜色值
            val sb = SpannableStringBuilder()
            synchronized(highlightCache) {
                val cached = highlightCache[rawContent]?.get()
                if (cached != null) return cached
            }

            val tagBlockPattern = Pattern.compile("<[^>]+>")
            val matcher = tagBlockPattern.matcher(rawContent)
            var lastEnd = 0

            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    sb.append(rawContent.substring(lastEnd, matcher.start()))
                }

                val fullTag = matcher.group()
                val innerSb = SpannableStringBuilder()
                val innerPattern = Pattern.compile(
                    "(</?)" +
                    "|(/?>)" +
                    "|([a-zA-Z][\\w-]*)" +
                    "|(=\")" +
                    "|(=\')" +
                    "|(=\"[^\"]*\")" +
                    "|(='[^']*')" +
                    "|([\\w./#:-]+)"
                )
                val innerMatcher = innerPattern.matcher(fullTag)
                var innerLast = 0
                var afterEqualsQuote = false

                while (innerMatcher.find()) {
                    if (innerMatcher.start() > innerLast) {
                        val between = fullTag.substring(innerLast, innerMatcher.start())
                        innerSb.append(between)
                    }

                    val bracketOpen = innerMatcher.group(1)
                    val bracketClose = innerMatcher.group(2)
                    val nameToken = innerMatcher.group(3)
                    val equalsQuote = innerMatcher.group(4)
                    val equalsApos = innerMatcher.group(5)
                    val quotedValue = innerMatcher.group(6)
                    val aposValue = innerMatcher.group(7)
                    val bareValue = innerMatcher.group(8)

                    when {
                        bracketOpen != null -> {
                            val s = SpannableStringBuilder(bracketOpen)
                            s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                            innerSb.append(s)
                        }
                        bracketClose != null -> {
                            val s = SpannableStringBuilder(bracketClose)
                            s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                            innerSb.append(s)
                        }
                        afterEqualsQuote -> {
                            if (nameToken != null) {
                                val s = SpannableStringBuilder(nameToken)
                                s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                                innerSb.append(s)
                            } else if (bareValue != null) {
                                val s = SpannableStringBuilder(bareValue)
                                s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                                innerSb.append(s)
                            } else if (quotedValue != null) {
                                val s = SpannableStringBuilder(quotedValue)
                                s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                                innerSb.append(s)
                            } else if (aposValue != null) {
                                val s = SpannableStringBuilder(aposValue)
                                s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                                innerSb.append(s)
                            }
                            afterEqualsQuote = false
                        }
                        equalsQuote != null || equalsApos != null -> {
                            val eq = if (equalsQuote != null) "=\"" else "='"
                            val s = SpannableStringBuilder(eq)
                            s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                            innerSb.append(s)
                            afterEqualsQuote = true
                        }
                        quotedValue != null -> {
                            val s = SpannableStringBuilder(quotedValue)
                            s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                            innerSb.append(s)
                        }
                        aposValue != null -> {
                            val s = SpannableStringBuilder(aposValue)
                            s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                            innerSb.append(s)
                        }
                        nameToken != null -> {
                            val before = fullTag.substring(0, innerMatcher.start())
                            val containsOpeningBracket = before.contains("<")
                            val hasPreviousAttrOrTag = containsOpeningBracket &&
                                !before.matches(Regex("^\\s*<\\s*\$"))
                            if (hasPreviousAttrOrTag) {
                                val s = SpannableStringBuilder(nameToken)
                                s.setSpan(ForegroundColorSpan(attrColor), 0, s.length, 0)
                                innerSb.append(s)
                            } else {
                                val s = SpannableStringBuilder(nameToken)
                                s.setSpan(ForegroundColorSpan(tagColor), 0, s.length, 0)
                                innerSb.append(s)
                            }
                        }
                        bareValue != null -> {
                            val s = SpannableStringBuilder(bareValue)
                            s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                            innerSb.append(s)
                        }
                    }
                    innerLast = innerMatcher.end()
                }
                if (innerLast < fullTag.length) {
                    innerSb.append(fullTag.substring(innerLast))
                }
                sb.append(innerSb)
                lastEnd = matcher.end()
            }
            if (lastEnd < rawContent.length) {
                sb.append(rawContent.substring(lastEnd))
            }
            synchronized(highlightCache) {
                highlightCache[rawContent] = WeakReference(sb)
            }
            return sb
        }

        private fun isProbablyHtml(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            return trimmed.contains("<") && trimmed.contains(">") &&
                (trimmed.contains("</") ||
                 trimmed.matches(Regex(".*<[a-zA-Z!?][^>]*>.*")) ||
                 trimmed.startsWith("<!") ||
                 trimmed.startsWith("<?xml"))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ResultItem>() {
        override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
            return oldItem.index == newItem.index
        }

        override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
            return oldItem == newItem
        }
    }


}
