package com.SoloSu.Crawler_tool

import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import com.SoloSu.Crawler_tool.databinding.PageSettingsBinding
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.SoloSu.Crawler_tool.viewmodel.SettingsViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/**
 * 设置页控制器。
 *
 * 设置从独立 Activity 改为底部导航的一个页面,因此把原 SettingsActivity 的
 * 全部逻辑收拢到此处:绑定 [PageSettingsBinding]、读写 [SettingsViewModel],
 * 并在影响全局的开关(主题、莫奈取色)变化时重建 Activity 让主题立即生效。
 *
 * 之所以不用 Fragment:全应用只有四个页面,页面间不需要各自的返回栈,
 * 用控制器 + 布局直接 inflate 更轻,也避免 Fragment 重建带来的状态丢失。
 */
class SettingsPageController(
    private val host: MainActivity,
    private val binding: PageSettingsBinding,
) {

    private val viewModel: SettingsViewModel =
        ViewModelProvider(host)[SettingsViewModel::class.java]

    /** 避免 load() 期间回写设置造成递归 */
    private var suppressCallbacks = false

    private val monetSupported: Boolean = DynamicColors.isDynamicColorAvailable()

    fun bind() {
        suppressCallbacks = true
        load()
        suppressCallbacks = false
        setupListeners()
    }

    fun load() {
        val state = viewModel.state.value ?: return
        suppressCallbacks = true

        binding.etUserAgent.setText(state.userAgent)
        binding.sliderConnectTimeout.value = state.connectTimeout.toFloat()
        binding.tvConnectTimeout.text = "${state.connectTimeout} 秒"
        binding.sliderReadTimeout.value = state.readTimeout.toFloat()
        binding.tvReadTimeout.text = "${state.readTimeout} 秒"

        when (state.theme) {
            SettingsRepository.THEME_LIGHT -> binding.radioGroupTheme.check(R.id.radioThemeLight)
            SettingsRepository.THEME_DARK -> binding.radioGroupTheme.check(R.id.radioThemeDark)
            else -> binding.radioGroupTheme.check(R.id.radioThemeSystem)
        }

        // 莫奈取色:系统不支持时禁用开关并说明原因,而不是让用户点了一个无效的开关
        binding.switchMonet.isChecked = state.monetEnabled && monetSupported
        binding.switchMonet.isEnabled = monetSupported
        binding.tvMonetSummary.setText(
            if (monetSupported) R.string.settings_monet_summary
            else R.string.settings_monet_unavailable
        )

        binding.sliderBatchDelay.value = state.batchDelay.toFloat()
        binding.tvBatchDelay.text = SettingsActivityLabels.formatDelay(state.batchDelay)
        binding.sliderRetry.value = state.batchRetry.toFloat()
        binding.tvRetry.text = "${state.batchRetry} 次"

        binding.switchProxy.isChecked = state.proxyEnabled
        binding.etProxyHost.setText(state.proxyHost)
        binding.etProxyPort.setText(state.proxyPort.toString())
        binding.proxyFieldsLayout.visibility = visibleIf(state.proxyEnabled)

        binding.switchJsRender.isChecked = state.jsRenderEnabled
        binding.sliderJsDelay.value = state.jsRenderDelay.toFloat()
        binding.tvJsDelay.text = "${state.jsRenderDelay} 秒"
        binding.jsDelayLayout.visibility = visibleIf(state.jsRenderEnabled)

        binding.tvVersion.text = host.getString(R.string.settings_version) + " " +
            host.packageManager.getPackageInfo(host.packageName, 0).versionName

        // loadCustomHeaders 会写 switchHeaders.isChecked,必须仍在抑制区间内
        loadCustomHeaders()
        suppressCallbacks = false
    }

    private fun setupListeners() {
        binding.etUserAgent.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.updateUserAgent(text(binding.etUserAgent))
        }

        binding.chipUaChrome.setOnClickListener {
            applyUa(UA_CHROME)
        }
        binding.chipUaEdge.setOnClickListener {
            applyUa(UA_EDGE)
        }
        binding.chipUaIphone.setOnClickListener {
            applyUa(UA_IPHONE)
        }

        binding.sliderConnectTimeout.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvConnectTimeout.text = "${value.toLong()} 秒"
            viewModel.updateConnectTimeout(value.toLong())
        }

        binding.sliderReadTimeout.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvReadTimeout.text = "${value.toLong()} 秒"
            viewModel.updateReadTimeout(value.toLong())
        }

        binding.radioGroupTheme.setOnCheckedChangeListener { _: RadioGroup, checkedId ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            val theme = when (checkedId) {
                R.id.radioThemeLight -> SettingsRepository.THEME_LIGHT
                R.id.radioThemeDark -> SettingsRepository.THEME_DARK
                else -> SettingsRepository.THEME_SYSTEM
            }
            if (theme == viewModel.state.value?.theme) return@setOnCheckedChangeListener
            viewModel.updateTheme(theme)
            val mode = nightModeOf(theme)
            if (AppCompatDelegate.getDefaultNightMode() == mode) {
                // 模式没变(例如系统跟随 -> 显式同值),AppCompat 不会重建,手动触发
                host.recreate()
            } else {
                // 模式变化时 setDefaultNightMode 会自行重建,再调 recreate 会导致重建两次
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        binding.switchMonet.setOnCheckedChangeListener { _, checked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            if (checked == viewModel.state.value?.monetEnabled) return@setOnCheckedChangeListener
            viewModel.updateMonetEnabled(checked)
            // 取色作用于主题叠加层,必须重建才能让已 inflate 的视图换色
            host.recreate()
        }

        binding.sliderBatchDelay.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvBatchDelay.text = SettingsActivityLabels.formatDelay(value.toLong())
            viewModel.updateBatchDelay(value.toLong())
        }

        binding.sliderRetry.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvRetry.text = "${value.toInt()} 次"
            viewModel.updateBatchRetry(value.toInt())
        }

        binding.switchProxy.setOnCheckedChangeListener { _, checked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            viewModel.updateProxyEnabled(checked)
            binding.proxyFieldsLayout.visibility = visibleIf(checked)
        }

        binding.etProxyHost.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.updateProxyHost(text(binding.etProxyHost))
        }

        binding.etProxyPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateProxyPort(text(binding.etProxyPort).toIntOrNull() ?: 8080)
            }
        }

        binding.switchJsRender.setOnCheckedChangeListener { _, checked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            viewModel.updateJsRenderEnabled(checked)
            binding.jsDelayLayout.visibility = visibleIf(checked)
        }

        binding.sliderJsDelay.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvJsDelay.text = "${value.toInt()} 秒"
            viewModel.updateJsRenderDelay(value.toInt())
        }

        binding.switchHeaders.setOnCheckedChangeListener { _, checked ->
            if (suppressCallbacks) return@setOnCheckedChangeListener
            HeaderManager.setEnabled(host, checked)
            binding.headerListLayout.visibility = visibleIf(checked)
        }

        binding.btnAddHeader.setOnClickListener {
            viewModel.addHeader(HeaderManager.HeaderEntry("", ""))
            loadCustomHeaders()
        }

        binding.cardXpathRules.setOnClickListener {
            host.startActivity(
                android.content.Intent(host, XPathRulesActivity::class.java)
            )
        }

        binding.cardAbout.setOnClickListener { showAbout() }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(host)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun applyUa(ua: String) {
        binding.etUserAgent.setText(ua)
        binding.etUserAgent.setSelection(ua.length)
        viewModel.updateUserAgent(ua)
        binding.chipUaChrome.isChecked = ua == UA_CHROME
        binding.chipUaEdge.isChecked = ua == UA_EDGE
        binding.chipUaIphone.isChecked = ua == UA_IPHONE
    }

    private fun nightModeOf(theme: String): Int = when (theme) {
        SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    // ─── 自定义请求头 ────────────────────────────────────────

    private fun loadCustomHeaders() {
        val headers = viewModel.getHeaders()
        val enabled = HeaderManager.isEnabled(host)
        binding.switchHeaders.isChecked = enabled
        binding.headerListLayout.visibility = visibleIf(enabled)
        binding.tvHeaderCount.text = "已设置 ${headers.size} 个自定义请求头"

        val container = binding.headerListLayout
        // 保留末尾的「添加请求头」按钮,只重建中间的条目
        if (container.childCount > 1) container.removeViews(0, container.childCount - 1)
        headers.forEachIndexed { index, header ->
            container.addView(
                createHeaderRow(header, index),
                container.childCount - 1
            )
        }
    }

    private fun createHeaderRow(header: HeaderManager.HeaderEntry, index: Int): View {
        val row = host.layoutInflater.inflate(
            R.layout.item_header_entry, binding.headerListLayout, false
        )
        val keyEdit = row.findViewById<TextInputEditText>(R.id.etHeaderKey)
        val valueEdit = row.findViewById<TextInputEditText>(R.id.etHeaderValue)
        val deleteBtn = row.findViewById<MaterialButton>(R.id.btnDeleteHeader)

        keyEdit.setText(header.key)
        valueEdit.setText(header.value)

        val save = {
            val entry = HeaderManager.HeaderEntry(text(keyEdit), text(valueEdit))
            viewModel.updateHeader(index, entry)
        }
        keyEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) save() }
        valueEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) save() }
        deleteBtn.setOnClickListener {
            viewModel.removeHeader(index)
            loadCustomHeaders()
        }
        return row
    }

    private fun text(edit: TextInputEditText): String =
        edit.text?.toString()?.trim().orEmpty()

    private fun visibleIf(condition: Boolean): Int =
        if (condition) View.VISIBLE else View.GONE

    companion object {
        private const val UA_CHROME =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"
        private const val UA_EDGE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.6778.135 Safari/537.36 Edg/131.0.2903.70"
        private const val UA_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
    }
}
