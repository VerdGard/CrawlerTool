package com.SoloSu.Crawler_tool

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import com.SoloSu.Crawler_tool.databinding.ActivityMainBinding
import com.SoloSu.Crawler_tool.databinding.SheetBatchBinding
import com.SoloSu.Crawler_tool.databinding.SheetHistoryBinding
import com.SoloSu.Crawler_tool.viewmodel.MainEvent
import com.SoloSu.Crawler_tool.viewmodel.MainUiState
import com.SoloSu.Crawler_tool.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * 主界面。
 *
 * 布局遵循「从上到下 = 从输入到产出」的阅读顺序:
 * 1 模式 + URL + 表达式(固定顶部,不随结果滚动)
 * 2 结果区(占满剩余空间,内部滚动)
 * 3 吸底操作坞(主操作按钮始终在拇指可达区域)
 */
class MainActivity : BaseActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding get() = checkNotNull(_binding) { "Binding 已销毁" }

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ResultAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private var historySheet: BottomSheetDialog? = null

    /** 仅在「回填」时用,避免 TextWatcher 把用户输入循环写回状态 */
    private var suppressTextWatcher = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupResultList()
        setupInputs()
        setupModeSelector()
        setupQuickAttrChips()
        setupActions()
        observe()

        requestNotificationPermissionIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_history -> { viewModel.showHistory(); true }
        R.id.action_export -> { viewModel.requestExport(); true }
        R.id.action_view_structure -> { openStructureView(); true }
        R.id.action_xpath_rules -> {
            startActivity(android.content.Intent(this, XPathRulesActivity::class.java)); true
        }
        R.id.action_settings -> {
            startActivity(android.content.Intent(this, SettingsActivity::class.java)); true
        }
        R.id.action_about -> { showAbout(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ─── 工具栏 ──────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    // ─── 结果列表 ────────────────────────────────────────────

    private fun setupResultList() {
        adapter = ResultAdapter(onLongClickItem = { item ->
            // 长按优先看该条内容的结构树,没有 HTML 则退回整页
            val html = item.content
            if (html.contains("<") && html.contains(">")) {
                HtmlTreeDialog.show(this, html)
            } else {
                openStructureView()
            }
        })
        binding.recyclerResult.adapter = adapter
        (binding.recyclerResult.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
    }

    // ─── 输入区 ──────────────────────────────────────────────

    private fun setupInputs() {
        binding.etUrl.addTextChangedListener(simpleWatcher { viewModel.updateUrl(it) })
        binding.etExpression.addTextChangedListener(simpleWatcher { viewModel.updateExpression(it) })

        // 端点图标:粘贴 URL
        binding.urlInputLayout.setEndIconOnClickListener { pasteUrlFromClipboard() }

        // 表达式框 Done / 搜索键直接触发匹配
        binding.etExpression.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                viewModel.performMatch()
                true
            } else false
        }
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (suppressTextWatcher) return
            onChange(s?.toString().orEmpty())
        }
    }

    private fun pasteUrlFromClipboard() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cb.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (clip.startsWith("http://") || clip.startsWith("https://")) {
            binding.etUrl.setText(clip)
            binding.etUrl.setSelection(clip.length)
            snack("已粘贴 URL")
        } else {
            snack("剪贴板中没有有效 URL")
        }
    }

    // ─── 模式选择 ────────────────────────────────────────────

    private fun setupModeSelector() {
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector
                R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }
            viewModel.updateMatchMode(mode)
            applyModeUi(mode)
        }
        applyModeUi(CrawlerEngine.MatchMode.XPath)
    }

    private fun applyModeUi(mode: CrawlerEngine.MatchMode) {
        binding.exprInputLayout.hint = when (mode) {
            CrawlerEngine.MatchMode.CssSelector -> getString(R.string.hint_css)
            CrawlerEngine.MatchMode.Regex -> getString(R.string.hint_regex)
            else -> getString(R.string.hint_xpath)
        }
        val labels = when (mode) {
            CrawlerEngine.MatchMode.CssSelector ->
                listOf("[title]", "[href]", "[data-original]", "[data-src]", ":contains(文本)")
            CrawlerEngine.MatchMode.Regex ->
                listOf("[^<]+?", "https?://[^\"']+", "[^\"']+", "[^\"']+", ".*?")
            else -> listOf("@title", "@href", "@data-original", "@data-src", "text()")
        }
        listOf(
            binding.chipTitle, binding.chipHref, binding.chipDataOriginal,
            binding.chipDataSrc, binding.chipText
        ).forEachIndexed { i, chip ->
            chip.text = labels[i]
            chip.tag = i
        }
    }

    private fun setupQuickAttrChips() {
        val keys = listOf("title", "href", "data-original", "data-src", "text()")
        listOf(
            binding.chipTitle, binding.chipHref, binding.chipDataOriginal,
            binding.chipDataSrc, binding.chipText
        ).forEachIndexed { index, chip ->
            chip.setOnClickListener {
                chip.isChecked = false
                insertIntoExpression(quickSnippet(viewModel.state.value?.matchMode, keys[index]))
            }
        }
    }

    /** 按当前模式,把芯片语义转换成可插入的片段。 */
    private fun quickSnippet(mode: CrawlerEngine.MatchMode?, attr: String): String =
        when (mode) {
            CrawlerEngine.MatchMode.CssSelector ->
                if (attr == "text()") ":contains(文本)" else "[$attr]"
            CrawlerEngine.MatchMode.Regex -> when (attr) {
                "title" -> "[^<]+?"
                "href" -> "https?://[^\"']+"
                "data-original", "data-src" -> "[^\"']+"
                else -> ".*?"
            }
            else -> if (attr == "text()") "text()" else "@$attr"
        }

    private fun insertIntoExpression(snippet: String) {
        val editable = binding.etExpression.text ?: return
        val start = binding.etExpression.selectionStart.coerceAtLeast(0)
        val end = binding.etExpression.selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, snippet)
        binding.etExpression.setSelection(start + snippet.length)
        binding.etExpression.clearFocus()
    }

    // ─── 操作 ────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnMatch.setOnClickListener {
            hideKeyboard()
            viewModel.performMatch()
        }
        binding.btnClear.setOnClickListener { viewModel.clearResults() }
        binding.btnCopyRule.setOnClickListener { viewModel.copyExpression() }
        binding.btnExport.setOnClickListener { viewModel.requestExport() }
        binding.btnStructure.setOnClickListener { openStructureView() }
        binding.btnBatch.setOnClickListener { showBatchSheet() }

        binding.btnPrevPage.setOnClickListener { viewModel.prevPage() }
        binding.btnNextPage.setOnClickListener { viewModel.nextPage() }
    }

    private fun openStructureView() {
        val html = viewModel.state.value?.rawHtml.orEmpty()
        if (html.isBlank()) {
            snack(getString(R.string.status_no_html))
        } else {
            HtmlTreeDialog.show(this, html)
        }
    }

    private fun showBatchSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = SheetBatchBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        // 沿用当前 URL 作为模板初值,用户改成带 {page} 的形式即可
        val currentUrl = viewModel.state.value?.url.orEmpty()
        if (currentUrl.isNotBlank() && sheet.etBatchUrlPattern.text.isNullOrBlank()) {
            sheet.etBatchUrlPattern.setText(
                if (currentUrl.contains("{page}")) currentUrl else "$currentUrl?page={page}"
            )
        }

        val settings = com.SoloSu.Crawler_tool.repository.SettingsRepository(this)
        val delay = settings.getBatchDelay()
        sheet.tvBatchHint.text = if (delay > 0) {
            getString(
                R.string.batch_hint_delay,
                SettingsActivity.formatDelayLabel(delay),
                settings.getBatchRetry()
            )
        } else {
            getString(R.string.batch_hint_no_delay, settings.getBatchRetry())
        }

        sheet.btnBatchStart.setOnClickListener {
            val pattern = sheet.etBatchUrlPattern.text?.toString()?.trim().orEmpty()
            val start = sheet.etBatchStart.text?.toString()?.toIntOrNull() ?: 1
            val end = sheet.etBatchEnd.text?.toString()?.toIntOrNull() ?: start
            if (pattern.isBlank() || !pattern.contains("{page}")) {
                sheet.batchUrlInputLayout.error = getString(R.string.batch_url_pattern)
                return@setOnClickListener
            }
            sheet.batchUrlInputLayout.error = null
            dialog.dismiss()
            hideKeyboard()
            viewModel.performBatchMatch(pattern, start, end)
        }

        dialog.show()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    // ─── 历史记录 ────────────────────────────────────────────

    private fun showHistorySheet(items: List<HistoryManager.HistoryItem>) {
        historySheet?.dismiss()
        val dialog = BottomSheetDialog(this)
        val sheet = SheetHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        historySheet = dialog

        sheet.tvHistoryTitle.text = getString(R.string.history_title) + " (${items.size})"
        historyAdapter = HistoryAdapter(
            onClick = { item ->
                viewModel.loadHistoryItem(item)
                applyStateToInputs(viewModel.state.value)
                dialog.dismiss()
                snack("已回填历史记录")
            },
            onDelete = { item ->
                viewModel.deleteHistoryItem(item)
                dialog.dismiss()
                // 重新拉取:若还有记录则弹出新面板,否则提示为空
                viewModel.showHistory()
            }
        )
        sheet.recyclerHistory.adapter = historyAdapter
        historyAdapter.submitList(items)
        sheet.tvHistoryEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        sheet.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.clearHistory()
                    dialog.dismiss()
                    snack("历史记录已清空")
                }
                .show()
        }

        dialog.setOnDismissListener {
            if (historySheet === dialog) historySheet = null
        }
        dialog.show()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    // ─── 状态渲染 ────────────────────────────────────────────

    private fun observe() {
        viewModel.state.observe(this) { state ->
            state ?: return@observe
            render(state)
        }
        viewModel.events.observe(this) { event ->
            when (event) {
                is MainEvent.Message -> snack(event.text)
                is MainEvent.ShowHistory -> showHistorySheet(event.items)
                is MainEvent.ShowExportChooser -> showExportChooser(event.formats)
                null -> Unit
            }
        }
    }

    private fun render(state: MainUiState) {
        // 输入区:只在首次或外部回填时同步,避免打断用户输入
        if (!binding.etUrl.hasFocus() && binding.etUrl.text?.toString() != state.url) {
            binding.etUrl.setText(state.url)
        }
        if (!binding.etExpression.hasFocus() &&
            binding.etExpression.text?.toString() != state.expression
        ) {
            binding.etExpression.setText(state.expression)
        }

        binding.modeChipGroup.check(
            when (state.matchMode) {
                CrawlerEngine.MatchMode.CssSelector -> R.id.chipModeCss
                CrawlerEngine.MatchMode.Regex -> R.id.chipModeRegex
                else -> R.id.chipModeXPath
            }
        )

        // 结果区
        adapter.submitList(state.results)
        binding.emptyView.visibility = if (state.showEmptyState) View.VISIBLE else View.GONE
        binding.recyclerResult.visibility = if (state.results.isEmpty()) View.GONE else View.VISIBLE

        binding.tvResultCount.text = when {
            state.allResults.isEmpty() -> ""
            state.totalPages > 1 -> getString(
                R.string.result_count_paged,
                state.allResults.size,
                state.currentPage + 1,
                state.totalPages
            )
            else -> getString(R.string.result_count, state.allResults.size)
        }

        // 分页条
        binding.paginationBar.visibility = if (state.totalPages > 1) View.VISIBLE else View.GONE
        binding.tvPageInfo.text = "${state.currentPage + 1} / ${state.totalPages}"
        binding.btnPrevPage.isEnabled = state.currentPage > 0
        binding.btnNextPage.isEnabled = state.currentPage < state.totalPages - 1

        // 状态条 + 加载态
        binding.tvStatus.text = state.statusText
        binding.tvStatus.visibility = if (state.statusText.isBlank()) View.GONE else View.VISIBLE
        binding.btnMatch.isEnabled = !state.isLoading
        binding.btnMatch.text = if (state.isLoading) getString(R.string.loading)
        else getString(R.string.btn_match)
        binding.btnBatch.isEnabled = !state.isLoading
    }

    private fun applyStateToInputs(state: MainUiState?) {
        state ?: return
        suppressTextWatcher = true
        binding.etUrl.setText(state.url)
        binding.etExpression.setText(state.expression)
        suppressTextWatcher = false
        binding.modeChipGroup.check(
            when (state.matchMode) {
                CrawlerEngine.MatchMode.CssSelector -> R.id.chipModeCss
                CrawlerEngine.MatchMode.Regex -> R.id.chipModeRegex
                else -> R.id.chipModeXPath
            }
        )
    }

    // ─── 导出 ────────────────────────────────────────────────

    private fun showExportChooser(formats: List<String>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setItems(formats.toTypedArray()) { _, which ->
                viewModel.exportResults(formats[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ─── 杂项 ────────────────────────────────────────────────

    private fun snack(text: String) {
        Snackbar.make(binding.rootLayout, text, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnMatch)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.rootLayout.windowToken, 0)
        binding.etUrl.clearFocus()
        binding.etExpression.clearFocus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    override fun onDestroy() {
        historySheet?.dismiss()
        historySheet = null
        _binding = null
        super.onDestroy()
    }
}
