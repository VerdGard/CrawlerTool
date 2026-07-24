package com.SoloSu.Crawler_tool

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil.Coil
import coil.api.load
import coil.request.CachePolicy
import coil.transform.RoundedCornersTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 图片预览对话框 (P1.7)
 * 从图片URL加载并展示，支持缩放、保存到相册
 */
object ImagePreviewDialog {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun show(context: Context, imageUrl: String) {
        val density = context.resources.displayMetrics.density

        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (350 * density).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#1A000000"))
            // 加载图片
            load(imageUrl) {
                crossfade(300)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                transformations(RoundedCornersTransformation(8f))
            }
        }

        val btnSave = MaterialButton(context).apply {
            text = "保存到相册"
            icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_save)
            setOnClickListener {
                saveImage(context, imageUrl)
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 12)
            addView(imageView)
            val btnLayout = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt(); gravity = Gravity.CENTER_HORIZONTAL }
            addView(btnSave, btnLayout)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("图片预览")
            .setView(container)
            .setPositiveButton("关闭", null)
            .show()
    }

    fun showGallery(context: Context, imageUrls: List<String>, startIndex: Int = 0) {
        if (imageUrls.isEmpty()) {
            Toast.makeText(context, "未检测到图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 简单实现：显示第一个图片
        // 实际可扩展为 ViewPager 画廊
        show(context, imageUrls[startIndex.coerceIn(0, imageUrls.lastIndex)])

        if (imageUrls.size > 1) {
            Toast.makeText(context, "共 ${imageUrls.size} 张图片，可逐张查看", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImage(context: Context, imageUrl: String) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: throw Exception("空响应")

            val fileName = "Crawler_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CrawlerTool")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(bytes)
                    }
                    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                // 通知媒体扫描
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 从结果列表中提取图片URL
     */
    fun extractImageUrls(results: List<String>): List<String> {
        val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg")
        return results.filter { result ->
            val lower = result.lowercase().trim()
            imageExtensions.any { lower.contains(it) } || lower.startsWith("http")
        }.map { it.trim() }
    }
}
