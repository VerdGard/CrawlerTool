package com.SoloSu.Crawler_tool

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.SoloSu.Crawler_tool.databinding.ActivityMainBinding
import com.SoloSu.Crawler_tool.databinding.PageFetchBinding
import com.SoloSu.Crawler_tool.databinding.PageHistoryBinding
import com.SoloSu.Crawler_tool.databinding.PageResultsBinding
import com.SoloSu.Crawler_tool.databinding.PageSettingsBinding
import com.SoloSu.Crawler_tool.databinding.SheetBatchBinding
import com.SoloSu.Crawler_tool.viewmodel.MainEvent
import com.SoloSu.Crawler_tool.viewmodel.MainUiState
import com.SoloSu.Crawler_tool.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * 主界面:底部导航容器。
 *
 * 四个页面各自是一个独立布局,惰性 inflate 后挂在 pageContainer 上并常驻缓存。
 * 切页只做 visibility 切换,因此输入内容、列表滚动位置、底部导航选中态都不丢;
 * 相比「每页一个 Fragment」,这里没有返回栈需求,控制器 + View 更轻。
 */
class MainActivity : BaseActivity() {

    private enum class Page { FETCH, RESULTS, HISTORY, SETTINGS }

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding get() = checkNotNull(_binding) { "Binding 已销毁" }

    private lateinit var viewModel: MainViewModel
    private lateinit var resultAdapter: ResultAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private var fetchBinding: PageFetchBinding? = null
    private var resultsBinding: PageResultsBinding? = null
    private var historyBinding: PageHistoryBinding? = null
    private var settingsBinding: PageSettingsBinding? = null
    private var settingsController: SettingsPageController? = null

    private var currentPage = Page.FETCH

    /** 代码内部切页时置位,避免 selectedItemId 回调形成 showPage 递归 */
    private var suppressNavCallback = false

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

        resultAdapter = ResultAdapter(onLongClickItem = { item ->
            val html = item.content
            if (html.contains("<") && html.contains(">")) HtmlTreeDialog.show(this, html)
            else openStructureView()
        })
        historyAdapter = HistoryAdapter(
            onClick = { item ->
                viewModel.loadHistoryItem(item)
                showPage(Page.FETCH)
                applyStateToInputs(viewModel.state.value)
                snack("已回填历史记录")
            },
            onDelete = { item -> viewModel.deleteHistoryItem(item) }
        )

        setupBottomNav()
        observe()

        // 重建(切换主题/取色)后回到原来的页
        val restored = savedInstanceState?.getString(KEY_PAGE)
            ?.let { runCatching { Page.valueOf(it) }.getOrNull() }
            ?: Page.FETCH
        showPage(restored)

