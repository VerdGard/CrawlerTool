package com.SoloSu.Crawler_tool

import android.os.Bundle
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.SoloSu.Crawler_tool.HeaderManager
import com.SoloSu.Crawler_tool.databinding.ActivitySettingsBinding
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.SoloSu.Crawler_tool.viewmodel.SettingsViewModel

class SettingsActivity : BaseActivity() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding: ActivitySettingsBinding get() = checkNotNull(_binding) { "Binding已被销毁" }

    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        val state = viewModel.state.value ?: return

        binding.etUserAgent.setText(state.userAgent)
        binding.sliderConnectTimeout.value = state.connectTimeout.toFloat()
        binding.tvConnectTimeout.text = "${state.connectTimeout} 秒"
        binding.sliderReadTimeout.value = state.readTimeout.toFloat()
        binding.tvReadTimeout.text = "${state.readTimeout} 秒"

        when (state.theme) {
            SettingsRepository.THEME_SYSTEM -> binding.radioGroupTheme.check(R.id.radioThemeSystem)
            SettingsRepository.THEME_LIGHT -> binding.radioGroupTheme.check(R.id.radioThemeLight)
            SettingsRepository.THEME_DARK -> binding.radioGroupTheme.check(R.id.radioThemeDark)
        }

        binding.sliderBatchDelay.value = state.batchDelay.toFloat()
        binding.tvBatchDelay.text = formatDelay(state.batchDelay)
        binding.sliderRetry.value = state.batchRetry.toFloat()
        binding.tvRetry.text = "${state.batchRetry} 次"

        binding.switchProxy.isChecked = state.proxyEnabled
        binding.etProxyHost.setText(state.proxyHost)
        binding.etProxyPort.setText(state.proxyPort.toString())
        updateProxyVisibility(state.proxyEnabled)

        binding.switchJsRender.isChecked = state.jsRenderEnabled
        binding.sliderJsDelay.value = state.jsRenderDelay.toFloat()
        binding.tvJsDelay.text = "${state.jsRenderDelay} 秒"
        updateJsVisibility(state.jsRenderEnabled)

        loadCustomHeaders()
    }

    private fun setupListeners() {
        // User-Agent
        binding.etUserAgent.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val ua = binding.etUserAgent.text?.toString()?.trim() ?: ""
                viewModel.updateUserAgent(ua)
            }
        }

        // 预设 User-Agent 快捷选择(单选)
        binding.chipUaChrome.setOnClickListener {
            val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
            binding.etUserAgent.setText(ua)
            binding.etUserAgent.setSelection(ua.length)
            viewModel.updateUserAgent(ua)
            binding.chipUaChrome.isChecked = true
            binding.chipUaEdge.isChecked = false
            binding.chipUaIphone.isChecked = false
        }
        binding.chipUaEdge.setOnClickListener {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Safari/537.36 Edg/131.0.2903.70"
            binding.etUserAgent.setText(ua)
            binding.etUserAgent.setSelection(ua.length)
            viewModel.updateUserAgent(ua)
            binding.chipUaChrome.isChecked = false
            binding.chipUaEdge.isChecked = true
            binding.chipUaIphone.isChecked = false
        }
        binding.chipUaIphone.setOnClickListener {
            val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
            binding.etUserAgent.setText(ua)
            binding.etUserAgent.setSelection(ua.length)
            viewModel.updateUserAgent(ua)
            binding.chipUaChrome.isChecked = false
            binding.chipUaEdge.isChecked = false
            binding.chipUaIphone.isChecked = true
        }

        // 连接超时
        binding.sliderConnectTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toLong()
                binding.tvConnectTimeout.text = "$seconds 秒"
                viewModel.updateConnectTimeout(seconds)
            }
        }

        // 读取超时
        binding.sliderReadTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toLong()
                binding.tvReadTimeout.text = "$seconds 秒"
                viewModel.updateReadTimeout(seconds)
            }
        }

        // 主题
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioThemeLight -> SettingsRepository.THEME_LIGHT
                R.id.radioThemeDark -> SettingsRepository.THEME_DARK
                else -> SettingsRepository.THEME_SYSTEM
            }
            viewModel.updateTheme(theme)
            when (theme) {
                SettingsRepository.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                SettingsRepository.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            recreate()
        }

        // 批量延迟
        binding.sliderBatchDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val ms = value.toLong()
                binding.tvBatchDelay.text = formatDelay(ms)
                viewModel.updateBatchDelay(ms)
            }
        }

        // 重试次数
        binding.sliderRetry.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.tvRetry.text = "$count 次"
                viewModel.updateBatchRetry(count)
            }
        }

        // 代理开关
        binding.switchProxy.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateProxyEnabled(isChecked)
            updateProxyVisibility(isChecked)
        }

        // 代理主机
        binding.etProxyHost.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateProxyHost(binding.etProxyHost.text?.toString()?.trim() ?: "")
            }
        }

        // 代理端口
        binding.etProxyPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val port = binding.etProxyPort.text?.toString()?.trim()?.toIntOrNull() ?: 8080
                viewModel.updateProxyPort(port)
            }
        }

        // JS渲染开关
        binding.switchJsRender.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateJsRenderEnabled(isChecked)
            updateJsVisibility(isChecked)
        }

        // JS渲染延迟
        binding.sliderJsDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toInt()
                binding.tvJsDelay.text = "$seconds 秒"
                viewModel.updateJsRenderDelay(seconds)
            }
        }

        // Header 开关
        binding.switchHeaders.setOnCheckedChangeListener { _, isChecked ->
            HeaderManager.setEnabled(this, isChecked)
            binding.headerListLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // 添加 Header
        binding.btnAddHeader.setOnClickListener { addHeaderEntry() }

        // XPath 规则手册
        binding.cardXpathRules.setOnClickListener {
            startActivity(android.content.Intent(this, XPathRulesActivity::class.java))
        }
    }

    private fun loadCustomHeaders() {
        val headers = viewModel.getHeaders()
        val enabled = HeaderManager.isEnabled(this)
        binding.switchHeaders.isChecked = enabled
        binding.headerListLayout.visibility = if (enabled) View.VISIBLE else View.GONE

        binding.tvHeaderCount.text = "已设置 ${headers.size} 个自定义请求头"
        binding.tvHeaderCount.setTextColor(
            if (headers.isNotEmpty()) resources.getColor(R.color.md_primary, theme)
            else resources.getColor(R.color.md_on_surface_variant, theme)
        )

        // 重建内联 Header 列表
        binding.headerListLayout.removeViews(0, binding.headerListLayout.childCount)
        headers.forEachIndexed { index, header ->
            addHeaderEntryView(header, index)
        }
        binding.headerListLayout.addView(binding.btnAddHeader)
    }

    private fun addHeaderEntry() {
        viewModel.addHeader(HeaderManager.HeaderEntry("", ""))
        loadCustomHeaders()
    }

    private fun addHeaderEntryView(header: HeaderManager.HeaderEntry, index: Int) {
        val row = layoutInflater.inflate(R.layout.item_header_entry, binding.headerListLayout, false) as android.widget.LinearLayout

        val keyEdit = row.findViewById<TextInputEditText>(R.id.etHeaderKey)
        val valueEdit = row.findViewById<TextInputEditText>(R.id.etHeaderValue)
        val deleteBtn = row.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteHeader)

        keyEdit.setText(header.key)
        valueEdit.setText(header.value)
        keyEdit.tag = index
        valueEdit.tag = index

        keyEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveHeaderFromView(keyEdit.tag as Int)
        }
        valueEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveHeaderFromView(valueEdit.tag as Int)
        }
        deleteBtn.setOnClickListener {
            viewModel.removeHeader(index)
            loadCustomHeaders()
        }

        // 插入到添加按钮之前
        binding.headerListLayout.addView(row, binding.headerListLayout.childCount - 1)
    }

    private fun saveHeaderFromView(index: Int) {
        if (index < 0 || index >= binding.headerListLayout.childCount - 1) return
        val row = binding.headerListLayout.getChildAt(index) ?: return
        val keyEdit = row.findViewById<TextInputEditText>(R.id.etHeaderKey) ?: return
        val valueEdit = row.findViewById<TextInputEditText>(R.id.etHeaderValue) ?: return
        val key = keyEdit.text?.toString()?.trim() ?: ""
        val value = valueEdit.text?.toString()?.trim() ?: ""
        viewModel.updateHeader(index, HeaderManager.HeaderEntry(key, value))
    }

    private fun updateProxyVisibility(enabled: Boolean) {
        binding.proxyFieldsLayout.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun updateJsVisibility(enabled: Boolean) {
        binding.jsDelayLayout.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun formatDelay(ms: Long): String = when (ms) {
        0L -> "无延迟"
        500L -> "0.5秒"
        1000L -> "1秒"
        2000L -> "2秒"
        5000L -> "5秒"
        else -> "${ms}ms"
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
