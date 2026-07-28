package com.SoloSu.Crawler_tool

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.SoloSu.Crawler_tool.repository.SettingsRepository

/**
 * 所有 Activity 的基类,集中管理主题应用逻辑。
 *
 * 在 [onCreate] 中先读取用户保存的主题偏好,依次调用:
 * 1. [AppCompatDelegate.setDefaultNightMode] — 同步 AppCompat 内部状态
 * 2. [setTheme] — 设置 Activity 样式
 *
 * 子类无需再处理主题逻辑,直接调用 super.onCreate(savedInstanceState) 即可。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applySavedTheme() {
        val theme = SettingsRepository(this).getTheme()
        when (theme) {
            SettingsRepository.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                setTheme(R.style.AppTheme_Light)
            }
            SettingsRepository.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                setTheme(R.style.AppTheme_Dark)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                setTheme(R.style.AppTheme_System)
            }
        }
    }
}