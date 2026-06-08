package com.SoloSu.Crawler_tool

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 已废弃 — 使用 HtmlTreeDialog 替代。
 * 保留此文件仅用于兼容已存在的引用路径；实际功能由 HtmlTreeDialog 提供。
 */
@Deprecated("Use HtmlTreeDialog.show() instead")
class HtmlTreeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "已迁移到 HtmlTreeDialog", Toast.LENGTH_SHORT).show()
        finish()
    }
}
