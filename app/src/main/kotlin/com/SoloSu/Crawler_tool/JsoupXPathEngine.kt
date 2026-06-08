package com.SoloSu.Crawler_tool

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * 基于 Jsoup DOM 树的 XPath 求值引擎。
 *
 * 支持：
 * - 绝对路径 `/html/body/div`, 相对路径 `//div`
 * - 属性访问 `//a/@href`, `//div/text()`
 * - 谓词 `[@class='foo']`, `[@attr]`, `[n]`,
 *   `[contains(@class,'foo')]`, `[starts-with(@attr,'val')]`,
 *   `[last()]`, `[position()<n]`, `[normalize-space()]`
 * - 复合谓词 `[@class='a' and @id='b']`, `[@class='a' or @class='b']`
 * - 通配符 `*`, 父节点 `..`, 当前节点 `.`
 * - 单引号/双引号均可
 */
object JsoupXPathEngine {
    // ─── 表达式缓存 ────────────────────────────────────────────
    // 对相同 XPath 表达式缓存解析后的步骤，避免重复解析
    private val stepCache = object : LinkedHashMap<String, List<XPathStep>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<XPathStep>>): Boolean {
            return size > 16
        }
    }


    // ─── 公开入口 ──────────────────────────────────────────────

    /** 解析 HTML 并执行 XPath，返回匹配结果的字符串列表 */
    fun evaluate(html: String, expression: String): List<String> {
        val doc = Jsoup.parse(html)
        return evaluateOn(doc, expression.trim())
    }

    /** 在已有 Document 上执行 XPath */
    fun evaluateOn(doc: Document, expression: String): List<String> {
        return evaluateOn(doc as Element, expression)
    }

    /** 在任意 Element 上执行 XPath */
    fun evaluateOn(context: Element, expression: String): List<String> {
        @Synchronized
        fun getSteps(expr: String): List<XPathStep> =
            stepCache.getOrPut(expr) { parseSteps(expr) }
        val steps = getSteps(expression)
        if (steps.isEmpty() && expression.isNotBlank()) {
            throw RuntimeException("无法解析XPath表达式: $expression")
        }
        if (steps.isEmpty()) {
            return emptyList()
        }

        var nodes = listOf(context)

        for (step in steps) {
            when {
                // 属性访问（最终步骤）
                step.isAttribute -> {
                    val attrName = step.nodeTest.removePrefix("@")
                    return if (attrName == "*") {
                        nodes.flatMap { el ->
                            el.attributes().asList().map { "${it.key}=${it.value}" }
                        }
                    } else {
                        nodes.map { el -> el.attr(attrName) }
                    }
                }
                // text() 中间/最终步骤
                step.isText -> {
                    return nodes.flatMap { el ->
                        if (step.nodeTest == "text()") {
                            el.textNodes().map { it.text().trim() }.filter { it.isNotEmpty() }
                        } else {
                            // normalize-space()
                            listOf(el.textNodes().joinToString(" ") { it.text().trim() }
                                .replace(Regex("\\s+"), " ").trim())
                        }
                    }
                }
                // ..
                step.isParent -> {
                    nodes = nodes.mapNotNull { it.parent() }
                }
                // .
                step.isSelf -> { /* 保持 nodes 不变 */ }
                // 正常步骤
                else -> {
                    nodes = evaluateStep(nodes, step)
                }
            }
        }

        return nodes.map { it.outerHtml() }
    }

    // ─── 步骤定义 ──────────────────────────────────────────────

    private data class XPathStep(
        val axis: Axis,
        val nodeTest: String,
        val predicates: List<PredicateExpr> = emptyList()
    ) {
        val isAttribute: Boolean get() = nodeTest.startsWith("@")
        val isText: Boolean
            get() = nodeTest == "text()" || nodeTest == "normalize-space()"
        val isParent: Boolean get() = nodeTest == ".."
        val isSelf: Boolean get() = nodeTest == "."
        val isWildcard: Boolean get() = nodeTest == "*"
        val tagName: String get() = nodeTest
    }

    private enum class Axis { CHILD, DESCENDANT }

    // ─── 谓词表达式 ────────────────────────────────────────────

    private sealed class PredicateExpr {
        abstract fun matches(el: Element, index: Int, siblings: List<Element>): Boolean
    }

    private data class IndexPredicate(val index: Int) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) = i == index
    }

    private class LastPredicate : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            i == siblings.size - 1
    }

    private data class PositionPredicate(val op: String, val value: Int) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>): Boolean {
            val pos = i + 1
            return when (op) {
                "="  -> pos == value
                "!=" -> pos != value
                "<"  -> pos < value
                ">"  -> pos > value
                "<=" -> pos <= value
                ">=" -> pos >= value
                else -> false
            }
        }
    }

    private data class AttrExistsPredicate(val attr: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.hasAttr(attr)
    }

    private data class AttrEqualsPredicate(val attr: String, val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr) == value
    }

    private data class AttrNotEqualsPredicate(val attr: String, val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr) != value
    }

    private data class AttrContainsPredicate(val attr: String, val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr).contains(value)
    }

    private data class AttrStartsWithPredicate(val attr: String, val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr).startsWith(value)
    }

    private data class TextEqualsPredicate(val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.ownText().trim() == value
    }

    private data class TextContainsPredicate(val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.ownText().contains(value)
    }

    private class TextNormalizeSpacePredicate : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.ownText().trim().isNotEmpty()
    }

    private class AndPredicate(
        val left: PredicateExpr, val right: PredicateExpr
    ) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            left.matches(el, i, siblings) && right.matches(el, i, siblings)
    }

    private class OrPredicate(
        val left: PredicateExpr, val right: PredicateExpr
    ) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            left.matches(el, i, siblings) || right.matches(el, i, siblings)
    }

    // ─── 步骤解析 ──────────────────────────────────────────────

    /**
     * 将 XPath 表达式解析为步骤列表。
     * 示例：
     *   "//a/@href"        → [DESCENDANT:"a"] + attribute "@href"
     *   "/html/body/div"   → [CHILD:"html", CHILD:"body", CHILD:"div"]
     *   "//div[@class='a']/span" → [DESCENDANT:"div", CHILD:"span"]
     */
    private fun parseSteps(expression: String): List<XPathStep> {
        val expr = expression.trim()
        if (expr.isEmpty()) return emptyList()

        val steps = mutableListOf<XPathStep>()
        var pos = 0

        while (pos < expr.length) {
            // 处理开头的 /
            var axis = Axis.CHILD
            if (expr[pos] == '/') {
                pos++
                if (pos < expr.length && expr[pos] == '/') {
                    axis = Axis.DESCENDANT
                    pos++
                }
            }

            if (pos >= expr.length) break

            // 提取 nodeTest + 谓词
            val remaining = expr.substring(pos)
            val parsed = parseNodeTestAndPredicates(remaining)
            steps.add(
                XPathStep(
                    axis = axis,
                    nodeTest = parsed.nodeTest,
                    predicates = parsed.predicates
                )
            )
            pos += parsed.consumedLength
        }

        return steps
    }

    private data class StepParseResult(
        val nodeTest: String,
        val predicates: List<PredicateExpr>,
        val consumedLength: Int
    )

    private fun parseNodeTestAndPredicates(input: String): StepParseResult {
        var pos = 0
        val len = input.length

        // 提取 nodeTest
        val nodeTest: String = when {
            // @attr
            input[pos] == '@' -> {
                val start = pos
                pos++
                while (pos < len && input[pos] !in charArrayOf('[', '/')) pos++
                input.substring(start, pos)
            }
            // text() | normalize-space() | node()
            input.substring(pos).startsWith("text()") -> {
                pos += 6; "text()"
            }
            input.substring(pos).startsWith("normalize-space()") -> {
                pos += 17; "normalize-space()"
            }
            input.substring(pos).startsWith("comment()") -> {
                pos += 9; "comment()"
            }
            input.substring(pos).startsWith("node()") -> {
                pos += 6; "node()"
            }
            // .. | .
            input[pos] == '.' -> {
                if (pos + 1 < len && input[pos + 1] == '.') {
                    pos += 2; ".."
                } else {
                    pos++; "."
                }
            }
            // * (wildcard)
            input[pos] == '*' -> {
                pos++; "*"
            }
            // 标签名
            input[pos].isLetter() || input[pos] == '_' -> {
                val start = pos
                while (pos < len && input[pos] !in charArrayOf('[', '/', ':')) pos++
                var tag = input.substring(start, pos)
                // 跳过 namespace 前缀
                if (pos < len && input[pos] == ':') {
                    pos++
                    val tagStart = pos
                    while (pos < len && input[pos] !in charArrayOf('[', '/')) pos++
                    tag = input.substring(tagStart, pos)
                }
                tag
            }
            else -> {
                // 无法解析，全部返回
                val rest = input.substring(pos)
                pos = len
                rest
            }
        }

        // 解析谓词 [...]
        val predicates = mutableListOf<PredicateExpr>()
        while (pos < len && input[pos] == '[') {
            val bracketResult = parseBracketPredicate(input, pos)
            predicates.addAll(bracketResult.predicates)
            pos = bracketResult.endPos
        }

        return StepParseResult(nodeTest, predicates, pos)
    }

    private fun parseBracketPredicate(input: String, startPos: Int): BracketResult {
        val inner = extractBracketContent(input, startPos)
        val endPos = startPos + inner.length + 2
        val predicates = parsePredicateInner(inner)
        return BracketResult(predicates, endPos)
    }

    private data class BracketResult(
        val predicates: List<PredicateExpr>,
        val endPos: Int
    )

    /** 提取 [...] 中的内容，正确处理嵌套 */
    private fun extractBracketContent(input: String, startPos: Int): String {
        var depth = 0
        var pos = startPos
        while (pos < input.length) {
            when (input[pos]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return input.substring(startPos + 1, pos)
                }
            }
            pos++
        }
        return input.substring(startPos + 1)
    }

    /** 将引号内文本替换为占位符，保护引号内的内容不被分割 */
    private fun extractQuotedSegments(input: String): Pair<String, List<String>> {
        val placeholders = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when (c) {
                '\'' -> {
                    val end = input.indexOf('\'', i + 1)
                    if (end != -1) {
                        placeholders.add(input.substring(i + 1, end))
                        sb.append("\${Q$placeholders.size}")
                        i = end + 1
                    } else { sb.append(c); i++ }
                }
                '"' -> {
                    val end = input.indexOf('"', i + 1)
                    if (end != -1) {
                        placeholders.add(input.substring(i + 1, end))
                        sb.append("\${Q$placeholders.size}")
                        i = end + 1
                    } else { sb.append(c); i++ }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString() to placeholders
    }

    /** 解析谓词内部，支持 and/or */
    private fun parsePredicateInner(inner: String): List<PredicateExpr> {
        val trimmed = inner.trim()
        val (masked, quoted) = extractQuotedSegments(trimmed)

        // 恢复引号后解析单个条件
        fun restore(s: String): String {
            var result = s
            for (i in quoted.indices) {
                result = result.replace("\${Q${i + 1}}", "'${quoted[i]}'")
            }
            return result
        }

        val andParts = splitByLogicalOp(masked, "and")
        if (andParts.size > 1) {
            var result: PredicateExpr? = null
            for (part in andParts) {
                val single = parseSinglePredicate(restore(part.trim()))
                if (single != null) {
                    result = if (result == null) single else AndPredicate(result, single)
                }
            }
            return if (result != null) listOf(result) else emptyList()
        }

        val orParts = splitByLogicalOp(masked, "or")
        if (orParts.size > 1) {
            var result: PredicateExpr? = null
            for (part in orParts) {
                val single = parseSinglePredicate(restore(part.trim()))
                if (single != null) {
                    result = if (result == null) single else OrPredicate(result, single)
                }
            }
            return if (result != null) listOf(result) else emptyList()
        }

        val single = parseSinglePredicate(trimmed)
        return if (single != null) listOf(single) else emptyList()
    }

    /** 按逻辑运算符分割，跳过引号内内容 */
    private fun splitByLogicalOp(input: String, op: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when (c) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"'  -> if (!inSingle) inDouble = !inDouble
            }
            if (!inSingle && !inDouble && input.substring(i).startsWith(op)) {
                val before = if (i > 0) input[i - 1] else ' '
                val afterIdx = i + op.length
                val after = if (afterIdx < input.length) input[afterIdx] else ' '
                if (before == ' ' && after == ' ') {
                    parts.add(current.toString())
                    current.clear()
                    i += op.length
                    continue
                }
            }
            current.append(c)
            i++
        }
        parts.add(current.toString())
        return parts.filter { it.isNotBlank() }
    }

    /** 提取引号内的值，兼容 ' 和 " */
    private fun extractQuotedValue(input: String, startIdx: Int): Pair<String, Int>? {
        if (startIdx >= input.length) return null
        val quote = input[startIdx]
        if (quote != '\'' && quote != '"') return null
        val end = input.indexOf(quote, startIdx + 1)
        if (end == -1) return null
        return input.substring(startIdx + 1, end) to (end + 1)
    }

    /** 解析单个谓词条件 */
    private fun parseSinglePredicate(expr: String): PredicateExpr? {
        val e = expr.trim()
        if (e.isEmpty()) return null

        // [n] — 纯数字索引
        e.toIntOrNull()?.let { n -> return IndexPredicate(n - 1) }

        // [last()]
        if (e == "last()" || e.matches(Regex("last\\(\\)\\s*"))) return LastPredicate()

        // [position() op value]
        val posMatch = Regex("position\\s*\\(\\s*\\)\\s*([=!<>]=?)\\s*(\\d+)").find(e)
        if (posMatch != null) {
            return PositionPredicate(posMatch.groupValues[1], posMatch.groupValues[2].toInt())
        }

        // [normalize-space()] 或 [normalize-space(.)]
        if (e.matches(Regex("normalize-space\\(\\.?\\)\\s*"))) {
            return TextNormalizeSpacePredicate()
        }

        // 处理带引号的匹配
        // [@attr='val'] / [@attr="val"]
        val attrEqMatch = Regex("@([\\w-]+)\\s*=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (attrEqMatch != null) {
            return AttrEqualsPredicate(attrEqMatch.groupValues[1], attrEqMatch.groupValues[2])
        }

        // [@attr!='val'] / [@attr!="val"]
        val attrNeqMatch = Regex("@([\\w-]+)\\s*!=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (attrNeqMatch != null) {
            return AttrNotEqualsPredicate(attrNeqMatch.groupValues[1], attrNeqMatch.groupValues[2])
        }

        // [@attr] — 属性存在
        val attrExistsMatch = Regex("@([\\w-]+)").find(e)
        if (attrExistsMatch != null) {
            return AttrExistsPredicate(attrExistsMatch.groupValues[1])
        }

        // [text()='val'] / [.='val']
        val textEqMatch = Regex("(?:text\\(\\)|\\.)\\s*=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (textEqMatch != null) {
            return TextEqualsPredicate(textEqMatch.groupValues[1])
        }

        // [contains(text(),'val')]
        val textContainsMatch =
            Regex("contains\\(text\\(\\)\\s*,\\s*['\"]([^'\"]*)['\"]\\s*\\)").find(e)
        if (textContainsMatch != null) {
            return TextContainsPredicate(textContainsMatch.groupValues[1])
        }

        // [contains(@attr,'val')]
        val containsMatch =
            Regex("contains\\(\\s*@([\\w-]+)\\s*,\\s*['\"]([^'\"]*)['\"]\\s*\\)").find(e)
        if (containsMatch != null) {
            return AttrContainsPredicate(containsMatch.groupValues[1], containsMatch.groupValues[2])
        }

        // [starts-with(@attr,'val')]
        val startsWithMatch =
            Regex("starts-with\\(\\s*@([\\w-]+)\\s*,\\s*['\"]([^'\"]*)['\"]\\s*\\)").find(e)
        if (startsWithMatch != null) {
            return AttrStartsWithPredicate(startsWithMatch.groupValues[1], startsWithMatch.groupValues[2])
        }

        return null
    }

    // ─── 步骤求值 ──────────────────────────────────────────────

    private fun evaluateStep(nodes: List<Element>, step: XPathStep): List<Element> {
        val candidates = mutableListOf<Pair<Element, List<Element>>>()

        for (node in nodes) {
            when (step.axis) {
                Axis.CHILD -> {
                    val children = node.children()
                    val filtered = if (step.isWildcard) children
                    else children.filter { it.tagName().equals(step.tagName, ignoreCase = true) }
                    candidates.addAll(filtered.map { it to children })
                }
                Axis.DESCENDANT -> {
                    val all = if (step.isWildcard) {
                        node.allElements.filter { it !== node }
                    } else {
                        // 用 Jsoup select 按标签名过滤，避免遍历全部后代
                        val tag = step.tagName.lowercase()
                        node.select(tag)
                    }
                    for (el in all) {
                        val parent = el.parent()
                        val siblings = parent?.children() ?: listOf(el)
                        candidates.add(el to siblings)
                    }
                }
            }
        }

        if (step.predicates.isEmpty()) {
            return candidates.map { it.first }.distinct()
        }

        return candidates.filter { (el, siblings) ->
            val idx = siblings.indexOf(el)
            step.predicates.all { it.matches(el, idx, siblings) }
        }.map { it.first }.distinct()
    }

    // ─── 序列化辅助 ────────────────────────────────────────────

    fun elementToHtml(el: Element): String = el.outerHtml()
    fun elementToText(el: Element): String = el.text()
    fun textNodeToString(tn: TextNode): String = tn.text().trim()
}
