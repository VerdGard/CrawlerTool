package com.SoloSu.Crawler_tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 正则表达式测试器对话框 (P1.9)
 * 实时输入正则 + 测试文本，高亮显示匹配结果
 */
object RegexTesterDialog {

    fun show(context: Context, initialPattern: String = "", initialText: String = "") {
        val density = context.resources.displayMetrics.density

        // 主容器
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        // 正则输入
        val patternLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "正则表达式"
            isHintEnabled = true
            setStartIconDrawable(android.R.drawable.ic_menu_edit)
        }
        val patternInput = TextInputEditText(context).apply {
            setText(initialPattern)
            setTypeface(Typeface.MONOSPACE)
            textSize = 14f
        }
        patternLayout.addView(patternInput)
        container.addView(patternLayout)

        // 测试文本输入
        val textLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
            hint = "测试文本"
            isHintEnabled = true
            setStartIconDrawable(android.R.drawable.ic_menu_edit)
        }
        val textInput = TextInputEditText(context).apply {
            setText(initialText)
            setTypeface(Typeface.MONOSPACE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (150 * density).toInt()
            )
            gravity = Gravity.START or Gravity.TOP
        }
        textLayout.addView(textInput)
        container.addView(textLayout)

        // 匹配结果标题
        val resultTitle = TextView(context).apply {
            text = "匹配结果"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.md_primary))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt(); bottomMargin = (4 * density).toInt() }
        }
        container.addView(resultTitle)

        // 匹配结果展示区
        val resultView = TextView(context).apply {
            setTypeface(Typeface.MONOSPACE)
            textSize = 13f
            setPadding(8, 8, 8, 8)
            minHeight = (60 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.input_bg))
        }
        val scrollResult = ScrollView(context).apply {
            addView(resultView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (120 * density).toInt()
            )
        }
        container.addView(scrollResult)

        // 匹配计数
        val countView = TextView(context).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        }
        container.addView(countView)

        // 实时更新匹配结果
        fun updateMatches() {
            val pattern = patternInput.text?.toString()?.trim() ?: ""
            val text = textInput.text?.toString() ?: ""

            if (pattern.isEmpty() || text.isEmpty()) {
                resultView.text = "等待输入..."
                countView.text = ""
                return
            }

            try {
                val regex = Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
                val matches = regex.findAll(text).toList()

                if (matches.isEmpty()) {
                    resultView.text = "无匹配结果"
                    countView.text = "共 0 处匹配"
                    countView.setTextColor(ContextCompat.getColor(context, R.color.html_value_color))
                    return
                }

                // 高亮显示匹配
                val sb = SpannableStringBuilder()
                var lastEnd = 0
                var groupIndex = 0
                val groupColors = listOf(
                    ContextCompat.getColor(context, R.color.md_primary),
                    ContextCompat.getColor(context, R.color.html_value_color),
                    ContextCompat.getColor(context, R.color.html_tag_color),
                    ContextCompat.getColor(context, R.color.md_secondary)
                )

                for (match in matches) {
                    // 未匹配部分
                    if (match.range.first > lastEnd) {
                        sb.append(text.substring(lastEnd, match.range.first))
                    }

                    // 完整匹配
                    val start = sb.length
                    sb.append(match.value)
                    sb.setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), start, sb.length, 0)
                    sb.setSpan(
                        BackgroundColorSpan(groupColors[groupIndex % groupColors.size]),
                        start, sb.length, 0
                    )
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, 0)

                    lastEnd = match.range.last + 1
                    groupIndex++
                }

                // 剩余未匹配部分
                if (lastEnd < text.length) {
                    sb.append(text.substring(lastEnd))
                }

                resultView.text = sb
                countView.text = "共 ${matches.size} 处匹配"
                countView.setTextColor(ContextCompat.getColor(context, R.color.md_primary))

            } catch (e: Exception) {
                resultView.text = "正则错误: ${e.message}"
                countView.text = ""
                countView.setTextColor(ContextCompat.getColor(context, R.color.html_value_color))
            }
        }

        patternInput.doAfterTextChanged { updateMatches() }
        textInput.doAfterTextChanged { updateMatches() }
        // 初始触发
        updateMatches()

        // 底部按钮行
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }

        val btnCopy = MaterialButton(context).apply {
            text = "复制结果"
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Regex Result", resultView.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(btnCopy)

        container.addView(btnRow)

        MaterialAlertDialogBuilder(context)
            .setTitle("正则表达式测试器")
            .setView(container)
            .setPositiveButton("关闭", null)
            .show()
    }
}
