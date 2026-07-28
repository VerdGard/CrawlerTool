package com.SoloSu.Crawler_tool

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import com.SoloSu.Crawler_tool.databinding.ActivityMainBinding
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.SoloSu.Crawler_tool.viewmodel.MainUiState
import com.SoloSu.Crawler_tool.viewmodel.MainViewModel

class MainActivity : BaseActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding get() = checkNotNull(_binding) { "Binding已被销毁" }

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Crawler Tool"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_history -> {
                viewModel.showHistory()
                true
            }
            R.id.action_view_structure -> {
                val html = viewModel.state.value?.rawHtml
                if (html.isNullOrBlank()) {
                    Toast.makeText(this, "没有可用的 HTML 内容,请先抓取页面", Toast.LENGTH_SHORT).show()
                } else {
                    HtmlTreeDialog.show(this, html)
                }
                true
            }
            R.id.action_export -> {
                checkStoragePermission { showExportDialog() }
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun setupViews() {
        // Mode chip group
        binding.modeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val mode = when (checkedId) {
                R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector
                R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex
                else -> CrawlerEngine.MatchMode.XPath
            }
            viewModel.updateMatchMode(mode)

            // Show quick attrs for all modes
            binding.tvQuickAttrs.visibility = View.VISIBLE
            binding.quickAttrScroll.visibility = View.VISIBLE
            updateQuickAttrChips(mode)

            // Update hint
            binding.exprInputLayout.hint = when (mode) {
                CrawlerEngine.MatchMode.CssSelector -> "CSS Selector 表达式"
                CrawlerEngine.MatchMode.Regex -> "正则表达式"
                else -> "XPath 表达式"
            }
        }

        // URL input
        binding.etUrl.setText(viewModel.state.value?.url ?: "")
        binding.etUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.updateUrl(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Expression input
        binding.etExpression.setText(viewModel.state.value?.expression ?: "")
        binding.etExpression.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.updateExpression(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Quick attr chips(动态适配模式)
        val quickAttrChips = listOf(
            binding.chipTitle to "title",
            binding.chipHref to "href",
            binding.chipDataOriginal to "data-original",
            binding.chipDataSrc to "data-src",
            binding.chipText to "text()"
        )
        for ((chip, attr) in quickAttrChips) {
            chip.setOnClickListener {
                // 清除 Chip 的 pressed/focused 状态
                chip.isPressed = false
                chip.clearFocus()
                val mode = getCurrentMatchMode()
                val suffix = when (mode) {
                    CrawlerEngine.MatchMode.CssSelector -> if (attr == "text()") ":contains(文本)" else "[$attr]"
                    CrawlerEngine.MatchMode.Regex -> when (attr) {
                        "title" -> "[^<]+?"
                        "href" -> "https?://[^\"']+"
                        "data-original", "data-src" -> "[^\"']+"
                        "text()" -> ".*?"
                        else -> attr
                    }
                    else -> {
                        if (attr == "text()") "text()" else "@$attr"
                    }
                }
                val editable = binding.etExpression.text
                if (editable != null) {
                    val pos = editable.length
                    editable.insert(pos, suffix)
                    binding.etExpression.setSelection(pos + suffix.length)
                }
                binding.etExpression.clearFocus()
            }
        }

        // Result adapter
        adapter = ResultAdapter()
        binding.recyclerResult.adapter = adapter

        // Match button
        binding.btnMatch.setOnClickListener { viewModel.performMatch() }

        // Clear button
        binding.btnClear.setOnClickListener { viewModel.clearResults() }

        // Batch toggle
        binding.btnBatch.setOnClickListener {
            binding.batchSection.visibility = if (binding.batchSection.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Batch start
        binding.btnBatchStart.setOnClickListener {
            val startStr = binding.etBatchStart.text?.toString() ?: "1"
            val endStr = binding.etBatchEnd.text?.toString() ?: "1"
            val pattern = binding.etBatchUrlPattern.text?.toString() ?: ""
            val start = startStr.toIntOrNull() ?: 1
            val end = endStr.toIntOrNull() ?: 1
            if (pattern.isBlank()) {
                Toast.makeText(this, "请输入URL模式,使用 {n} 表示页码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.performBatchMatch(pattern, start, end)
        }

        // Paste URL button
        binding.btnPasteUrl.setOnClickListener {
            pasteFromClipboard()
        }

        // Copy all button
        binding.btnCopyAll.setOnClickListener {
            viewModel.copyRuleExpression()
        }

        // Page navigation
        binding.btnPrevPage.setOnClickListener {
            val page = viewModel.state.value?.currentPage ?: 0
            viewModel.goToPage(page - 1)
        }
        binding.btnNextPage.setOnClickListener {
            val page = viewModel.state.value?.currentPage ?: 0
            viewModel.goToPage(page + 1)
        }
    }

    private fun getCurrentMatchMode(): CrawlerEngine.MatchMode {
        return when (binding.modeChipGroup.checkedChipId) {
            R.id.chipModeCss -> CrawlerEngine.MatchMode.CssSelector
            R.id.chipModeRegex -> CrawlerEngine.MatchMode.Regex
            else -> CrawlerEngine.MatchMode.XPath
        }
    }

    private fun updateQuickAttrChips(mode: CrawlerEngine.MatchMode) {
        val labels = when (mode) {
            CrawlerEngine.MatchMode.CssSelector -> listOf("[title]", "[href]", "[data-original]", "[data-src]", ":contains(文本)")
            CrawlerEngine.MatchMode.Regex -> listOf("[^<]+?", "https?://[^\"']+", "[^\"']+", "[^\"']+", ".*?")
            else -> listOf("@title", "@href", "@data-original", "@data-src", "text()")
        }
        val chips = listOf(binding.chipTitle, binding.chipHref, binding.chipDataOriginal, binding.chipDataSrc, binding.chipText)
        for (i in chips.indices) {
            chips[i].text = labels[i]
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            val s = state ?: return@observe
            binding.tvStatus.text = s.statusText

            // Update adapter
            adapter.submitList(s.results)

            // Pagination
            if (s.totalPages > 1) {
                binding.paginationBar.visibility = View.VISIBLE
                binding.tvPageInfo.text = "${s.currentPage + 1}/${s.totalPages}"
            } else {
                binding.paginationBar.visibility = View.GONE
            }
        }

        viewModel.events.observe(this) { event ->
            when (event) {
                is com.SoloSu.Crawler_tool.viewmodel.MainEvent.ShowToast ->
                    Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                is com.SoloSu.Crawler_tool.viewmodel.MainEvent.ShowHistory ->
                    showHistoryDialog(event.items)
                else -> {}
            }
        }
    }

    private fun pasteFromClipboard() {
        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (cb.hasPrimaryClip()) {
                val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    binding.etUrl.setText(text)
                    viewModel.updateUrl(text)
                    Toast.makeText(this, "已粘贴URL", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "剪贴板中无有效URL", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val REQUEST_MANAGE_STORAGE = 1001
    }

    private fun checkStoragePermission(callback: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                callback()
            } else {
                Toast.makeText(this, "请授予「所有文件管理权限」以使用导出功能", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
                // 回调暂存,launcher 回调中重新执行
                pendingExportCallback = callback
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 请求 WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                callback()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_MANAGE_STORAGE
                )
                pendingExportCallback = callback
            }
        } else {
            // Android 9- 权限在安装时已授予
            callback()
        }
    }

    private var pendingExportCallback: (() -> Unit)? = null

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        pendingExportCallback?.invoke()
        pendingExportCallback = null
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingExportCallback?.invoke()
            } else {
                Toast.makeText(this, "存储权限被拒绝,导出功能无法使用", Toast.LENGTH_SHORT).show()
            }
            pendingExportCallback = null
        }
    }

    private fun showExportDialog() {
        val items = viewModel.getExportFormats()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("导出结果")
            .setItems(items) { _, which ->
                val format = arrayOf("json", "csv", "text", "clipboard")[which]
                viewModel.exportResults(format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHistoryDialog(items: List<HistoryManager.HistoryItem>) {
        val names = items.map { "${it.url.take(30)}... ${it.mode} | ${it.resultCount}条" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("历史记录 (${items.size})")
            .setItems(names) { _, which ->
                viewModel.loadHistoryItem(items[which])
                binding.etUrl.setText(items[which].url)
                binding.etExpression.setText(items[which].expression)
            }
            .setNegativeButton("关闭", null)
            .show()
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
