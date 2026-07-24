package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GestureDetectorCompat
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

    private val adapter = ResultAdapter(onLongClickHtml = { html -> showHtmlPreview(html) })
    private var currentMode: CrawlerEngine.MatchMode = CrawlerEngine.MatchMode.XPath
    private var allResults = mutableListOf<String>()
    private var currentPage = 0
    private val pageSize = 20
    private var lastUrl = ""
    private var lastExpression = ""
    private var fetchStats = CrawlerEngine.FetchStats()
    private lateinit var gestureDetector: GestureDetectorCompat

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
        setupGesture()
        checkClipboard()
        updateExpressionHint()
        NotificationHelper.createChannel(this)
    }

    // ── 主题 ────────────────────────────────────────────────────

    private fun applyThemeSetting() {
        when (SettingsActivity.getTheme(this)) {
            SettingsActivity.THEME_LIGHT -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); setTheme(R.style.AppTheme_Light) }
            SettingsActivity.THEME_DARK -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); setTheme(R.style.AppTheme_Dark) }
            else -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
    }

    // ── 手势 (P3.18) ───────────────────────────────────────────

    private fun setupGesture() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (Math.abs(dx) > 200 && Math.abs(dx) > Math.abs(vy)) {
                    if (dx > 0) { clearResults(); toast("右滑清空") }
                    else { performMatch(); toast("左滑匹配") }
                    return true
                }
                return false
            }
        })
        binding.root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
    }

    // ── 剪贴板检测 (P0.4) ──────────────────────────────────────

    private fun checkClipboard() {
        try {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (cb.hasPrimaryClip() && binding.etUrl.text.isNullOrBlank()) {
                val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: return
                if (UrlToolkit.isValidUrl(text)) {
                    toast("检测到链接，已自动填入")
                    binding.etUrl.setText(text)
                    cb.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } catch (_: Exception) {}
    }

    private fun pasteFromClipboard() {
        try {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (cb.hasPrimaryClip()) {
                val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (UrlToolkit.isValidUrl(text)) {
                    binding.etUrl.setText(text)
                    toast("已粘贴URL")
                } else toast("剪贴板中无有效URL")
            }
        } catch (_: Exception) {}
    }

    // ── 初始化组件 ──────────────────────────────────────────────

    private fun setupToolbar() { setSupportActionBar(binding.toolbar); supportActionBar?.setDisplayShowTitleEnabled(true) }
    private fun setupRecyclerView() { binding.recyclerResult.adapter = adapter }

    private fun setupModeSelector() {
        binding.modeChipGroup.setOnCheckedChangeListener { _, id ->
            currentMode = when (id) { R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector; R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex; else -> CrawlerEngine.MatchMode.XPath }
            updateExpressionHint(); updateQuickAttrVisibility()
            findViewById<Chip>(id)?.animate()?.scaleXBy(0.1f)?.scaleYBy(0.1f)?.setDuration(100)?.withEndAction {
                findViewById<Chip>(id)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
            }?.start()
        }
    }

    private fun updateExpressionHint() {
        binding.exprInputLayout.hint = when (currentMode) {
            is CrawlerEngine.MatchMode.XPath -> getString(R.string.hint_xpath)
            is CrawlerEngine.MatchMode.CssSelector -> getString(R.string.hint_css)
            is CrawlerEngine.MatchMode.Regex -> getString(R.string.hint_regex)
        }
        binding.etExpression.animate().alpha(0.5f).setDuration(80).withEndAction { binding.etExpression.animate().alpha(1f).setDuration(120).start() }.start()
    }

    private fun updateQuickAttrVisibility() {
        val vis = if (currentMode is CrawlerEngine.MatchMode.XPath) View.VISIBLE else View.GONE
        val parent = binding.quickAttrChipGroup.parent.parent as ViewGroup
        android.transition.TransitionManager.beginDelayedTransition(parent, android.transition.AutoTransition().apply { duration = 250 })
        binding.tvQuickAttrs.visibility = vis; binding.quickAttrScroll.visibility = vis
    }

    private fun setupQuickAttrChips() {
        val cl: (View) -> Unit = { v ->
            if (v is Chip) {
                val t = v.text.toString(); val e = binding.etExpression.text ?: return@let
                val pos = binding.etExpression.selectionStart
                if (pos in 0..e.length) { e.insert(pos, t); binding.etExpression.setSelection(pos + t.length) }
                else { e.append(t); binding.etExpression.setSelection(e.length) }
            }
        }
        binding.chipTitle.setOnClickListener(cl); binding.chipHref.setOnClickListener(cl)
        binding.chipDataOriginal.setOnClickListener(cl); binding.chipDataSrc.setOnClickListener(cl); binding.chipText.setOnClickListener(cl)
    }

    private fun setupExpressionKeyboardAction() {
        binding.etExpression.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) { performMatch(); true } else false
        }
    }

    private fun setupBatchMode() {
        binding.btnBatch.setOnClickListener {
            if (binding.batchSection.visibility == View.VISIBLE) collapseView(binding.batchSection)
            else expandView(binding.batchSection)
        }
        binding.btnBatchStart.setOnClickListener { performBatchMatch() }
    }

    private fun expandView(v: View) {
        val p = v.parent as ViewGroup
        android.transition.TransitionManager.beginDelayedTransition(p, android.transition.AutoTransition().apply { duration = 250; interpolator = android.view.animation.OvershootInterpolator(0.4f) })
        v.visibility = View.VISIBLE
    }

    private fun collapseView(v: View) {
        val p = v.parent as ViewGroup
        android.transition.TransitionManager.beginDelayedTransition(p, android.transition.AutoTransition().apply { duration = 200 })
        v.visibility = View.GONE
    }

    private fun setupButtons() {
        binding.btnMatch.setOnClickListener { performMatch() }
        binding.btnClear.setOnClickListener { clearResults() }
        binding.btnCopyAll.setOnClickListener { copyRuleExpression() }
        binding.btnPrevPage.setOnClickListener { goToPage(currentPage - 1) }
        binding.btnNextPage.setOnClickListener { goToPage(currentPage + 1) }
        binding.btnShare.setOnClickListener { shareResults() }
        binding.btnPasteUrl.setOnClickListener { pasteFromClipboard() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun toastLong(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
// ═══════════════════════════════════════════════════════════
    // 核心匹配
    // ═══════════════════════════════════════════════════════════

    private fun performMatch() {
        val url = binding.etUrl.text.toString().trim()
        val expr = binding.etExpression.text.toString().trim()
        if (url.isBlank() || url == "https://") { toast(getString(R.string.error_no_url)); return }
        if (expr.isBlank()) { toast(getString(R.string.error_no_expression)); return }
        lastUrl = url; lastExpression = expr
        binding.btnMatch.isEnabled = false
        binding.tvStatus.text = getString(R.string.status_fetching)
        fetchStats = CrawlerEngine.FetchStats(startTime = System.currentTimeMillis())

        val useJs = SettingsActivity.isJsRenderEnabled(this)
        val jsDelay = SettingsActivity.getJsRenderDelay(this)

        lifecycleScope.launch {
            val result = if (useJs) {
                binding.tvStatus.text = getString(R.string.status_js_rendering)
                withContext(Dispatchers.IO) { CrawlerEngine.fetchAndMatchWithJs(this@MainActivity, url, expr, currentMode, jsDelay) }
            } else {
                withContext(Dispatchers.IO) { CrawlerEngine.fetchAndMatch(this@MainActivity, url, expr, currentMode, stats = fetchStats) }
            }
            binding.btnMatch.isEnabled = true
            fetchStats.endTime = System.currentTimeMillis()

            result.fold(
                onSuccess = { items ->
                    allResults = items.toMutableList(); currentPage = 0
                    if (items.isEmpty()) {
                        binding.tvStatus.text = getString(R.string.status_no_result)
                        adapter.submitList(emptyList()); hidePagination(); toast(getString(R.string.status_no_result))
                    } else {
                        showPage(0)
                        binding.tvStatus.text = getString(R.string.status_results, items.size)
                        if (SettingsActivity.isSaveHistoryEnabled(this@MainActivity))
                            HistoryManager.save(this@MainActivity, url, expr, currentMode.toString(), items.size)
                    }
                },
                onFailure = { e ->
                    binding.tvStatus.text = getString(R.string.status_failed)
                    adapter.submitList(emptyList()); hidePagination()
                    showErrorDialog(getString(R.string.status_failed), e.message ?: "未知错误")
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 批量抓取 (P0.2)
    // ═══════════════════════════════════════════════════════════

    private fun performBatchMatch() {
        val expr = binding.etExpression.text.toString().trim()
        val startPage = binding.etBatchStart.text.toString().toIntOrNull() ?: 1
        val endPage = binding.etBatchEnd.text.toString().toIntOrNull() ?: 1
        val urlPattern = binding.etBatchUrlPattern.text.toString().trim()
        if (expr.isBlank()) { toast("请输入表达式"); return }
        if (urlPattern.isBlank()) { toast("请输入URL模板"); return }
        if (startPage > endPage) { toast("起始页码不能大于结束页码"); return }

        binding.btnMatch.isEnabled = false; binding.btnBatchStart.isEnabled = false
        allResults.clear(); currentPage = 0
        fetchStats = CrawlerEngine.FetchStats(startTime = System.currentTimeMillis())
        val delay = SettingsActivity.getBatchDelay(this)
        val retry = SettingsActivity.getBatchRetry(this)

        lifecycleScope.launch {
            var totalPages = endPage - startPage + 1
            for (page in startPage..endPage) {
                val url = urlPattern.replace("{page}", page.toString())
                binding.tvStatus.text = "正在抓取第 ${page - startPage + 1}/$totalPages 页..."
                if (delay > 0 && page > startPage) delay(delay)

                var result: Result<List<String>>? = null
                for (attempt in 0..retry) {
                    if (attempt > 0) { binding.tvStatus.text = "重试第 $attempt 次..."; delay(1000) }
                    result = withContext(Dispatchers.IO) {
                        CrawlerEngine.fetchAndMatch(this@MainActivity, url, expr, currentMode, stats = fetchStats)
                    }
                    if (result.isSuccess) break
                }
                result?.onSuccess { allResults.addAll(it) }
            }
            fetchStats.endTime = System.currentTimeMillis()
            binding.btnMatch.isEnabled = true; binding.btnBatchStart.isEnabled = true

            if (allResults.isEmpty()) {
                binding.tvStatus.text = getString(R.string.status_no_result)
                adapter.submitList(emptyList()); hidePagination()
            } else {
                showPage(0)
                binding.tvStatus.text = "批量完成，共 ${allResults.size} 条（耗时 ${fetchStats.elapsedStr}）"
                if (SettingsActivity.isSaveHistoryEnabled(this@MainActivity))
                    HistoryManager.save(this@MainActivity, urlPattern, expr, "Batch", allResults.size)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 结果管理
    // ═══════════════════════════════════════════════════════════

    private fun clearResults() {
        allResults.clear(); currentPage = 0; adapter.submitList(emptyList())
        binding.tvStatus.text = getString(R.string.status_cleared); hidePagination()
    }

    private fun showPage(page: Int) {
        if (allResults.isEmpty()) { adapter.submitList(emptyList()); hidePagination(); return }
        val totalPages = (allResults.size + pageSize - 1) / pageSize
        val start = page * pageSize; val end = minOf(start + pageSize, allResults.size)
        if (start >= allResults.size) { goToPage(0); return }
        adapter.submitList(allResults.subList(start, end).mapIndexed { i, c -> ResultItem(start + i + 1, c) })
        currentPage = page
        if (totalPages > 1) {
            binding.paginationBar.visibility = View.VISIBLE
            binding.tvPageInfo.text = "${page + 1}/$totalPages"
            binding.btnPrevPage.isEnabled = page > 0
            binding.btnNextPage.isEnabled = page < totalPages - 1
        } else hidePagination()
    }

    private fun goToPage(page: Int) { showPage(page) }
    private fun hidePagination() { binding.paginationBar.visibility = View.GONE }

    private fun copyRuleExpression() {
        val expr = binding.etExpression.text.toString().trim()
        if (expr.isEmpty()) { toast("表达式为空"); return }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Crawler Rule", expr))
        toast("已复制规则表达式")
    }

    // ═══════════════════════════════════════════════════════════
    // 分享 (P0.3)
    // ═══════════════════════════════════════════════════════════

    private fun shareResults() {
        if (allResults.isEmpty()) { toast("没有可分享的结果"); return }
        val content = allResults.joinToString("\n") { it }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Crawler Tool 抓取结果:\n$content")
            putExtra(Intent.EXTRA_SUBJECT, "抓取结果 - $lastUrl")
        }
        startActivity(Intent.createChooser(intent, "分享结果"))
    }

    // ═══════════════════════════════════════════════════════════
    // 导出
    // ═══════════════════════════════════════════════════════════

    private fun showExportDialog() {
        if (allResults.isEmpty()) { toast(getString(R.string.export_empty)); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_title))
            .setItems(arrayOf("JSON", "CSV", "纯文本", "复制到剪贴板")) { _, which ->
                if (which == 3) {
                    val content = allResults.joinToString("\n")
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Crawler Results", content))
                    toast("已复制全部结果到剪贴板")
                } else {
                    val format = arrayOf("json", "csv", "text")[which]
                    exportResults(format)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportResults(format: String) {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.Default) { CrawlerEngine.exportResults(allResults, format) }
            val ext = when (format) { "json" -> "json"; "csv" -> "csv"; else -> "txt" }
            val file = File(cacheDir, "Crawler_Results_${System.currentTimeMillis()}.$ext")
            try {
                file.writeText(content)
                toastLong("已导出到: ${file.absolutePath}")
            } catch (e: Exception) { toastLong("导出失败: ${e.message}") }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 预览
    // ═══════════════════════════════════════════════════════════

    private fun showHtmlPreview(html: String) {
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = false; settings.allowFileAccess = false
            settings.loadWithOverviewMode = true; settings.useWideViewPort = true
            setBackgroundColor(android.graphics.Color.WHITE)
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.preview_html)).setView(wv).setPositiveButton("关闭", null).show()
    }

    private fun showImagePreview() {
        val urls = ImagePreviewDialog.extractImageUrls(allResults)
        if (urls.isEmpty()) { toast("未检测到图片链接"); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("图片画廊 (${urls.size}张)")
            .setItems(urls.map { if (it.length > 40) it.take(40) + "..." else it }.toTypedArray()) { _, which ->
                ImagePreviewDialog.show(this, urls[which])
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // 数据过滤 (P2.10)
    // ═══════════════════════════════════════════════════════════

    private fun showFilterDialog() {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }

        val keywordInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "关键词"; setSingleLine(); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(keywordInput)

        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }
        val rbInclude = RadioButton(this).apply { text = "仅保留"; id = 1 }; val rbExclude = RadioButton(this).apply { text = "排除"; id = 2 }
        modeGroup.addView(rbInclude); modeGroup.addView(rbExclude); container.addView(modeGroup)

        val dedupSwitch = com.google.android.material.switchmaterial.SwitchMaterial(this).apply { text = "去重"; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }
        container.addView(dedupSwitch)

        val sortGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }
        val rbDefault = RadioButton(this).apply { text = "默认"; id = 3; isChecked = true }; val rbAlpha = RadioButton(this).apply { text = "字母序"; id = 4 }; val rbLen = RadioButton(this).apply { text = "按长度"; id = 5 }
        sortGroup.addView(rbDefault); sortGroup.addView(rbAlpha); sortGroup.addView(rbLen); container.addView(sortGroup)

        val countText = TextView(this).apply { text = "当前 ${allResults.size} 条"; textSize = 12f; setPadding(0, (8 * density).toInt(), 0, 0) }
        container.addView(countText)

        MaterialAlertDialogBuilder(this)
            .setTitle("数据过滤")
            .setView(container)
            .setPositiveButton("应用") { _, _ ->
                val kw = keywordInput.text?.toString()?.trim() ?: ""
                val fm = if (kw.isBlank()) DataFilter.FilterMode.OFF else if (rbInclude.isChecked) DataFilter.FilterMode.INCLUDE else DataFilter.FilterMode.EXCLUDE
                val sm = when { rbAlpha.isChecked -> DataFilter.SortMode.ALPHA; rbLen.isChecked -> DataFilter.SortMode.LENGTH; else -> DataFilter.SortMode.DEFAULT }
                val opts = DataFilter.FilterOptions(kw, fm, dedupSwitch.isChecked, sm)
                val filtered = DataFilter.apply(allResults, opts)
                allResults = filtered.toMutableList(); currentPage = 0
                showPage(0)
                binding.tvStatus.text = "过滤后 ${allResults.size} 条"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // 统计信息 (P2.13)
    // ═══════════════════════════════════════════════════════════

    private fun showStatsDialog() {
        val density = resources.displayMetrics.density
        val sb = StringBuilder()
        sb.appendLine("📊 抓取统计")
        sb.appendLine("─────────────")
        sb.appendLine("总结果数: ${allResults.size}")
        sb.appendLine("去重数: ${allResults.distinct().size} (去重 ${allResults.size - allResults.distinct().size} 条)")
        sb.appendLine("请求总数: ${fetchStats.totalRequests}")
        sb.appendLine("成功请求: ${fetchStats.successRequests}")
        sb.appendLine("失败请求: ${fetchStats.failedRequests}")
        sb.appendLine("总耗时: ${fetchStats.elapsedStr}")
        sb.appendLine("URL: $lastUrl")
        sb.appendLine("表达式: $lastExpression")
        if (allResults.isNotEmpty()) {
            val lens = allResults.map { it.length }
            sb.appendLine("最短: ${lens.min()} 字符")
            sb.appendLine("最长: ${lens.max()} 字符")
            sb.appendLine("平均: ${lens.average().toInt()} 字符")
        }

        val tv = TextView(this).apply {
            text = sb.toString(); textSize = 13f; setPadding(16, 8, 16, 8)
            setTypeface(null, android.graphics.Typeface.MONOSPACE)
        }
        MaterialAlertDialogBuilder(this).setTitle("统计信息").setView(tv).setPositiveButton("关闭", null).show()
    }

    // ═══════════════════════════════════════════════════════════
    // 历史记录 (P0.1)
    // ═══════════════════════════════════════════════════════════

    private fun showHistoryDialog() {
        val items = HistoryManager.loadAll(this)
        if (items.isEmpty()) { toast("暂无历史记录"); return }
        val names = items.map { "${it.url.take(30)}… ${it.mode} | ${it.resultCount}条" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("历史记录 (${items.size})")
            .setItems(names) { _, which ->
                val item = items[which]
                binding.etUrl.setText(item.url)
                binding.etExpression.setText(item.expression)
                when (item.mode) {
                    "CssSelector" -> binding.chipModeCss.isChecked = true
                    "Regex" -> binding.chipModeRegex.isChecked = true
                    else -> binding.chipModeXPath.isChecked = true
                }
                toast("已填入历史记录")
            }
            .setPositiveButton("收藏夹") { _, _ -> showFavoritesDialog() }
            .setNeutralButton("清空") { _, _ ->
                MaterialAlertDialogBuilder(this).setTitle("清空历史").setMessage("确定清空所有历史记录？")
                    .setPositiveButton("确定") { _, _ -> HistoryManager.clearAll(this); toast("已清空") }
                    .setNegativeButton("取消", null).show()
            }
            .setNegativeButton("关闭", null).show()
    }

    private fun showFavoritesDialog() {
        val items = HistoryManager.loadFavorites(this)
        if (items.isEmpty()) { toast("暂无收藏"); return }
        val names = items.map { "${it.url.take(30)}… ${it.expression.take(20)}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("收藏夹")
            .setItems(names) { _, which ->
                val item = items[which]
                binding.etUrl.setText(item.url); binding.etExpression.setText(item.expression)
                when (item.mode) { "CssSelector" -> binding.chipModeCss.isChecked = true; "Regex" -> binding.chipModeRegex.isChecked = true; else -> binding.chipModeXPath.isChecked = true }
            }
            .setNegativeButton("关闭", null).show()
    }

    // ═══════════════════════════════════════════════════════════
    // URL工具箱 (P2.12)
    // ═══════════════════════════════════════════════════════════

    private fun showUrlToolkit() {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "输入URL"; setText(binding.etUrl.text.toString().trim()); setSingleLine()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(input)

        val output = TextView(this).apply {
            textSize = 12f; setTypeface(null, android.graphics.Typeface.MONOSPACE)
            setPadding(0, (8 * density).toInt(), 0, 0)
            minHeight = (40 * density).toInt()
        }
        container.addView(output)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }

        fun updateOutput(action: String) {
            val url = input.text?.toString()?.trim() ?: ""
            output.text = when (action) {
                "encode" -> "编码: ${UrlToolkit.encode(url)}"
                "decode" -> "解码: ${UrlToolkit.decode(url)}"
                "params" -> {
                    val params = UrlToolkit.extractParams(url)
                    if (params.isEmpty()) "无参数" else params.joinToString("\n") { "${it.key} = ${it.value}" }
                }
                else -> ""
            }
        }

        for ((label, action) in listOf("编码" to "encode", "解码" to "decode", "参数" to "params")) {
            val btn = com.google.android.material.button.MaterialButton(this).apply {
                text = label; setOnClickListener { updateOutput(action) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = (4 * density).toInt() }
            }
            btnRow.addView(btn)
        }
        container.addView(btnRow)

        MaterialAlertDialogBuilder(this).setTitle("URL工具箱").setView(container).setPositiveButton("关闭", null).show()
    }

    // ═══════════════════════════════════════════════════════════
    // 定时任务 (P3.16)
    // ═══════════════════════════════════════════════════════════

    private fun showScheduleDialog() {
        val tasks = ScheduleManager.loadTasks(this)
        if (tasks.isEmpty()) {
            // 添加新任务
            showAddScheduleDialog()
        } else {
            val names = tasks.map { "${it.url.take(25)}… 每${it.intervalMinutes}分钟" }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("定时任务")
                .setItems(names) { _, which ->
                    val opts = arrayOf("编辑", "删除", "添加新任务")
                    MaterialAlertDialogBuilder(this).setTitle(tasks[which].url.take(30))
                        .setItems(opts) { _, opt ->
                            when (opt) { 0 -> showAddScheduleDialog(tasks[which]); 1 -> { ScheduleManager.removeTask(this, tasks[which].id); toast("已删除") }; 2 -> showAddScheduleDialog() }
                        }.show()
                }
                .setPositiveButton("添加") { _, _ -> showAddScheduleDialog() }
                .setNegativeButton("关闭", null).show()
        }
    }

    private fun showAddScheduleDialog(existing: ScheduleManager.ScheduleTask? = null) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }
        val urlInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "URL"; setText(existing?.url ?: binding.etUrl.text.toString().trim()); setSingleLine()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        container.addView(urlInput)
        val exprInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "表达式"; setText(existing?.expression ?: binding.etExpression.text.toString().trim()); setSingleLine()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() }
        }
        container.addView(exprInput)
        val intervalInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "间隔(分钟)"; setText((existing?.intervalMinutes ?: 30).toString()); setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() }
        }
        container.addView(intervalInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing != null) "编辑定时任务" else "添加定时任务")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val url = urlInput.text?.toString()?.trim() ?: ""; val expr = exprInput.text?.toString()?.trim() ?: ""
                val interval = intervalInput.text?.toString()?.trim()?.toIntOrNull() ?: 30
                if (url.isBlank() || expr.isBlank()) { toast("请填写完整"); return@setPositiveButton }
                val task = ScheduleManager.ScheduleTask(
                    id = existing?.id ?: System.currentTimeMillis(),
                    url = url, expression = expr, mode = currentMode.toString(), intervalMinutes = interval
                )
                if (existing != null) ScheduleManager.updateTask(this, task) else ScheduleManager.addTask(this, task)
                toast("定时任务已${if (existing != null) "更新" else "添加"}")
            }
            .setNegativeButton("取消", null).show()
    }
}
// ═══════════════════════════════════════════════════════════
    // 菜单
    // ═══════════════════════════════════════════════════════════

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { showExportDialog(); true }
            R.id.action_share -> { shareResults(); true }
            R.id.action_history -> { showHistoryDialog(); true }
            R.id.action_filter -> { showFilterDialog(); true }
            R.id.action_stats -> { showStatsDialog(); true }
            R.id.action_image_preview -> { showImagePreview(); true }
            R.id.action_regex_tester -> { RegexTesterDialog.show(this, binding.etExpression.text?.toString() ?: ""); true }
            R.id.action_url_toolkit -> { showUrlToolkit(); true }
            R.id.action_schedule -> { showScheduleDialog(); true }
            R.id.action_about -> {
                MaterialAlertDialogBuilder(this).setTitle(getString(R.string.about_title))
                    .setMessage(getString(R.string.about_message))
                    .setPositiveButton("更新日志") { _, _ ->
                        MaterialAlertDialogBuilder(this).setTitle("更新日志")
                            .setMessage(getString(R.string.about_changelog_content))
                            .setPositiveButton("确定", null).show()
                    }
                    .setNegativeButton("确定", null).show(); true
            }
            R.id.action_xpath_rules -> { showMdDoc("XPath规则 - 使用指南", "xpath_rules.md"); true }
            R.id.action_view_structure -> {
                val url = binding.etUrl.text.toString().trim()
                if (url.isBlank() || url == "https://") { toast(getString(R.string.error_no_url)); return true }
                toast("正在获取页面结构...")
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { CrawlerEngine.fetchHtml(this@MainActivity, url) }
                    result.fold(
                        onSuccess = { html -> if (html.isBlank()) toast("页面内容为空") else HtmlTreeDialog.show(this@MainActivity, html) },
                        onFailure = { e -> toastLong("获取失败: ${e.message}") }
                    )
                }; true
            }
            R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Markdown 文档渲染 (P3.17 深色模式WebView)
    // ═══════════════════════════════════════════════════════════

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
                } catch (e: Exception) { "<html><body><h2>加载失败</h2><p>${e.message}</p></body></html>" }
            }
            val wv = WebView(this@MainActivity).apply {
                settings.javaScriptEnabled = false; settings.allowFileAccess = false
                settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                setBackgroundColor(if (isDark) android.graphics.Color.parseColor("#1E1E1E") else android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(title).setView(wv).setPositiveButton("关闭", null).show()
        }
    }

    private fun mdToHtml(md: String, textColor: Int, primaryColor: Int, svColor: Int, isDark: Boolean): String {
        val textHex = colorToHex(textColor); val primaryHex = colorToHex(primaryColor); val svHex = colorToHex(svColor)
        val codeColor = if (isDark) "#EF5350" else "#D32F2F"; val preBg = if (isDark) "#333337" else "#E8ECF4"
        val blockquoteBg = if (isDark) "#2A3850" else "#E0E8F5"; val tableEvenBg = if (isDark) "#333337" else "#E8ECF4"
        val bgColor = if (isDark) "#1E1E1E" else "transparent"

        return """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<style>body{font-family:-apple-system,system-ui,sans-serif;font-size:14px;line-height:1.6;color:$textHex;padding:8px 12px;background:$bgColor}
h1{font-size:18px;color:$primaryHex;margin:12px 0 8px;padding-bottom:4px;border-bottom:1px solid $svHex}
h2{font-size:16px;color:$primaryHex;margin:10px 0 6px}h3{font-size:14px;color:$primaryHex;margin:8px 0 4px}
p{margin:6px 0}code{background:$preBg;color:$codeColor;padding:1px 5px;border-radius:3px;font-family:monospace;font-size:12px}
pre{background:$preBg;border:1px solid $svHex;border-radius:6px;padding:8px 12px;overflow-x:auto;font-size:12px;line-height:1.5}
blockquote{background:$blockquoteBg;border-left:3px solid $primaryHex;padding:4px 8px;margin:6px 0;border-radius:0 4px 4px 0}
table{border-collapse:collapse;width:100%;margin:6px 0;font-size:13px}th,td{border:1px solid $svHex;padding:4px 8px;text-align:left}
th{background:$primaryHex;color:#fff}tr:nth-child(even){background:$tableEvenBg}
a{color:$primaryHex}ul,ol{padding-left:20px;margin:4px 0}li{margin:2px 0}
</style></head><body>${mdToHtmlContent(md)}</body></html>"""
    }

    private fun mdToHtmlContent(md: String): String {
        var html = md
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace(Regex("""^### (.+)$""", RegexOption.MULTILINE)) { "<h3>${it.group(1)}</h3>" }
            .replace(Regex("""^## (.+)$""", RegexOption.MULTILINE)) { "<h2>${it.group(1)}</h2>" }
            .replace(Regex("""^# (.+)$""", RegexOption.MULTILINE)) { "<h1>${it.group(1)}</h1>" }
            .replace(Regex("""`([^`]+)`""")) { "<code>${it.group(1)}</code>" }
            .replace(Regex("""\*\*([^*]+)\*\*""")) { "<b>${it.group(1)}</b>" }
            .replace(Regex("""\*([^*]+)\*""")) { "<i>${it.group(1)}</i>" }
            .replace(Regex("""^---$""", RegexOption.MULTILINE)) { "<hr>" }
            .replace(Regex("""^> (.+)$""", RegexOption.MULTILINE)) { "<blockquote>${it.group(1)}</blockquote>" }
        // 处理代码块
        html = html.replace(Regex("""```[\s\S]*?```""")) {
            it.value.replace(Regex("""```(\w*)\n?"""), "").let { "<pre>${it.trim()}</pre>" }
        }
        // 处理列表
        html = html.replace(Regex("""^- (.+)$""", RegexOption.MULTILINE)) { "<li>${it.group(1)}</li>" }
        html = html.replace(Regex("""(?:<li>.*?</li>\n?)+""")) { "<ul>${it.value}</ul>" }
        // 处理换行
        html = html.replace(Regex("""\n\n"""), "</p><p>")
        html = html.replace(Regex("""\n"""), "<br>")
        return "<p>$html</p>"
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("确定", null).setIcon(android.R.drawable.ic_dialog_alert).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
