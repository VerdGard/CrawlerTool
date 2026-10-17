package com.SoloSu.Crawler_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SoloSu.Crawler_tool.R

/**
 * 极简 Markdown 渲染器。
 *
 * 只覆盖 xpath_rules.md 实际用到的子集:标题、段落、代码块、
 * 引用、表格、有序/无序列表、分隔线与行内 `code` / **粗体**。
 * 与其引入完整 Markdown 库(体积与 lint 成本),不如按文档实际用到的语法实现。
 */

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Code(val code: String) : Block
    data class Quote(val text: String) : Block
    data class Bullets(val items: List<String>, val ordered: Boolean) : Block
    data class Table(val rows: List<List<String>>) : Block
    data object Divider : Block
}

/** 把 Markdown 拆成块级结构,渲染时逐块输出。 */
private fun parseBlocks(md: String): List<Block> {
    val lines = md.split('\n')
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()

        when {
            t.startsWith("```") -> {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.add(lines[i])
                    i++
                }
                blocks.add(Block.Code(code.joinToString("\n")))
                i++
            }

            t.startsWith("> ") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("> ")) {
                    quote.add(lines[i].trim().removePrefix("> "))
                    i++
                }
                blocks.add(Block.Quote(quote.joinToString("\n")))
            }

            t.startsWith("### ") -> { blocks.add(Block.Heading(3, t.removePrefix("### ").trim())); i++ }
            t.startsWith("## ") -> { blocks.add(Block.Heading(2, t.removePrefix("## ").trim())); i++ }
            t.startsWith("# ") -> { blocks.add(Block.Heading(1, t.removePrefix("# ").trim())); i++ }

            t.startsWith("|") && t.endsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size &&
                    lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")
                ) {
                    val row = lines[i].trim()
                    // 跳过 |---|---| 这样的分隔行
                    if (row.matches(Regex("^\\|[\\s\\-:|]+\\|$"))) {
                        i++
                        continue
                    }
                    rows.add(row.split('|').filter { it.isNotEmpty() }.map { it.trim() })
                    i++
                }
                if (rows.isNotEmpty()) blocks.add(Block.Table(rows))
            }

            t == "---" -> { blocks.add(Block.Divider); i++ }

            t.startsWith("- ") || t.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i].trim()
                    if (cur.startsWith("- ") || cur.startsWith("* ")) {
                        items.add(cur.removePrefix("- ").removePrefix("* ").trim())
                        i++
                    } else if (cur.isEmpty()) {
                        i++
                        break
                    } else break
                }
                blocks.add(Block.Bullets(items, ordered = false))
            }

            t.matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i].trim()
                    val m = Regex("^\\d+\\.\\s(.*)").matchEntire(cur)
                    if (m != null) {
                        items.add(m.groupValues[1])
                        i++
                    } else if (cur.isEmpty()) {
                        i++
                        break
                    } else break
                }
                blocks.add(Block.Bullets(items, ordered = true))
            }

            t.isEmpty() -> i++

            else -> {
                val para = mutableListOf(raw)
                i++
                while (i < lines.size) {
                    val next = lines[i].trim()
                    val isBlockStart = next.isEmpty() ||
                        next.startsWith("```") || next.startsWith("# ") ||
                        next.startsWith("## ") || next.startsWith("### ") ||
                        next.startsWith("|") || next.startsWith("---") ||
                        next.startsWith("> ") || next.startsWith("- ") ||
                        next.startsWith("* ") || next.matches(Regex("^\\d+\\.\\s.*"))
                    if (isBlockStart) break
                    para.add(lines[i])
                    i++
                }
                blocks.add(Block.Paragraph(para.joinToString("\n")))
            }
        }
    }
    return blocks
}

/** 行内样式:`code` 与 **粗体**。 */
private fun inlineMarkdown(text: String, codeColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var rest = text
        while (rest.isNotEmpty()) {
            val codeAt = rest.indexOf('`')
            val boldAt = rest.indexOf("**")
            when {
                codeAt >= 0 && (boldAt < 0 || codeAt < boldAt) -> {
                    val end = rest.indexOf('`', codeAt + 1)
                    if (end > codeAt) {
                        append(rest.substring(0, codeAt))
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = codeColor,
                            )
                        ) { append(rest.substring(codeAt + 1, end)) }
                        rest = rest.substring(end + 1)
                    } else {
                        append(rest[0]); rest = rest.substring(1)
                    }
                }
                boldAt >= 0 -> {
                    val end = rest.indexOf("**", boldAt + 2)
                    if (end > boldAt) {
                        append(rest.substring(0, boldAt))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(rest.substring(boldAt + 2, end))
                        }
                        rest = rest.substring(end + 2)
                    } else {
                        append(rest[0]); rest = rest.substring(1)
                    }
                }
                else -> { append(rest); rest = "" }
            }
        }
    }

/** 渲染整篇文档。 */
@Composable
fun MarkdownDocument(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val inlineCodeColor = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PageHorizontal),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            top = if (block.level == 1) 8.dp else 16.dp,
                            bottom = 6.dp,
                        ),
                    )
                }

                is Block.Paragraph -> {
                    Text(
                        text = inlineMarkdown(block.text, inlineCodeColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                is Block.Code -> {
                    CodeBlockWithCopy(block.code)
                }

                is Block.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = inlineMarkdown(block.text, inlineCodeColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is Block.Bullets -> {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = if (block.ordered) "${index + 1}." else "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(20.dp),
                                )
                                Text(
                                    text = inlineMarkdown(item, inlineCodeColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                is Block.Table -> {
                    MarkdownTable(block.rows, inlineCodeColor)
                }

                Block.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.PageBottom))
    }
}

/** 表格:列多时横向滚动,不换行挤压内容。 */
@Composable
private fun MarkdownTable(
    rows: List<List<String>>,
    inlineCodeColor: androidx.compose.ui.graphics.Color,
) {
    val columnCount = rows.maxOf { it.size }
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            rows.forEachIndexed { rowIndex, cells ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier.background(
                        if (isHeader) MaterialTheme.colorScheme.primaryContainer
                        else if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                    for (col in 0 until columnCount) {
                        val text = cells.getOrElse(col) { "" }
                        Text(
                            text = inlineMarkdown(text, inlineCodeColor),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.Normal,
                            color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(TABLE_CELL_WIDTH)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private val TABLE_CELL_WIDTH = 148.dp

/** 代码块 + 复制按钮。复制反馈用按钮文案变化表示,不弹 Toast 打断阅读。 */
@Composable
private fun CodeBlockWithCopy(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(10.dp),
            ),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        )
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (copied) "已复制" else "复制",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
