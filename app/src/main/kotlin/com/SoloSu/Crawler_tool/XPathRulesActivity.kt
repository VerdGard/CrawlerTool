package com.SoloSu.Crawler_tool

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.SoloSu.Crawler_tool.databinding.ActivityXpathRulesBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider

class XPathRulesActivity : BaseActivity() {

    private var _binding: ActivityXpathRulesBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val density by lazy { resources.displayMetrics.density }

    // 主题色缓存(随主题动态变化)
    private val themePrimary by lazy { MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0) }
    private val themeOnSurface by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0) }
    private val themeOnSurfaceVariant by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0) }
    private val themeSurfaceVariant by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0) }
    private val themeOutline by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0) }
    private val themeSurface by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0) }
    private val themeError by lazy { MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError, 0) }
    private val themePrimaryContainer by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0) }
    private val themeOnPrimaryContainer by lazy { MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityXpathRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadAndRender()
    }

    private fun loadAndRender() {
        try {
            val mdText = try {
                assets.open("xpath_rules.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                addParagraph(binding.contentContainer, "加载失败: ${e.message}")
                return
            }
            renderMarkdown(binding.contentContainer, mdText)
        } catch (e: Exception) {
            addParagraph(binding.contentContainer, "渲染错误: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun renderMarkdown(container: LinearLayout, md: String) {
        val lines = md.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // 代码块
                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    addCodeBlock(container, codeLines.joinToString("\n"))
                    i++
                }
                // 引用
                trimmed.startsWith("> ") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("> ")) {
                        quoteLines.add(lines[i].trim().removePrefix("> "))
                        i++
                    }
                    addBlockquote(container, quoteLines.joinToString("\n"))
                }
                // H1
                trimmed.startsWith("# ") && !trimmed.startsWith("## ") -> {
                    addHeading(container, trimmed.removePrefix("# ").trim(), 1)
                    i++
                }
                // H2
                trimmed.startsWith("## ") && !trimmed.startsWith("### ") -> {
                    addHeading(container, trimmed.removePrefix("## ").trim(), 2)
                    i++
                }
                // H3
                trimmed.startsWith("### ") -> {
                    addHeading(container, trimmed.removePrefix("### ").trim(), 3)
                    i++
                }
                // 表格
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    val tableRows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        val row = lines[i].trim()
                        if (row.matches(Regex("^\\|[\\s\\-:|]+\\|$"))) { i++; continue }
                        val cells = row.split("\\|".toRegex()).filter { it.isNotEmpty() }.map { it.trim() }
                        tableRows.add(cells)
                        i++
                    }
                    if (tableRows.isNotEmpty()) addTable(container, tableRows)
                }
                // 分隔线
                trimmed == "---" -> { addDivider(container); i++ }
                // 无序列表
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.startsWith("- ") || t.startsWith("* ")) {
                            items.add(t.removePrefix("- ").removePrefix("* ").trim())
                            i++
                        } else if (t.isEmpty()) {
                            i++
                            break
                        } else break
                    }
                    addList(container, items, false)
                }
                // 有序列表
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        val m = Regex("^\\d+\\.\\s(.*)").matchEntire(t)
                        if (m != null) {
                            items.add(m.groupValues[1])
                            i++
                        } else if (t.isEmpty()) {
                            i++
                            break
                        } else break
                    }
                    addList(container, items, true)
                }
                // 空行
                trimmed.isEmpty() -> { i++ }
                // 段落
                else -> {
                    val paragraphLines = mutableListOf(line)
                    i++
                    while (i < lines.size) {
                        val next = lines[i].trim()
                        if (next.isEmpty() || next.startsWith("```") || next.startsWith("## ") ||
                            next.startsWith("# ") || next.startsWith("|") || next.startsWith("---") ||
                            next.startsWith("> ") || next.startsWith("- ") || next.startsWith("* ") ||
                            next.matches(Regex("^\\d+\\.\\s.*"))) break
                        paragraphLines.add(lines[i])
                        i++
                    }
                    addParagraph(container, paragraphLines.joinToString("\n"))
                }
            }
        }
    }

    // ─── 工具方法 ───

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpf(value: Float): Float = value * density

    private fun makeBg(backgroundColor: Int, strokeColor: Int, radius: Float, leftTop: Boolean = true, rightTop: Boolean = true, leftBottom: Boolean = true, rightBottom: Boolean = true): GradientDrawable {
        return GradientDrawable().apply {
            setColor(backgroundColor)
            setStroke(dp(1), strokeColor)
            cornerRadii = floatArrayOf(
                if (leftTop) radius else 0f, if (leftTop) radius else 0f,
                if (rightTop) radius else 0f, if (rightTop) radius else 0f,
                if (rightBottom) radius else 0f, if (rightBottom) radius else 0f,
                if (leftBottom) radius else 0f, if (leftBottom) radius else 0f
            )
        }
    }

    // ─── 渲染方法 ───

    private fun addHeading(container: LinearLayout, text: String, level: Int) {
        val tv = TextView(this).apply {
            this.text = parseInline(text)
            setTextColor(themePrimary)
            typeface = Typeface.DEFAULT_BOLD
            setTextIsSelectable(true)
            textSize = when (level) {
                1 -> 20f
                2 -> 17f
                else -> 15f
            }
            setLineSpacing(dpf(2f), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(if (level == 1) 8 else if (level == 2) 12 else 16)
                bottomMargin = dp(6)
            }
        }
        container.addView(tv)
    }

    private fun addParagraph(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = parseInline(text)
            setTextColor(themeOnSurface)
            textSize = 14f
            setLineSpacing(dpf(4f), 1f)
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        container.addView(tv)
    }

    private fun addCodeBlock(container: LinearLayout, code: String) {
        // 代码块容器(FrameLayout 用于叠加复制按钮)
        val frame = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
            background = makeBg(themeSurfaceVariant, themeOutline, dp(6).toFloat())
        }

        val tv = TextView(this).apply {
            text = code
            setTextColor(themeOnSurface)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setLineSpacing(dpf(2f), 1f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextIsSelectable(true)
        }
        frame.addView(tv)

        // 复制按钮
        val btnCopy = TextView(this).apply {
            text = "复制"
            textSize = 11f
            setTextColor(themeOnPrimaryContainer)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = makeBg(themePrimaryContainer, themePrimaryContainer, dp(4).toFloat())
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            ).apply { setMargins(0, dp(4), dp(4), 0) }
            setOnClickListener {
                val clip = android.content.ClipData.newPlainText("XPath Code", code)
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(clip)
                text = "已复制 ✓"
                postDelayed({ text = "复制" }, 1500)
            }
        }
        btnCopy.isClickable = true
        btnCopy.isFocusable = true
        frame.addView(btnCopy)

        container.addView(frame)
    }

    private fun addBlockquote(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = parseInline(text)
            setTextColor(themeOnSurfaceVariant)
            textSize = 13f
            setLineSpacing(dpf(3f), 1f)
            setPadding(dp(16), dp(8), dp(12), dp(8))
            setTextIsSelectable(true)
            background = makeBg(themeSurfaceVariant, themeOutline, dp(4).toFloat(), leftTop = false, rightTop = true, leftBottom = false, rightBottom = true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        }
        container.addView(tv)
    }

    private fun addDivider(container: LinearLayout) {
        val divider = MaterialDivider(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
        }
        container.addView(divider)
    }

    private fun addList(container: LinearLayout, items: List<String>, ordered: Boolean) {
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val bullet = TextView(this).apply {
                text = if (ordered) "${index + 1}." else "•"
                setTextColor(themePrimary)
                textSize = 13f
                setPadding(0, 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.TOP
            }
            val content = TextView(this).apply {
                text = parseInline(item)
                setTextColor(themeOnSurface)
                textSize = 13f
                setLineSpacing(dpf(3f), 1f)
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(bullet)
            row.addView(content)
            listContainer.addView(row)
        }
        container.addView(listContainer)
    }

    private fun addTable(container: LinearLayout, rows: List<List<String>>) {
        if (rows.isEmpty()) return

        val scrollView = android.widget.HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        }

        val table = android.widget.TableLayout(this).apply {
            layoutParams = android.widget.TableLayout.LayoutParams(
                android.widget.TableLayout.LayoutParams.WRAP_CONTENT,
                android.widget.TableLayout.LayoutParams.WRAP_CONTENT
            )
            isStretchAllColumns = true
        }
        scrollView.addView(table)

        rows.forEachIndexed { rowIndex, cells ->
            val isHeader = rowIndex == 0
            val tableRow = android.widget.TableRow(this).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(
                    android.widget.TableLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.TableLayout.LayoutParams.WRAP_CONTENT
                )
            }
            cells.forEachIndexed { colIndex, cellText ->
                val tv = TextView(this).apply {
                    text = parseInline(cellText)
                    textSize = if (isHeader) 13f else 12f
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    gravity = Gravity.CENTER_VERTICAL
                    typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextIsSelectable(true)
                    val bgColor = if (isHeader) themePrimaryContainer
                        else if (rowIndex % 2 == 1) themeSurfaceVariant
                        else android.graphics.Color.TRANSPARENT
                    val textColor = if (isHeader) themeOnPrimaryContainer else themeOnSurface
                    setTextColor(textColor)
                    setBackgroundColor(bgColor)
                }
                tableRow.addView(tv)
            }
            table.addView(tableRow)
        }

        container.addView(scrollView)
    }

    private fun parseInline(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val codeStart = remaining.indexOf('`')
            val boldStart = remaining.indexOf("**")
            when {
                codeStart >= 0 && (boldStart < 0 || codeStart < boldStart) -> {
                    val codeEnd = remaining.indexOf('`', codeStart + 1)
                    if (codeEnd > codeStart) {
                        if (codeStart > 0) sb.append(remaining.substring(0, codeStart))
                        val code = remaining.substring(codeStart + 1, codeEnd)
                        val codeSpan = SpannableStringBuilder(code)
                        codeSpan.setSpan(ForegroundColorSpan(themeError), 0, code.length, 0)
                        codeSpan.setSpan(BackgroundColorSpan(themeSurfaceVariant), 0, code.length, 0)
                        codeSpan.setSpan(TypefaceSpan("monospace"), 0, code.length, 0)
                        sb.append(codeSpan)
                        remaining = remaining.substring(codeEnd + 1)
                    } else { sb.append(remaining[0]); remaining = remaining.substring(1) }
                }
                boldStart >= 0 -> {
                    val boldEnd = remaining.indexOf("**", boldStart + 2)
                    if (boldEnd > boldStart) {
                        if (boldStart > 0) sb.append(remaining.substring(0, boldStart))
                        val bold = remaining.substring(boldStart + 2, boldEnd)
                        val boldSpan = SpannableStringBuilder(bold)
                        boldSpan.setSpan(StyleSpan(Typeface.BOLD), 0, bold.length, 0)
                        sb.append(boldSpan)
                        remaining = remaining.substring(boldEnd + 2)
                    } else { sb.append(remaining[0]); remaining = remaining.substring(1) }
                }
                else -> { sb.append(remaining); remaining = "" }
            }
        }
        return sb
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
