package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val adapter = ResultAdapter(
        onLongClickHtml = { html -> showHtmlPreview(html) }
    )

    private var currentMode: CrawlerEngine.MatchMode = CrawlerEngine.MatchMode.XPath
    private var allResults = mutableListOf<String>()
    private var currentPage = 0
    private val pageSize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeSetting()
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupModeSelector()
        setupExpressionKeyboardAction()
        setupButtons()
        setupQuickAttrChips()
        setupBatchMode()

        updateExpressionHint()
    }

    private fun applyThemeSetting() {
        // 必须同步设置 AppCompatDelegate 的夜间模式,确保资源(如 @color/md_surface)正确解析
        val theme = SettingsActivity.getTheme(this)
        when (theme) {
            SettingsActivity.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                setTheme(R.style.AppTheme_Light)
            }
            SettingsActivity.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                setTheme(R.style.AppTheme_Dark)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupRecyclerView() {
        binding.recyclerResult.adapter = adapter
    }

    private fun setupModeSelector() {
        binding.modeChipGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = when (checkedId) {
                R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector
                R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }
            updateExpressionHint()
            updateQuickAttrVisibility()
            // 选中芯片动画
            val chip = binding.modeChipGroup.findViewById<com.google.android.material.chip.Chip>(checkedId)
            chip?.animate()?.scaleXBy(0.1f)?.scaleYBy(0.1f)?.setDuration(100)?.withEndAction {
                chip?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
            }?.start()
        }
    }

    private fun updateExpressionHint() {
        val hint = when (currentMode) {
            is CrawlerEngine.MatchMode.XPath -> getString(R.string.hint_xpath)
            is CrawlerEngine.MatchMode.CssSelector -> getString(R.string.hint_css)
            is CrawlerEngine.MatchMode.Regex -> getString(R.string.hint_regex)
        }
        binding.exprInputLayout.hint = hint
        // 输入框切换动画
        binding.etExpression.animate()
            .alpha(0.5f).setDuration(80)
            .withEndAction {
                binding.etExpression.animate().alpha(1f).setDuration(120).start()
            }.start()
    }

    private fun updateQuickAttrVisibility() {
        val isXPath = currentMode is CrawlerEngine.MatchMode.XPath
        val parent = binding.quickAttrChipGroup.parent.parent as ViewGroup
        val transition = android.transition.AutoTransition()
        transition.duration = 250
        android.transition.TransitionManager.beginDelayedTransition(parent, transition)
        binding.tvQuickAttrs.visibility = if (isXPath) View.VISIBLE else View.GONE
        binding.quickAttrScroll.visibility = if (isXPath) View.VISIBLE else View.GONE
    }

    private fun setupQuickAttrChips() {
        val chipClickListener: (View) -> Unit = { view ->
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

    private fun setupBatchMode() {
        binding.btnBatch.setOnClickListener {
            val isVisible = binding.batchSection.visibility == View.VISIBLE
            if (isVisible) {
                collapseView(binding.batchSection)
            } else {
                expandView(binding.batchSection)
            }
    }
    }


    private fun expandView(view: View) {
        val parent = view.parent as ViewGroup
        val transition = android.transition.AutoTransition()
        transition.duration = 250
        transition.interpolator = android.view.animation.OvershootInterpolator(0.4f)
        android.transition.TransitionManager.beginDelayedTransition(parent, transition)
        view.visibility = View.VISIBLE
    }

    private fun collapseView(view: View) {
        val parent = view.parent as ViewGroup
        val transition = android.transition.AutoTransition()
        transition.duration = 200
        android.transition.TransitionManager.beginDelayedTransition(parent, transition)
        view.visibility = View.GONE
    }

    private fun setupButtons() {
        binding.btnMatch.setOnClickListener { performMatch() }
        binding.btnClear.setOnClickListener {
            clearResults()
        }
        binding.btnCopyAll.setOnClickListener { copyRuleExpression() }
        binding.btnPrevPage.setOnClickListener { goToPage(currentPage - 1) }
        binding.btnNextPage.setOnClickListener { goToPage(currentPage + 1) }
    }

    private fun performMatch() {
        val url = binding.etUrl.text.toString().trim()
        val expression = binding.etExpression.text.toString().trim()

        if (url.isBlank() || url == "https://") {
            Toast.makeText(this, getString(R.string.error_no_url), Toast.LENGTH_SHORT).show()
            return
        }
        if (expression.isBlank()) {
            Toast.makeText(this, getString(R.string.error_no_expression), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnMatch.isEnabled = false
        binding.tvStatus.text = getString(R.string.status_fetching)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CrawlerEngine.fetchAndMatch(
                    url = url,
                    expression = expression,
                    mode = currentMode
                )
            }

            binding.btnMatch.isEnabled = true

            result.fold(
                onSuccess = { items ->
                    allResults = items.toMutableList()
                    currentPage = 0
                    if (items.isEmpty()) {
                        binding.tvStatus.text = getString(R.string.status_no_result)
                        adapter.submitList(emptyList())
                        hidePagination()
                        Toast.makeText(this@MainActivity, R.string.status_no_result, Toast.LENGTH_SHORT).show()
                    } else {
                        showPage(0)
                        binding.tvStatus.text = getString(R.string.status_results, items.size)
                    }
                },
                onFailure = { error ->
                    binding.tvStatus.text = getString(R.string.status_failed)
                    adapter.submitList(emptyList())
                    hidePagination()
                    showErrorDialog(getString(R.string.status_failed), error.message ?: "未知错误")
                }
            )
        }
    }

    private fun performBatchMatch() {
        val expression = binding.etExpression.text.toString().trim()
        val startPage = binding.etBatchStart.text.toString().toIntOrNull() ?: 1
        val endPage = binding.etBatchEnd.text.toString().toIntOrNull() ?: 1
        val urlPattern = binding.etBatchUrlPattern.text.toString().trim()

        if (expression.isBlank()) {
            Toast.makeText(this, getString(R.string.error_no_expression), Toast.LENGTH_SHORT).show()
            return
        }
        if (urlPattern.isBlank()) {
            Toast.makeText(this, "请输入URL模板", Toast.LENGTH_SHORT).show()
            return
        }
        if (startPage > endPage) {
            Toast.makeText(this, "起始页码不能大于结束页码", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnMatch.isEnabled = false
        binding.btnBatch.isEnabled = false
        allResults.clear()
        currentPage = 0

        lifecycleScope.launch {
            var totalResults = 0
            for (page in startPage..endPage) {
                val url = urlPattern.replace("{page}", page.toString())
                binding.tvStatus.text = getString(R.string.batch_progress, page - startPage + 1, endPage - startPage + 1)

                val result = withContext(Dispatchers.IO) {
                    CrawlerEngine.fetchAndMatch(url, expression, currentMode)
                }

                result.fold(
                    onSuccess = { items ->
                        allResults.addAll(items)
                        totalResults += items.size
                    },
                    onFailure = { error ->
                        // 继续抓取下一页
                        println("Page $page failed: ${error.message}")
                    }
                )
            }

            binding.btnMatch.isEnabled = true
            binding.btnBatch.isEnabled = true

            if (allResults.isEmpty()) {
                binding.tvStatus.text = getString(R.string.status_no_result)
                adapter.submitList(emptyList())
                hidePagination()
            } else {
                showPage(0)
                binding.tvStatus.text = getString(R.string.batch_complete, allResults.size)
            }
        }
    }

    private fun clearResults() {
        allResults.clear()
        currentPage = 0
        adapter.submitList(emptyList())
        binding.tvStatus.text = getString(R.string.status_cleared)
        hidePagination()
    }

    private fun showPage(page: Int) {
        if (allResults.isEmpty()) {
            adapter.submitList(emptyList())
            hidePagination()
            return
        }

        val totalPages = (allResults.size + pageSize - 1) / pageSize
        val start = page * pageSize
        val end = minOf(start + pageSize, allResults.size)

        if (start >= allResults.size) {
            goToPage(0)
            return
        }

        val pageItems = allResults.subList(start, end).mapIndexed { index, content ->
            ResultItem(index = start + index + 1, content = content)
        }

        adapter.submitList(pageItems)
        currentPage = page

        if (totalPages > 1) {
            showPagination()
            binding.tvPageInfo.text = "${page + 1}/$totalPages"
            binding.btnPrevPage.isEnabled = page > 0
            binding.btnNextPage.isEnabled = page < totalPages - 1
        } else {
            hidePagination()
        }
    }

    private fun goToPage(page: Int) {
        showPage(page)
    }

    private fun showPagination() {
        binding.paginationBar.visibility = View.VISIBLE
    }

    private fun hidePagination() {
        binding.paginationBar.visibility = View.GONE
    }

    private fun copyRuleExpression() {
        val expression = binding.etExpression.text.toString().trim()
        if (expression.isEmpty()) {
            Toast.makeText(this, "规则表达式为空,无可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crawler Rule", expression)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制规则表达式", Toast.LENGTH_SHORT).show()
    }

    private fun showExportDialog() {
        if (allResults.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val formats = arrayOf("JSON", "CSV", "纯文本")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_title))
            .setItems(formats) { _, which ->
                val format = when (which) {
                    0 -> "json"
                    1 -> "csv"
                    else -> "text"
                }
                exportResults(format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportResults(format: String) {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.Default) {
                CrawlerEngine.exportResults(allResults, format)
            }

            val extension = when (format) {
                "json" -> "json"
                "csv" -> "csv"
                else -> "txt"
            }
            val fileName = "Crawler_Results_${System.currentTimeMillis()}.$extension"

            // 写入缓存目录
            try {
                val file = java.io.File(cacheDir, fileName)
                file.writeText(content)
                Toast.makeText(this@MainActivity, getString(R.string.export_success, file.absolutePath), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showHtmlPreview(html: String) {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.WHITE)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 设置深色模式支持
                }
            }
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.preview_html))
            .setView(webView)
            .setPositiveButton("关闭", null)
            .show()
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
            R.id.action_export -> {
                showExportDialog()
                true
            }
            R.id.action_about -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage(getString(R.string.about_message))
                    .setPositiveButton("确定", null)
                    .show()
                true
            }
            R.id.action_xpath_rules -> {
                showMdDoc("XPath规则 - 使用指南", "xpath_rules.md")
                true
            }
            R.id.action_view_structure -> {
                val url = binding.etUrl.text.toString().trim()
                if (url.isBlank() || url == "https://") {
                    Toast.makeText(this, getString(R.string.error_no_url), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "正在获取页面结构...", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            CrawlerEngine.fetchHtml(url)
                        }
                        result.fold(
                            onSuccess = { html ->
                                if (html.isBlank()) {
                                    Toast.makeText(this@MainActivity, "页面内容为空", Toast.LENGTH_SHORT).show()
                                } else {
                                    HtmlTreeDialog.show(this@MainActivity, html)
                                }
                            },
                            onFailure = { error ->
                                Toast.makeText(this@MainActivity, getString(R.string.error_fetch_failed, error.message), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showMdDoc(title: String, assetFileName: String) {
        lifecycleScope.launch {
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val textColor = ContextCompat.getColor(this@MainActivity, R.color.md_on_surface)
            val primaryColor = ContextCompat.getColor(this@MainActivity, R.color.md_primary)
            val surfaceVariant = ContextCompat.getColor(this@MainActivity, R.color.md_surface_variant)
            val htmlContent = withContext(Dispatchers.IO) {
                try {
                    val mdText = assets.open(assetFileName).bufferedReader().use { it.readText() }
                    mdToHtml(mdText, textColor, primaryColor, surfaceVariant, isDark)
                } catch (e: Exception) {
                    "<html><body><h2>加载失败</h2><p>${e.message}</p></body></html>"
                }
            }

            val webView = WebView(this@MainActivity).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(Color.TRANSPARENT)
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(title)
                .setView(webView)
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    private fun mdToHtml(
        md: String,
        textColor: Int,
        primaryColor: Int,
        surfaceVariantColor: Int,
        isDark: Boolean
    ): String {
        val bgHex = "transparent"
        val textHex = colorToHex(textColor)
        val primaryHex = colorToHex(primaryColor)
        val svHex = colorToHex(surfaceVariantColor)
        val headerTextColor = if (isDark) "#111318" else "#FFFFFF"
        val borderColor = svHex
        val codeBg = svHex
        val codeColor = if (isDark) "#EF5350" else "#D32F2F"
        val preBg = if (isDark) "#333337" else "#E8ECF4"
        val blockquoteBg = if (isDark) "#2A3850" else "#E0E8F5"
        val tableEvenBg = if (isDark) "#333337" else "#E8ECF4"

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

        // 处理行内格式:将文本中的反引号代码和加粗标记转为 HTML
        fun applyInlineFormatting(text: String): String {
            val escaped = escapeHtml(text)
            var result = escaped.replace(Regex("`([^`]+)`")) { match ->
                "<code>${match.groupValues[1]}</code>"
            }
            result = result.replace(Regex("\\*\\*(.+?)\\*\\*")) { match ->
                "<strong>${match.groupValues[1]}</strong>"
            }
            return result
        }

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
                            tableSb.append("<th>${applyInlineFormatting(cell.trim())}</th>")
                        }
                        tableSb.append("</tr></thead><tbody>")
                        isFirstTableRow = false
                    } else {
                        tableSb.append("<tr>")
                        for (cell in cells) {
                            tableSb.append("<td>${applyInlineFormatting(cell.trim())}</td>")
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
                line.startsWith("### ") -> sb.append("<h3>${applyInlineFormatting(line.removePrefix("### "))}</h3>\n")
                line.startsWith("## ") -> sb.append("<h2>${applyInlineFormatting(line.removePrefix("## "))}</h2>\n")
                line.startsWith("# ") -> sb.append("<h1>${applyInlineFormatting(line.removePrefix("# "))}</h1>\n")
                line.startsWith("> ") -> sb.append("<blockquote>${applyInlineFormatting(line.removePrefix("> "))}</blockquote>\n")
                line.startsWith("- ") || line.startsWith("* ") -> sb.append("<li>${applyInlineFormatting(line.removePrefix("- ").removePrefix("* "))}</li>\n")
                line.matches(Regex("^\\d+\\.\\s.*")) -> sb.append("<li>${applyInlineFormatting(line.replaceFirst(Regex("^\\d+\\.\\s"), ""))}</li>\n")
                line.isBlank() -> sb.append("<br/>\n")
                else -> {
                    sb.append("<p>${applyInlineFormatting(line)}</p>\n")
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

    override fun onResume() {
        super.onResume()
        // 从设置页面返回时检测主题是否已变更,若不一致则重建 Activity
        val prefs = SettingsActivity.getPreferences(this)
        val themeChanged = prefs.getBoolean(SettingsActivity.KEY_THEME_CHANGED, false)
        if (themeChanged) {
            prefs.edit().putBoolean(SettingsActivity.KEY_THEME_CHANGED, false).apply()
            // 显式重建 Activity 以应用新主题
            recreate()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
