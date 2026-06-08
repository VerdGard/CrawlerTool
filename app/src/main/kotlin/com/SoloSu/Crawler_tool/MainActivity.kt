package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.SoloSu.Crawler_tool.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding) { "Binding已被销毁" }

    private val adapter = ResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupExpressionKeyboardAction()
        setupButtons()
        setupQuickAttrChips()

        // 初始提示
        binding.exprInputLayout.hint = "XPath表达式（如 //a/@href）"
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupRecyclerView() {
        binding.recyclerResult.adapter = adapter
    }

    private fun setupQuickAttrChips() {
        val chipClickListener: (android.view.View) -> Unit = { view ->
            if (view is Chip) {
                val chipText = view.text.toString()
                val editable = binding.etExpression.text
                if (editable != null) {
                    val cursorPos = binding.etExpression.selectionStart
                    if (cursorPos in 0..editable.length) {
                        editable.insert(cursorPos, chipText)
                        binding.etExpression.setSelection(cursorPos + chipText.length)
                    } else {
                        editable.append(chipText)
                        binding.etExpression.setSelection(editable.length)
                    }
                }
            }
        }

        binding.chipTitle.setOnClickListener(chipClickListener)
        binding.chipHref.setOnClickListener(chipClickListener)
        binding.chipDataOriginal.setOnClickListener(chipClickListener)
        binding.chipDataSrc.setOnClickListener(chipClickListener)
        binding.chipText.setOnClickListener(chipClickListener)
    }

    private fun setupExpressionKeyboardAction() {
        binding.etExpression.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                performMatch()
                true
            } else false
        }
    }

    private fun setupButtons() {
        binding.btnMatch.setOnClickListener { performMatch() }
        binding.btnClear.setOnClickListener {
            adapter.submitList(emptyList())
            binding.tvStatus.text = "已清空"
        }
        binding.btnCopyAll.setOnClickListener { copyRuleExpression() }
    }

    private fun performMatch() {
        val url = binding.etUrl.text.toString().trim()
        val expression = binding.etExpression.text.toString().trim()

        // 基本校验
        if (url.isBlank() || url == "https://") {
            Toast.makeText(this, "请输入有效的URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (expression.isBlank()) {
            Toast.makeText(this, "请输入匹配表达式", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnMatch.isEnabled = false
        binding.tvStatus.text = "正在请求并匹配..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CrawlerEngine.fetchAndMatch(
                    url = url,
                    expression = expression,
                    mode = CrawlerEngine.MatchMode.XPath
                )
            }

            binding.btnMatch.isEnabled = true

            result.fold(
                onSuccess = { items ->
                    if (items.isEmpty()) {
                        binding.tvStatus.text = "匹配完成，未找到结果"
                        adapter.submitList(emptyList())
                        Toast.makeText(this@MainActivity, "未匹配到任何结果", Toast.LENGTH_SHORT).show()
                    } else {
                        val resultItems = items.mapIndexed { index, content ->
                            ResultItem(index = index + 1, content = content)
                        }
                        adapter.submitList(resultItems)
                        binding.tvStatus.text = "匹配完成，共 ${items.size} 条结果"
                    }
                },
                onFailure = { error ->
                    binding.tvStatus.text = "匹配失败"
                    adapter.submitList(emptyList())
                    showErrorDialog("匹配失败", error.message ?: "未知错误")
                }
            )
        }
    }

    private fun copyRuleExpression() {
        val expression = binding.etExpression.text.toString().trim()
        if (expression.isEmpty()) {
            Toast.makeText(this, "规则表达式为空，无可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crawler Rule", expression)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制规则表达式", Toast.LENGTH_SHORT).show()
    }

    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("关于 Crawler Tool")
                    .setMessage("Crawler Tool v1.0\n\n一个轻量级网页数据爬取工具，支持 XPath 匹配方式，提供 HTML 结构查看与选择器生成功能。\n\nBy: SoloSu")
                    .setPositiveButton("确定", null)
                    .show()
                true
            }
            R.id.action_xpath_rules -> {
                showMdDoc("XPath规则 - 使用指南", "xpath_rules.md")
                true
            }
            R.id.action_settings -> {
                Toast.makeText(this, "设置开发中...", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 从assets读取MD文档，转HTML后用WebView显示在Dialog中
     */
    private fun showMdDoc(title: String, assetFileName: String) {
        lifecycleScope.launch {
            val bgColor = ContextCompat.getColor(this@MainActivity, R.color.md_background)
            val textColor = ContextCompat.getColor(this@MainActivity, R.color.md_on_background)
            val primaryColor = ContextCompat.getColor(this@MainActivity, R.color.md_primary)
            val surfaceVariant = ContextCompat.getColor(this@MainActivity, R.color.md_surface_variant)
            val htmlContent = withContext(Dispatchers.IO) {
                try {
                    val mdText = assets.open(assetFileName).bufferedReader().use { it.readText() }
                    mdToHtml(mdText, bgColor, textColor, primaryColor, surfaceVariant)
                } catch (e: Exception) {
                    "<html><body><h2>加载失败</h2><p>${e.message}</p></body></html>"
                }
            }

            val webView = android.webkit.WebView(this@MainActivity).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(bgColor)
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(title)
                .setView(webView)
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    /**
     * 将简单的 Markdown 文本转换为 HTML（跟随主题配色）
     */
    private fun mdToHtml(
        md: String,
        bgColor: Int,
        textColor: Int,
        primaryColor: Int,
        surfaceVariantColor: Int
    ): String {
        val bgHex = colorToHex(bgColor)
        val textHex = colorToHex(textColor)
        val primaryHex = colorToHex(primaryColor)
        val svHex = colorToHex(surfaceVariantColor)

        // 根据背景亮度选择表头文字颜色和边框颜色
        val isDark = android.R.color.black.let {
            val r = android.graphics.Color.red(bgColor)
            val g = android.graphics.Color.green(bgColor)
            val b = android.graphics.Color.blue(bgColor)
            (0.299 * r + 0.587 * g + 0.114 * b) < 128
        }
        val headerTextColor = if (isDark) "#111318" else "#FFFFFF"
        val borderColor = if (isDark) "#3C3C3E" else "#DEDEDE"
        val codeBg = if (isDark) "#38383A" else "#E0E8F5"
        val codeColor = if (isDark) "#EF5350" else "#D32F2F"
        val preBg = if (isDark) "#2C2C2E" else "#EDF2FA"
        val blockquoteBg = if (isDark) "#2A3A5A" else "#E0E8F5"
        val tableEvenBg = if (isDark) "#2C2C2E" else "#EDF2FA"

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, system-ui, sans-serif;
                        font-size: 14px;
                        line-height: 1.6;
                        color: $textHex;
                        padding: 8px 12px;
                        background: $bgHex;
                    }
                    h1 { font-size: 18px; color: $primaryHex; margin: 12px 0 8px; padding-bottom: 4px; border-bottom: 1px solid $borderColor; }
                    h2 { font-size: 16px; color: $primaryHex; margin: 10px 0 6px; }
                    h3 { font-size: 14px; color: $primaryHex; margin: 8px 0 4px; }
                    p { margin: 6px 0; }
                    code {
                        background: $codeBg;
                        color: $codeColor;
                        padding: 1px 5px;
                        border-radius: 3px;
                        font-family: monospace;
                        font-size: 12px;
                    }
                    pre {
                        background: $preBg;
                        border: 1px solid $borderColor;
                        border-radius: 6px;
                        padding: 8px 12px;
                        overflow-x: auto;
                        font-size: 12px;
                        line-height: 1.5;
                    }
                    pre code {
                        background: none;
                        color: $textHex;
                        padding: 0;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 8px 0;
                        font-size: 12px;
                    }
                    th {
                        background: $primaryHex;
                        color: $headerTextColor;
                        padding: 6px 8px;
                        text-align: left;
                    }
                    td {
                        border: 1px solid $borderColor;
                        padding: 5px 8px;
                    }
                    tr:nth-child(even) { background: $tableEvenBg; }
                    blockquote {
                        border-left: 3px solid $primaryHex;
                        margin: 8px 0;
                        padding: 4px 12px;
                        background: $blockquoteBg;
                        border-radius: 0 4px 4px 0;
                    }
                    ul, ol { margin: 4px 0; padding-left: 20px; }
                    li { margin: 2px 0; }
                </style>
            </head>
            <body>
        """.trimIndent())

        // 简易 Markdown → HTML 转换
        val lines = md.split("\n")
        var inCodeBlock = false
        var inTable = false
        var tableSb = StringBuilder()
        var isFirstTableRow = true

        for (line in lines) {
            when {
                line.trimStart().startsWith("```") -> {
                    if (inCodeBlock) {
                        sb.append("</code></pre>\n")
                        inCodeBlock = false
                    } else {
                        sb.append("<pre><code>")
                        inCodeBlock = true
                    }
                    continue
                }
                inCodeBlock -> {
                    sb.append(escapeHtml(line)).append("\n")
                    continue
                }
                line.trimStart().startsWith("|") && line.trimEnd().endsWith("|") -> {
                    val cells = line.split("|").filter { it.isNotBlank() }
                    if (cells.isEmpty() || cells.all { it.trim().all { c -> c == '-' || c == ':' } }) {
                        continue
                    }
                    if (!inTable) {
                        inTable = true
                        isFirstTableRow = true
                        tableSb = StringBuilder("<table>")
                    }
                    if (isFirstTableRow) {
                        tableSb.append("<thead><tr>")
                        for (cell in cells) {
                            tableSb.append("<th>${escapeHtml(cell.trim())}</th>")
                        }
                        tableSb.append("</tr></thead><tbody>")
                        isFirstTableRow = false
                    } else {
                        tableSb.append("<tr>")
                        for (cell in cells) {
                            tableSb.append("<td>${escapeHtml(cell.trim())}</td>")
                        }
                        tableSb.append("</tr>")
                    }
                    continue
                }
                line.isBlank() && inTable -> {
                    inTable = false
                    tableSb.append("</tbody></table>")
                    sb.append(tableSb.toString())
                    tableSb = StringBuilder()
                    continue
                }
                inTable -> {
                    continue
                }
            }

            when {
                line.startsWith("### ") -> sb.append("<h3>${escapeHtml(line.removePrefix("### "))}</h3>\n")
                line.startsWith("## ") -> sb.append("<h2>${escapeHtml(line.removePrefix("## "))}</h2>\n")
                line.startsWith("# ") -> sb.append("<h1>${escapeHtml(line.removePrefix("# "))}</h1>\n")
                line.startsWith("> ") -> sb.append("<blockquote>${escapeHtml(line.removePrefix("> "))}</blockquote>\n")
                line.startsWith("- ") || line.startsWith("* ") -> sb.append("<li>${escapeHtml(line.removePrefix("- ").removePrefix("* "))}</li>\n")
                line.matches(Regex("^\\d+\\.\\s.*")) -> sb.append("<li>${escapeHtml(line.replaceFirst(Regex("^\\d+\\.\\s"), ""))}</li>\n")
                line.isBlank() -> sb.append("<br/>\n")
                else -> {
                    var processed = escapeHtml(line)
                    processed = processed.replace(Regex("`([^`]+)`")) { match ->
                        "<code>${match.groupValues[1]}</code>"
                    }
                    processed = processed.replace(Regex("\\*\\*(.+?)\\*\\*")) { match ->
                        "<strong>${match.groupValues[1]}</strong>"
                    }
                    sb.append("<p>$processed</p>\n")
                }
            }
        }

        if (inTable) {
            tableSb.append("</tbody></table>")
            sb.append(tableSb.toString())
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun escapeHtml(text: String): String {
        val amp = '&'.toString()
        val lt = amp + "lt;"
        val gt = amp + "gt;"
        val quot = amp + "quot;"
        return text
            .replace(amp, amp + "amp;")
            .replace("<", lt)
            .replace(">", gt)
            .replace("\"", quot)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
