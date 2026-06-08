package com.SoloSu.Crawler_tool

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * HTML 工具方法：语法高亮（基于标准 DOM API）
 */
object HtmlUtil {

    private val HIGHLIGHT_PATTERN = Pattern.compile(
        "(</?)" +
        "|(/?>)" +
        "|([a-zA-Z][\\w-]*)" +
        "|(=\"[^\"]*\")" +
        "|(='[^']*')" +
        "|(=)"
    )

    /**
     * 为 HTML 标签字符串生成语法高亮的 SpannableString
     * 标签名=蓝色, 属性名=红色, 属性值=绿色, 括号=默认文本色
     */
    fun highlightTagHtml(context: Context, tagHtml: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val tagColor = ContextCompat.getColor(context, R.color.html_tag_color)
        val attrColor = ContextCompat.getColor(context, R.color.html_attr_color)
        val valueColor = ContextCompat.getColor(context, R.color.html_value_color)
        val textColor = ContextCompat.getColor(context, R.color.html_text_color)

        val pattern = Pattern.compile(
            "(</?)" +
            "|(/?>)" +
            "|([a-zA-Z][\\w-]*)" +
            "|(=\"[^\"]*\")" +
            "|(='[^']*')" +
            "|(=)"
        )
        val matcher = pattern.matcher(tagHtml)
        var lastEnd = 0
        var afterEquals = false

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                val between = tagHtml.substring(lastEnd, matcher.start())
                sb.append(between)
            }

            val bracketOpen = matcher.group(1)
            val bracketClose = matcher.group(2)
            val nameToken = matcher.group(3)
            val quotedDValue = matcher.group(4)
            val quotedSValue = matcher.group(5)
            val eqSign = matcher.group(6)

            when {
                bracketOpen != null -> {
                    val s = SpannableStringBuilder(bracketOpen)
                    s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                    sb.append(s)
                }
                bracketClose != null -> {
                    val s = SpannableStringBuilder(bracketClose)
                    s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                    sb.append(s)
                }
                afterEquals -> {
                    if (quotedDValue != null) {
                        val s = SpannableStringBuilder(quotedDValue)
                        s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                        sb.append(s)
                    } else if (quotedSValue != null) {
                        val s = SpannableStringBuilder(quotedSValue)
                        s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                        sb.append(s)
                    } else if (nameToken != null) {
                        val s = SpannableStringBuilder(nameToken)
                        s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                        sb.append(s)
                    }
                    afterEquals = false
                }
                quotedDValue != null -> {
                    val s = SpannableStringBuilder(quotedDValue)
                    s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                    sb.append(s)
                }
                quotedSValue != null -> {
                    val s = SpannableStringBuilder(quotedSValue)
                    s.setSpan(ForegroundColorSpan(valueColor), 0, s.length, 0)
                    sb.append(s)
                }
                eqSign != null -> {
                    val s = SpannableStringBuilder(eqSign)
                    s.setSpan(ForegroundColorSpan(textColor), 0, s.length, 0)
                    sb.append(s)
                    afterEquals = true
                }
                nameToken != null -> {
                    val before = tagHtml.substring(0, matcher.start())
                    val prevChar = before.takeLastWhile { it == ' ' || it == '\t' }.let {
                        before.dropLast(it.length).lastOrNull()
                    }
                    val isAttributeName = prevChar == ' ' || prevChar == '\t' || prevChar == '\n'
                    if (isAttributeName) {
                        val s = SpannableStringBuilder(nameToken)
                        s.setSpan(ForegroundColorSpan(attrColor), 0, s.length, 0)
                        sb.append(s)
                    } else {
                        val s = SpannableStringBuilder(nameToken)
                        s.setSpan(ForegroundColorSpan(tagColor), 0, s.length, 0)
                        sb.append(s)
                    }
                }
            }
            lastEnd = matcher.end()
        }
        if (lastEnd < tagHtml.length) {
            sb.append(tagHtml.substring(lastEnd))
        }
        return sb
    }
}
