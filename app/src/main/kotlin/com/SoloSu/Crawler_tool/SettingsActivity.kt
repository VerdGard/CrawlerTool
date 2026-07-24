package com.SoloSu.Crawler_tool

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
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

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun getUserAgent(context: Context): String {
            val prefs = getPreferences(context)
            return prefs.getString(KEY_USER_AGENT, "") ?: ""
        }

        fun getConnectTimeout(context: Context): Long {
            val prefs = getPreferences(context)
            return prefs.getLong(KEY_CONNECT_TIMEOUT, 15L)
        }

        fun getReadTimeout(context: Context): Long {
            val prefs = getPreferences(context)
            return prefs.getLong(KEY_READ_TIMEOUT, 30L)
        }

        fun getTheme(context: Context): String {
            val prefs = getPreferences(context)
            return prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 前更新全局夜间模式并应用主题
        val savedTheme = getTheme(this)
        when (savedTheme) {
            THEME_LIGHT -> {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                setTheme(R.style.AppTheme_Light)
            }
            THEME_DARK -> {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                setTheme(R.style.AppTheme_Dark)
            }
            else -> {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
    }

    private fun setupListeners() {
        val prefs = getPreferences(this)

        // User-Agent 自动保存
        binding.etUserAgent.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveUserAgent(prefs)
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
            // setDefaultNightMode 会更新全局配置并自动重建当前 Activity(保留在设置页面)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            when (theme) {
                THEME_LIGHT -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun saveUserAgent(prefs: SharedPreferences) {
        val ua = binding.etUserAgent.text?.toString()?.trim() ?: ""
        prefs.edit().putString(KEY_USER_AGENT, ua).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
