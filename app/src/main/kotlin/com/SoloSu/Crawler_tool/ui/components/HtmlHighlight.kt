package com.SoloSu.Crawler_tool.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.SoloSu.Crawler_tool.ui.theme.EditorColors
import java.util.regex.Pattern

/**
 * HTML 语法高亮的 Compose 实现。
 *
 * 与原 ResultAdapter 的 Spannable 版本保持同一套着色规则:
 * 标签名=tag,属性名=attr,属性值=value,括号与符号=text。
 * 结果缓存为 [AnnotatedString],滚动时同一内容不会重复跑正则。
 */
private val TAG_BLOCK: Pattern = Pattern.compile("<[^>]+>")

private val TAG_TOKEN: Pattern = Pattern.compile(
    "(</?)" +
        "|(/?>)" +
        "|([a-zA-Z][\\w-]*)" +
        "|(=\")" +
        "|(=\')" +
        "|(=\"[^\"]*\")" +
        "|(='[^']*')" +
        "|([\\w./#:-]+)"
)

private val ATTR_PATTERN: Pattern = Pattern.compile("@(\\w[\\w-]*)")

private val COMMON_ATTRS = listOf(
    "href", "src", "title", "data-src", "data-original", "alt", "class", "id"
)

/** 高亮缓存,LRU 上限 32 条,避免长列表滚动时内存膨胀。 */
private val highlightCache = object : LinkedHashMap<String, AnnotatedString>(64, 0.75f, true) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, AnnotatedString>
    ): Boolean = size > 32
}

fun clearHighlightCache() = synchronized(highlightCache) { highlightCache.clear() }

/** 取一条结果的语法高亮文本。 */
@Composable
fun rememberHighlightedHtml(raw: String, colors: EditorColors): AnnotatedString =
    remember(raw, colors) {
        synchronized(highlightCache) { highlightCache[raw] } ?: buildHighlight(raw, colors).also {
            synchronized(highlightCache) { highlightCache[raw] = it }
        }
    }

private fun buildHighlight(raw: String, colors: EditorColors): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val matcher = TAG_BLOCK.matcher(raw)
    var lastEnd = 0
    while (matcher.find()) {
        if (matcher.start() > lastEnd) {
            builder.append(raw.substring(lastEnd, matcher.start()))
        }
        appendColorizedTag(builder, matcher.group(), colors)
        lastEnd = matcher.end()
    }
    if (lastEnd < raw.length) builder.append(raw.substring(lastEnd))
    return builder.toAnnotatedString()
}

/** 对单个 `<...>` 片段着色。 */
private fun appendColorizedTag(
    builder: AnnotatedString.Builder,
    tag: String,
    colors: EditorColors,
) {
    val m = TAG_TOKEN.matcher(tag)
    var last = 0
    var pendingValue = false
    while (m.find()) {
        if (m.start() > last) builder.append(tag.substring(last, m.start()))
        val bracketOpen = m.group(1)
        val bracketClose = m.group(2)
        val nameToken = m.group(3)
        val equalsQuote = m.group(4)
        val equalsApos = m.group(5)
        val quotedValue = m.group(6)
        val aposValue = m.group(7)
        val bareValue = m.group(8)

        when {
            bracketOpen != null -> builder.appendStyled(bracketOpen, colors.text)
            bracketClose != null -> builder.appendStyled(bracketClose, colors.text)
            equalsQuote != null || equalsApos != null -> {
                builder.appendStyled(if (equalsQuote != null) "=\"" else "='", colors.text)
                pendingValue = true
            }
            quotedValue != null -> {
                builder.appendStyled(quotedValue, colors.value)
                pendingValue = false
            }
            aposValue != null -> {
                builder.appendStyled(aposValue, colors.value)
                pendingValue = false
            }
            nameToken != null -> {
                if (pendingValue) {
                    builder.appendStyled(nameToken, colors.value)
                    pendingValue = false
                } else {
                    val before = tag.substring(0, m.start())
                    val isTagName = before.contains("<") && before.substringAfter('<').isBlank()
                    builder.appendStyled(nameToken, if (isTagName) colors.tag else colors.attr)
                }
            }
            bareValue != null -> builder.appendStyled(bareValue, colors.value)
        }
        last = m.end()
    }
    if (last < tag.length) builder.append(tag.substring(last))
}

private fun AnnotatedString.Builder.appendStyled(text: String, color: Color) {
    pushStyle(SpanStyle(color = color, fontFamily = FontFamily.Monospace))
    append(text)
    pop()
}

/**
 * 从结果内容里提炼属性标签,用于卡片首行的摘要。
 * 提取不到 `@attr` 时退回常见属性名扫描,保证已有摘要可显示。
 */
fun extractAttrTags(content: String): String {
    val tags = LinkedHashSet<String>()
    val matcher = ATTR_PATTERN.matcher(content)
    while (matcher.find() && tags.size < 6) tags.add("@" + matcher.group(1))

    val mentionsText = content.contains("text()", true) || content.contains("text=\"", true)
    if (mentionsText) tags.add("text()")

    if (tags.isEmpty()) {
        for (attr in COMMON_ATTRS) {
            if (content.contains(attr, true)) {
                tags.add("@$attr")
                if (tags.size >= 6) break
            }
        }
    }
    return tags.joinToString("  ")
}

/** 估算换行后的视觉行数,用于判断是否需要「展开」。 */
fun estimateLineCount(text: String): Int {
    if (text.isEmpty()) return 0
    return text.split('\n').sumOf { line ->
        if (line.isEmpty()) 1 else line.length / 40 + 1
    }
}
