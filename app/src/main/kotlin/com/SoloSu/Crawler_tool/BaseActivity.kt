package com.SoloSu.Crawler_tool

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * 所有 Activity 的基类,集中管理主题与莫奈取色。
 *
 * 执行顺序:
 * 1. [AppCompatDelegate.setDefaultNightMode] — 同步日/夜设置
 * 2. [setTheme] — 应用唯一的 [R.style.AppTheme]
 * 3. super.onCreate — 让 AppCompatDelegate 完成主题安装
 * 4. [DynamicColors] — 若开关开启,叠加壁纸取色的 ThemeOverlay 覆盖整套角色色
 *
 * 取色是「叠加」而非「替换」:DynamicColors 读取主题里的
 * dynamicColorThemeOverlay 属性再 applyStyle 一层,因此未覆盖到的角色
 * 仍然落回 values/values-night 里的基线色板。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = SettingsRepository(this)
        applyNightMode(settings.getTheme())
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // 必须放在 super.onCreate 之后:AppCompatDelegate 会在 super.onCreate 里
        // 安装自己的主题叠加层,若更早 applyStyle 会被其覆盖而失效。
        // 同时要早于子类的 setContentView,否则已 inflate 的视图拿不到新颜色。
        applyMonetIfEnabled(settings.isMonetEnabled())
    }

    private fun applyNightMode(theme: String) {
        val mode = when (theme) {
            SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun applyMonetIfEnabled(enabled: Boolean) {
        if (!enabled) return
        if (!DynamicColors.isDynamicColorAvailable()) return
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ -> true }
            .build()
        DynamicColors.applyToActivityIfAvailable(this, options)
    }
}