        requestNotificationPermissionIfNeeded()
    }

    // ─── 底部导航 / 页面切换 ─────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (!suppressNavCallback) {
                val page = when (item.itemId) {
                    R.id.navResults -> Page.RESULTS
                    R.id.navHistory -> Page.HISTORY
                    R.id.navSettings -> Page.SETTINGS
                    else -> Page.FETCH
                }
                if (page != currentPage) showPage(page)
            }
            true
        }
    }

    private fun showPage(page: Page) {
        currentPage = page
        binding.pageContainer.removeAllViews()

        // 先确保 binding 存在,再触发数据刷新:
        // refreshHistory / load 都会推状态,观察者随即渲染,此时 binding 必须已就绪。
        val view: View = when (page) {
            Page.FETCH -> requireFetchBinding().root
            Page.RESULTS -> requireResultsBinding().root
            Page.HISTORY -> requireHistoryBinding().root
            Page.SETTINGS -> requireSettingsBinding().root
        }

        binding.pageContainer.addView(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 用 selectedItemId 而不是直接改 isChecked:后者会被 BottomNavigationView
        // 下次布局重排时纠正,导致选中态与实际页面不一致。
        val navId = when (page) {
            Page.FETCH -> R.id.navFetch
            Page.RESULTS -> R.id.navResults
            Page.HISTORY -> R.id.navHistory
            Page.SETTINGS -> R.id.navSettings
        }
        if (binding.bottomNav.selectedItemId != navId) {
            suppressNavCallback = true
            binding.bottomNav.selectedItemId = navId
            suppressNavCallback = false
        }

        when (page) {
            Page.HISTORY -> viewModel.refreshHistory()
            Page.SETTINGS -> settingsController?.load()
            else -> viewModel.state.value?.let { render(it) }
        }
    }

    // ─── 惰性创建各页 ────────────────────────────────────────

    private fun requireFetchBinding(): PageFetchBinding =
        fetchBinding ?: PageFetchBinding.inflate(layoutInflater).also {
            fetchBinding = it
            setupFetchPage(it)
        }

    private fun requireResultsBinding(): PageResultsBinding =
        resultsBinding ?: PageResultsBinding.inflate(layoutInflater).also {
            resultsBinding = it
            setupResultsPage(it)
        }

    private fun requireHistoryBinding(): PageHistoryBinding =
        historyBinding ?: PageHistoryBinding.inflate(layoutInflater).also {
            historyBinding = it
            setupHistoryPage(it)
        }

    private fun requireSettingsBinding(): PageSettingsBinding =
        settingsBinding ?: PageSettingsBinding.inflate(layoutInflater).also {
            settingsBinding = it
            settingsController = SettingsPageController(this, it).also { c -> c.bind() }
        }

    // ─── 抓取页 ──────────────────────────────────────────────

    private fun setupFetchPage(page: PageFetchBinding) {
        page.etUrl.addTextChangedListener(simpleWatcher { viewModel.updateUrl(it) })
        page.etExpression.addTextChangedListener(simpleWatcher { viewModel.updateExpression(it) })
        page.urlInputLayout.setEndIconOnClickListener { pasteUrlFromClipboard() }

        page.etExpression.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                viewModel.performMatch()
                true
            } else false
        }

        page.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector
                R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }
            viewModel.updateMatchMode(mode)
            applyModeUi(mode)
        }
        applyModeUi(CrawlerEngine.MatchMode.XPath)

        val chips = listOf(
            page.chipTitle, page.chipHref, page.chipDataOriginal, page.chipDataSrc, page.chipText
        )
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                insertIntoExpression(quickSnippet(viewModel.state.value?.matchMode, index))
            }
        }

        page.btnMatch.setOnClickListener {
            hideKeyboard()
            viewModel.performMatch()
        }
        page.btnBatch.setOnClickListener { showBatchSheet() }
        page.btnCopyRule.setOnClickListener { viewModel.copyExpression() }
        page.btnReset.setOnClickListener {
            hideKeyboard()
            viewModel.resetInputs()
            applyStateToInputs(viewModel.state.value)
            snack("已重置")
        }
    }

    /** 快捷芯片:按模式生成可插入片段,索引与 chips 顺序一致。 */
    private fun quickSnippet(mode: CrawlerEngine.MatchMode?, index: Int): String {
        val attr = ATTR_KEYS[index]
        return when (mode) {
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
    }

    private fun applyModeUi(mode: CrawlerEngine.MatchMode) {
        val page = fetchBinding ?: return
        page.exprInputLayout.hint = when (mode) {
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
            page.chipTitle, page.chipHref, page.chipDataOriginal,
            page.chipDataSrc, page.chipText
        ).forEachIndexed { i, chip -> chip.text = labels[i] }
    }

    private fun insertIntoExpression(snippet: String) {
        val edit = fetchBinding?.etExpression ?: return
        val editable = edit.text ?: return
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, snippet)
        edit.setSelection(start + snippet.length)
        edit.clearFocus()
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
        val edit = fetchBinding?.etUrl ?: return
        if (clip.startsWith("http://") || clip.startsWith("https://")) {
            edit.setText(clip)
            edit.setSelection(clip.length)
            snack("已粘贴 URL")
        } else {
            snack("剪贴板中没有有效 URL")
        }
    }

    // ─── 结果页 ──────────────────────────────────────────────

    private fun setupResultsPage(page: PageResultsBinding) {
        page.recyclerResult.layoutManager = LinearLayoutManager(this)
        page.recyclerResult.adapter = resultAdapter
        (page.recyclerResult.itemAnimator as? DefaultItemAnimator)
            ?.supportsChangeAnimations = false

        page.btnExport.setOnClickListener { viewModel.requestExport() }
        page.btnCopyAll.setOnClickListener { viewModel.copyAllResults() }
        page.btnStructure.setOnClickListener { openStructureView() }
        page.btnClearResults.setOnClickListener {
            viewModel.clearResults()
            snack("已清空结果")
        }
        page.btnPrevPage.setOnClickListener { viewModel.prevPage() }
        page.btnNextPage.setOnClickListener { viewModel.nextPage() }
    }

    private fun openStructureView() {
        val html = viewModel.state.value?.rawHtml.orEmpty()
        if (html.isBlank()) snack(getString(R.string.status_no_html))
        else HtmlTreeDialog.show(this, html)
    }

    // ─── 历史页 ──────────────────────────────────────────────

    private fun setupHistoryPage(page: PageHistoryBinding) {
        page.recyclerHistory.layoutManager = LinearLayoutManager(this)
        page.recyclerHistory.adapter = historyAdapter

        page.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.clearHistory()
                    snack("历史记录已清空")
                }
                .show()
        }
    }

    // ─── 批量抓取面板 ────────────────────────────────────────

    private fun showBatchSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = SheetBatchBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

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
                SettingsActivityLabels.formatDelay(delay),
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
            showPage(Page.RESULTS)
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
                is MainEvent.ShowExportChooser -> showExportChooser(event.formats)
                null -> Unit
            }
        }
    }

    private fun render(state: MainUiState) {
        renderFetch(state)
        renderResults(state)
        renderHistory(state)
    }

    private fun renderFetch(state: MainUiState) {
        val page = fetchBinding ?: return

        if (!page.etUrl.hasFocus() && page.etUrl.text?.toString() != state.url) {
            page.etUrl.setText(state.url)
        }
        if (!page.etExpression.hasFocus() &&
            page.etExpression.text?.toString() != state.expression
        ) {
            page.etExpression.setText(state.expression)
        }

        page.modeChipGroup.check(
            when (state.matchMode) {
                CrawlerEngine.MatchMode.CssSelector -> R.id.chipModeCss
                CrawlerEngine.MatchMode.Regex -> R.id.chipModeRegex
                else -> R.id.chipModeXPath
            }
        )

        page.btnMatch.isEnabled = !state.isLoading
        page.btnMatch.text = if (state.isLoading) getString(R.string.loading)
        else getString(R.string.btn_match)
        page.btnBatch.isEnabled = !state.isLoading

        page.tvFetchStatus.text = state.statusText.ifBlank { getString(R.string.fetch_idle_hint) }
    }

    private fun renderResults(state: MainUiState) {
        val page = resultsBinding ?: return

        resultAdapter.submitList(state.results)
        page.emptyView.visibility = if (state.showEmptyState) View.VISIBLE else View.GONE
        page.recyclerResult.visibility = if (state.results.isEmpty()) View.GONE else View.VISIBLE

        page.tvResultCount.text = when {
            state.allResults.isEmpty() -> getString(R.string.results_empty_title)
            state.totalPages > 1 -> getString(
                R.string.result_count_paged,
                state.allResults.size,
                state.currentPage + 1,
                state.totalPages
            )
            else -> getString(R.string.result_count, state.allResults.size)
        }

        page.paginationBar.visibility = if (state.totalPages > 1) View.VISIBLE else View.GONE
        page.tvPageInfo.text = "${state.currentPage + 1} / ${state.totalPages}"
        page.btnPrevPage.isEnabled = state.currentPage > 0
        page.btnNextPage.isEnabled = state.currentPage < state.totalPages - 1

        val hasResults = state.allResults.isNotEmpty()
        page.btnExport.isEnabled = hasResults
        page.btnCopyAll.isEnabled = hasResults
        page.btnStructure.isEnabled = state.rawHtml.isNotBlank()
        page.btnClearResults.isEnabled = hasResults
    }

    private fun renderHistory(state: MainUiState) {
        val page = historyBinding ?: return
        historyAdapter.submitList(state.history)
        page.tvHistoryCountLabel.text = "共 ${state.history.size} 条记录"
        page.historyEmptyView.visibility = if (state.history.isEmpty()) View.VISIBLE else View.GONE
        page.recyclerHistory.visibility = if (state.history.isEmpty()) View.GONE else View.VISIBLE
        page.btnClearHistory.isEnabled = state.history.isNotEmpty()
    }

    private fun applyStateToInputs(state: MainUiState?) {
        state ?: return
        val page = fetchBinding ?: return
        suppressTextWatcher = true
        page.etUrl.setText(state.url)
        page.etExpression.setText(state.expression)
        suppressTextWatcher = false
        page.modeChipGroup.check(
            when (state.matchMode) {
                CrawlerEngine.MatchMode.CssSelector -> R.id.chipModeCss
                CrawlerEngine.MatchMode.Regex -> R.id.chipModeRegex
                else -> R.id.chipModeXPath
            }
        )
        applyModeUi(state.matchMode)
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
            .setAnchorView(binding.bottomNav)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.rootLayout.windowToken, 0)
        fetchBinding?.etUrl?.clearFocus()
        fetchBinding?.etExpression?.clearFocus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PAGE, currentPage.name)
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        private const val KEY_PAGE = "selected_page"

        /** 快捷芯片对应的属性名,顺序与 page_fetch 中 chip 的顺序一致。 */
        private val ATTR_KEYS = listOf("title", "href", "data-original", "data-src", "text()")
    }
}
