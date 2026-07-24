package com.SoloSu.Crawler_tool

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.SoloSu.Crawler_tool.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding: ActivitySettingsBinding
        get() = checkNotNull(_binding) { "Binding已被销毁" }

    companion object {
        const val PREF_NAME = "crawler_settings"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_CONNECT_TIMEOUT = "connect_timeout"
        const val KEY_READ_TIMEOUT = "read_timeout"
        const val KEY_THEME = "theme"
        const val KEY_THEME_CHANGED = "theme_changed"
        const val KEY_BATCH_DELAY = "batch_delay"
        const val KEY_BATCH_RETRY = "batch_retry"
        const val KEY_PROXY_ENABLED = "proxy_enabled"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_JS_RENDER = "js_render"
        const val KEY_JS_DELAY = "js_delay"
        const val KEY_LANGUAGE = "language"
        const val KEY_SAVE_HISTORY = "save_history"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val LANG_ZH = "zh"
        const val LANG_EN = "en"

        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun getUserAgent(context: Context): String {
            return getPreferences(context).getString(KEY_USER_AGENT, "") ?: ""
        }

        fun getConnectTimeout(context: Context): Long {
            return getPreferences(context).getLong(KEY_CONNECT_TIMEOUT, 15L)
        }

        fun getReadTimeout(context: Context): Long {
            return getPreferences(context).getLong(KEY_READ_TIMEOUT, 30L)
        }

        fun getTheme(context: Context): String {
            return getPreferences(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        }

        fun getBatchDelay(context: Context): Long {
            return getPreferences(context).getLong(KEY_BATCH_DELAY, 0L)
        }

        fun getBatchRetry(context: Context): Int {
            return getPreferences(context).getInt(KEY_BATCH_RETRY, 1)
        }

        fun getProxySettings(context: Context): Pair<String, Int>? {
            val prefs = getPreferences(context)
            if (!prefs.getBoolean(KEY_PROXY_ENABLED, false)) return null
            val host = prefs.getString(KEY_PROXY_HOST, "") ?: ""
            val port = prefs.getInt(KEY_PROXY_PORT, 8080)
            if (host.isBlank()) return null
            return Pair(host, port)
        }

        fun isJsRenderEnabled(context: Context): Boolean {
            return getPreferences(context).getBoolean(KEY_JS_RENDER, false)
        }

        fun getJsRenderDelay(context: Context): Int {
            return getPreferences(context).getInt(KEY_JS_DELAY, 3)
        }

        fun getLanguage(context: Context): String {
            return getPreferences(context).getString(KEY_LANGUAGE, LANG_ZH) ?: LANG_ZH
        }

        fun isSaveHistoryEnabled(context: Context): Boolean {
            return getPreferences(context).getBoolean(KEY_SAVE_HISTORY, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = getTheme(this)
        when (savedTheme) {
            THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                setTheme(R.style.AppTheme_Light)
            }
            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                setTheme(R.style.AppTheme_Dark)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        super.onCreate(savedInstanceState)
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
        val prefs = getPreferences(this)

        // User-Agent
        val ua = prefs.getString(KEY_USER_AGENT, "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36") ?: ""
        binding.etUserAgent.setText(ua)

        // 连接超时
        val connectTimeout = prefs.getLong(KEY_CONNECT_TIMEOUT, 15L)
        binding.sliderConnectTimeout.value = connectTimeout.toFloat()
        binding.tvConnectTimeout.text = "$connectTimeout 秒"

        // 读取超时
        val readTimeout = prefs.getLong(KEY_READ_TIMEOUT, 30L)
        binding.sliderReadTimeout.value = readTimeout.toFloat()
        binding.tvReadTimeout.text = "$readTimeout 秒"

        // 主题
        val theme = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        when (theme) {
            THEME_SYSTEM -> binding.radioGroupTheme.check(R.id.radioThemeSystem)
            THEME_LIGHT -> binding.radioGroupTheme.check(R.id.radioThemeLight)
            THEME_DARK -> binding.radioGroupTheme.check(R.id.radioThemeDark)
        }

        // 批量延迟
        val batchDelay = prefs.getLong(KEY_BATCH_DELAY, 0L)
        binding.sliderBatchDelay.value = batchDelay.toFloat()
        binding.tvBatchDelay.text = formatDelay(batchDelay)

        // 重试次数
        val retry = prefs.getInt(KEY_BATCH_RETRY, 1)
        binding.sliderRetry.value = retry.toFloat()
        binding.tvRetry.text = "$retry 次"

        // 代理
        val proxyEnabled = prefs.getBoolean(KEY_PROXY_ENABLED, false)
        binding.switchProxy.isChecked = proxyEnabled
        val proxyHost = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        val proxyPort = prefs.getInt(KEY_PROXY_PORT, 8080)
        binding.etProxyHost.setText(proxyHost)
        binding.etProxyPort.setText(proxyPort.toString())
        updateProxyVisibility(proxyEnabled)

        // JS渲染
        val jsEnabled = prefs.getBoolean(KEY_JS_RENDER, false)
        binding.switchJsRender.isChecked = jsEnabled
        val jsDelay = prefs.getInt(KEY_JS_DELAY, 3)
        binding.sliderJsDelay.value = jsDelay.toFloat()
        binding.tvJsDelay.text = "$jsDelay 秒"
        updateJsVisibility(jsEnabled)

        // 语言
        val lang = prefs.getString(KEY_LANGUAGE, LANG_ZH) ?: LANG_ZH
        when (lang) {
            LANG_EN -> binding.radioGroupLanguage.check(R.id.radioLangEn)
            else -> binding.radioGroupLanguage.check(R.id.radioLangZh)
        }

        // 保存历史
        binding.switchSaveHistory.isChecked = prefs.getBoolean(KEY_SAVE_HISTORY, true)

        // 自定义Header
        loadCustomHeaders()
    }

    private fun setupListeners() {
        val prefs = getPreferences(this)

        // User-Agent
        binding.etUserAgent.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val ua = binding.etUserAgent.text?.toString()?.trim() ?: ""
                prefs.edit().putString(KEY_USER_AGENT, ua).apply()
            }
        }

        // 连接超时
        binding.sliderConnectTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toLong()
                binding.tvConnectTimeout.text = "$seconds 秒"
                prefs.edit().putLong(KEY_CONNECT_TIMEOUT, seconds).apply()
            }
        }

        // 读取超时
        binding.sliderReadTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toLong()
                binding.tvReadTimeout.text = "$seconds 秒"
                prefs.edit().putLong(KEY_READ_TIMEOUT, seconds).apply()
            }
        }

        // 主题
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioThemeLight -> THEME_LIGHT
                R.id.radioThemeDark -> THEME_DARK
                else -> THEME_SYSTEM
            }
            prefs.edit()
                .putString(KEY_THEME, theme)
                .putBoolean(KEY_THEME_CHANGED, true)
                .apply()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            when (theme) {
                THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        // 批量延迟
        binding.sliderBatchDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val ms = value.toLong()
                binding.tvBatchDelay.text = formatDelay(ms)
                prefs.edit().putLong(KEY_BATCH_DELAY, ms).apply()
            }
        }

        // 重试次数
        binding.sliderRetry.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.tvRetry.text = "$count 次"
                prefs.edit().putInt(KEY_BATCH_RETRY, count).apply()
            }
        }

        // 代理开关
        binding.switchProxy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_PROXY_ENABLED, isChecked).apply()
            updateProxyVisibility(isChecked)
        }

        // 代理主机
        binding.etProxyHost.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString(KEY_PROXY_HOST, binding.etProxyHost.text?.toString()?.trim() ?: "").apply()
            }
        }

        // 代理端口
        binding.etProxyPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val port = binding.etProxyPort.text?.toString()?.trim()?.toIntOrNull() ?: 8080
                prefs.edit().putInt(KEY_PROXY_PORT, port).apply()
            }
        }

        // JS渲染开关
        binding.switchJsRender.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_JS_RENDER, isChecked).apply()
            updateJsVisibility(isChecked)
        }

        // JS渲染延迟
        binding.sliderJsDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toInt()
                binding.tvJsDelay.text = "$seconds 秒"
                prefs.edit().putInt(KEY_JS_DELAY, seconds).apply()
            }
        }

        // 语言
        binding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.radioLangEn -> LANG_EN
                else -> LANG_ZH
            }
            prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        }

        // 保存历史
        binding.switchSaveHistory.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SAVE_HISTORY, isChecked).apply()
        }

        // Header管理按钮
        binding.btnManageHeaders.setOnClickListener {
            showHeaderManager()
        }
    }

    private fun loadCustomHeaders() {
        val headers = HeaderManager.getHeaders(this)
        binding.tvHeaderCount.text = "已设置 ${headers.size} 个自定义请求头"
        binding.tvHeaderCount.setTextColor(
            if (headers.isNotEmpty()) resources.getColor(R.color.md_primary, theme)
            else resources.getColor(R.color.md_on_surface_variant, theme)
        )
    }

    private fun showHeaderManager() {
        val headers = HeaderManager.getHeaders(this).toMutableList()
        val items = headers.mapIndexed { index, h ->
            "${h.key}: ${if (h.value.length > 30) h.value.take(30) + "..." else h.value}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("自定义请求头")
            .setItems(if (items.isEmpty()) arrayOf("暂无请求头，点击添加") else items) { _, which ->
                if (headers.isEmpty()) {
                    showHeaderEditor(null)
                } else if (which < headers.size) {
                    val options = arrayOf("编辑", "删除", "添加新Header")
                    MaterialAlertDialogBuilder(this)
                        .setTitle(headers[which].key)
                        .setItems(options) { _, opt ->
                            when (opt) {
                                0 -> showHeaderEditor(headers[which])
                                1 -> {
                                    HeaderManager.removeHeader(this, which)
                                    loadCustomHeaders()
                                }
                                2 -> showHeaderEditor(null)
                            }
                        }
                        .show()
                }
            }
            .setPositiveButton("添加", { _, _ -> showHeaderEditor(null) })
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showHeaderEditor(existing: HeaderManager.HeaderEntry?) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        val keyInput = TextInputLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Header名称"
            isHintEnabled = true
        }
        val keyEdit = TextInputEditText(this).apply {
            setText(existing?.key ?: "")
            setSingleLine()
        }
        keyInput.addView(keyEdit)
        container.addView(keyInput)

        val valueInput = TextInputLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Header值"
            isHintEnabled = true
        }
        val valueEdit = TextInputEditText(this).apply {
            setText(existing?.value ?: "")
            setSingleLine()
        }
        valueInput.addView(valueEdit)
        container.addView(valueInput)

        // 预设模板快速选择
        val presetLabel = TextView(this).apply {
            text = "快速插入预设:"
            textSize = 12f
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }
        container.addView(presetLabel)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        HeaderManager.PRESET_TEMPLATES.take(4).forEach { preset ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = preset.key
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    keyEdit.setText(preset.key)
                    valueEdit.setText(preset.value)
                }
            }
            presetRow.addView(chip)
        }
        container.addView(presetRow)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing != null) "编辑请求头" else "添加请求头")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val key = keyEdit.text?.toString()?.trim() ?: ""
                val value = valueEdit.text?.toString()?.trim() ?: ""
                if (key.isNotBlank()) {
                    if (existing != null) {
                        val idx = HeaderManager.getHeaders(this).indexOfFirst { it.key == existing.key && it.value == existing.value }
                        if (idx != -1) {
                            HeaderManager.updateHeader(this, idx, HeaderManager.HeaderEntry(key, value))
                        }
                    } else {
                        HeaderManager.addHeader(this, HeaderManager.HeaderEntry(key, value))
                    }
                    loadCustomHeaders()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateProxyVisibility(enabled: Boolean) {
        val visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.etProxyHost.visibility = visibility
        binding.etProxyPort.visibility = visibility
    }

    private fun updateJsVisibility(enabled: Boolean) {
        val visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.sliderJsDelay.visibility = visibility
        binding.tvJsDelay.visibility = visibility
    }

    private fun formatDelay(ms: Long): String {
        return when (ms) {
            0L -> "无延迟"
            500L -> "0.5秒"
            1000L -> "1秒"
            2000L -> "2秒"
            5000L -> "5秒"
            else -> "${ms}ms"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
