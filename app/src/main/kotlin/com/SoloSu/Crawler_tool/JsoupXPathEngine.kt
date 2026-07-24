package com.SoloSu.Crawler_tool

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

/**
 * 基于 Jsoup DOM 树的 XPath 求值引擎。
 *
 * 支持的 XPath 语法：
 * - 绝对路径 /html/body/div（从文档根开始）
 * - 相对路径 //div（descendant-or-self::，包含当前节点自身）
 * - 属性访问 //a/@href, //div/text(), //div/normalize-space()
 * - 轴 child::, descendant::, descendant-or-self::, parent::,
 *       ancestor::, following-sibling::, preceding-sibling::
 * - 谓词 [@class='foo'], [@attr], [n], [last()], [position()<n],
 *       [contains(@class,'foo')], [starts-with(@attr,'val')],
 *       [normalize-space()], [normalize-space()='val'],
 *       [normalize-space(@attr)], [normalize-space(@attr)='val'],
 *       [text()], [not(@attr)], [not(@attr='val')],
 *       [last()>1], [position()=last()]
 * - 复合谓词 [@class='a' and @id='b'], [@class='a' or @class='b']
 * - 通配符 *, node(), 父节点 .., 当前节点 .
 * - 显式命名空间跳过（prefix:local → local）
 * - 单引号/双引号均可
 */
object JsoupXPathEngine {
    // ─── 表达式缓存 ────────────────────────────────────────────
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
        val expr = expression.trim()
        if (expr.isEmpty()) return emptyList()

        // 绝对路径：从文档根开始
        val effectiveContext = if (expr.startsWith("/")) {
            val doc = when (context) {
                is Document -> context
                else -> context.ownerDocument()
            }
            doc?.children()?.firstOrNull() ?: context
        } else {
            context
        }

        @Synchronized
        fun getSteps(e: String): List<XPathStep> =
            stepCache.getOrPut(e) { parseSteps(e) }
        val steps = getSteps(expr)
        if (steps.isEmpty()) {
            throw RuntimeException("无法解析XPath表达式: $expr")
        }

        var nodes = listOf(effectiveContext)

        for (step in steps) {
            when {
                step.isAttribute -> {
                    val attrName = step.nodeTest.removePrefix("@")
                    val result = if (attrName == "*") {
                        nodes.flatMap { el ->
                            el.attributes().asList().map { "${it.key}=${it.value}" }
                        }
                    } else {
                        nodes.map { el -> el.attr(attrName) }
                    }
                    // 如果 @ 不是最后一步，抛出错误（属性节点不能再有子轴）
                    if (steps.last() !== step) {
                        throw RuntimeException("属性访问 @$attrName 必须是 XPath 最后一步")
                    }
                    return result
                }
                step.isText -> {
                    return nodes.flatMap { el ->
                        when (step.nodeTest) {
                            "text()" -> el.textNodes().map { it.text().trim() }.filter { it.isNotEmpty() }
                            "normalize-space()" -> listOf(el.text().replace(Regex("\\s+"), " ").trim())
                            else -> emptyList()
                        }
                    }
                }
                step.isParent -> {
                    nodes = nodes.mapNotNull { it.parent() }
                }
                step.isSelf -> { /* 保持不变 */ }
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
        val isText: Boolean get() = nodeTest == "text()" || nodeTest == "normalize-space()"
        val isParent: Boolean get() = nodeTest == ".."
        val isSelf: Boolean get() = nodeTest == "."
        val isWildcard: Boolean get() = nodeTest == "*" || nodeTest == "node()"
        val tagName: String get() = if (nodeTest == "node()") "*" else nodeTest
    }

    private enum class Axis {
        CHILD,
        DESCENDANT,
        DESCENDANT_OR_SELF,
        PARENT,
        ANCESTOR,
        FOLLOWING_SIBLING,
        PRECEDING_SIBLING
    }

    // ─── 谓词表达式 ────────────────────────────────────────────

    private sealed class PredicateExpr {
        abstract fun matches(el: Element, index: Int, siblings: List<Element>): Boolean
    }

    private data class IndexPredicate(val index: Int) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) = i == index
    }

    private class PositionAnyPredicate : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) = true
    }

    /** [last()] — 最后一个匹配节点 */
    private class LastPredicate : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            i == siblings.size - 1
    }

    /** [last() op n] — 与上下文大小比较 */
    private class LastComparisonPredicate(val op: String, val value: Int) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>): Boolean {
            val size = siblings.size
            return when (op) {
                "="  -> size == value
                "!=" -> size != value
                "<"  -> size < value
                ">"  -> size > value
                "<=" -> size <= value
                ">=" -> size >= value
                else -> false
            }
        }
    }

    /** [position()=last()] — 当 position=last 时匹配（等价 [last()]） */
    private class PositionEqualsLastPredicate : PredicateExpr() {
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

    /** [normalize-space(@attr)] — 归一化后非空 */
    private data class AttrNormalizeSpaceExistsPredicate(val attr: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr).replace(Regex("\\s+"), " ").trim().isNotEmpty()
    }

    /** [normalize-space(@attr)='val'] */
    private data class AttrNormalizeSpaceEqualsPredicate(val attr: String, val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.attr(attr).replace(Regex("\\s+"), " ").trim() == value
    }

    /** [normalize-space()='val'] */
    private data class TextNormalizeSpaceEqualsPredicate(val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.text().replace(Regex("\\s+"), " ").trim() == value
    }

    private data class TextEqualsPredicate(val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.text().trim() == value
    }

    private data class TextContainsPredicate(val value: String) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.text().contains(value)
    }

    /** [normalize-space()] / [text()] — 全文本非空 */
    private class TextNormalizeSpacePredicate : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            el.text().trim().isNotEmpty()
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

    private class NotPredicate(val inner: PredicateExpr) : PredicateExpr() {
        override fun matches(el: Element, i: Int, siblings: List<Element>) =
            !inner.matches(el, i, siblings)
    }

    // ─── 步骤解析 ──────────────────────────────────────────────

    /**
     * 将 XPath 表达式解析为步骤列表。
     *
     * - // 生成 DESCENDANT_OR_SELF，包含当前节点自身
     * - /html/body 绝对路径由外层处理（从文档根开始）
     */
    // ===== 修改开始：替换整个 parseSteps 函数 =====
private fun parseSteps(expression: String): List<XPathStep> {
    val expr = expression.trim()
    if (expr.isEmpty()) return emptyList()

    val steps = mutableListOf<XPathStep>()
    var pos = 0

    // 处理开头的 / 或 //
    if (expr[pos] == '/') {
        pos++
        if (pos < expr.length && expr[pos] == '/') {
            // 开头的 // → descendant-or-self::node()
            steps.add(XPathStep(Axis.DESCENDANT_OR_SELF, "node()"))
            pos++
        }
    }

    while (pos < expr.length) {
        // 处理步骤间的 /
        if (expr[pos] == '/') {
            pos++
            if (pos < expr.length && expr[pos] == '/') {
                // 中间的 // → descendant-or-self::node()
                steps.add(XPathStep(Axis.DESCENDANT_OR_SELF, "node()"))
                pos++
                continue
            }
            // 单个 / 意味着 CHILD 轴，无需插入额外步骤
            if (pos >= expr.length) break
        }

        // 检查显式轴前缀
        val remaining = expr.substring(pos)
        val axisMatch = Regex(
            "^(child|descendant|descendant-or-self|parent|ancestor|following-sibling|preceding-sibling)\\s*::"
        ).find(remaining)

        val axis = if (axisMatch != null) {
            pos += axisMatch.value.length
            parseAxisName(axisMatch.groupValues[1])
        } else {
            Axis.CHILD
        }

        // 解析 nodeTest + predicates
        val parsed = parseNodeTestAndPredicates(expr.substring(pos))
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
// ===== 修改结束 =====

    private fun parseAxisName(name: String): Axis {
        return when (name) {
            "child" -> Axis.CHILD
            "descendant" -> Axis.DESCENDANT
            "descendant-or-self" -> Axis.DESCENDANT_OR_SELF
            "parent" -> Axis.PARENT
            "ancestor" -> Axis.ANCESTOR
            "following-sibling" -> Axis.FOLLOWING_SIBLING
            "preceding-sibling" -> Axis.PRECEDING_SIBLING
            else -> Axis.CHILD
        }
    }

    private data class StepParseResult(
        val nodeTest: String,
        val predicates: List<PredicateExpr>,
        val consumedLength: Int
    )

    private fun parseNodeTestAndPredicates(input: String): StepParseResult {
        var pos = 0
        val len = input.length

        val nodeTest: String = when {
            // @attr
            input[pos] == '@' -> {
                val start = pos
                pos++
                while (pos < len && input[pos] !in charArrayOf('[', '/', ':')) pos++
                input.substring(start, pos)
            }
            // text() | normalize-space()
            input.substring(pos).startsWith("text()") -> {
                pos += 6; "text()"
            }
            input.substring(pos).startsWith("normalize-space()") -> {
                pos += 17; "normalize-space()"
            }
            // node() — 通配符匹配任意元素
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
                // 跳过 namespace 前缀（prefix:local → local）
                if (pos < len && input[pos] == ':') {
                    pos++
                    val tagStart = pos
                    while (pos < len && input[pos] !in charArrayOf('[', '/')) pos++
                    tag = input.substring(tagStart, pos)
                }
                tag
            }
            else -> {
                val rest = input.substring(pos)
                pos = len
                rest
            }
        }

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

    /** 将引号内文本替换为占位符 */
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

    /**
     * 解析谓词内部（含 and/or），返回谓词列表。
     */
    private fun parsePredicateInner(inner: String): List<PredicateExpr> {
        val trimmed = inner.trim()

        // not(...) 包裹复合 → 内部走 parsePredicateExpr 含 and/or
        val notMatch = Regex("not\\s*\\((.+)\\)\\s*$", RegexOption.DOT_MATCHES_ALL).find(trimmed)
        if (notMatch != null) {
            val body = notMatch.groupValues[1].trim()
            val innerPred = parsePredicateExpr(body)
            if (innerPred != null) return listOf(NotPredicate(innerPred))
        }

        val single = parsePredicateExpr(trimmed)
        return if (single != null) listOf(single) else emptyList()
    }

    /**
     * 解析完整谓词条件（含 and/or 复合），返回单个 PredicateExpr。
     */
    private fun parsePredicateExpr(input: String): PredicateExpr? {
        val trimmed = input.trim()
        val (masked, quoted) = extractQuotedSegments(trimmed)

        fun restore(s: String): String {
            var result = s
            for (i in quoted.indices) {
                result = result.replaceFirst("\${Q${i + 1}}", "'${quoted[i]}'")
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
            return result
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
            return result
        }

        return parseSinglePredicate(trimmed)
    }

    /**
     * 按逻辑运算符分割。不要求操作符两侧有空格，
     * 只确保前后不是字母/数字/下划线（避免匹配单词内嵌）。
     */
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
                // 前后都不是单词字符 → 安全的 and/or 分割
                if (!before.isLetterOrDigit() && before != '_' &&
                    !after.isLetterOrDigit() && after != '_') {
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

    /** 解析单个原子谓词条件 */
    private fun parseSinglePredicate(expr: String): PredicateExpr? {
        var e = expr.trim()
        if (e.isEmpty()) return null

        // ── last() 比较运算 ──
        // [last() op n]
        val lastCmpMatch = Regex("last\\s*\\(\\s*\\)\\s*([=!<>]=?)\\s*(\\d+)").find(e)
        if (lastCmpMatch != null) {
            return LastComparisonPredicate(lastCmpMatch.groupValues[1], lastCmpMatch.groupValues[2].toInt())
        }

        // [position()=last()]
        if (Regex("position\\s*\\(\\s*\\)\\s*=\\s*last\\s*\\(\\s*\\)").matches(e)) {
            return PositionEqualsLastPredicate()
        }

        // [position()=last() op n]? — No, position()=last() comparison

        // ── normalize-space 带参数 ──
        // [normalize-space(@attr)='val']
        val nsAttrEqMatch = Regex(
            "normalize-space\\s*\\(\\s*@([\\w-]+)\\s*\\)\\s*=\\s*['\"]([^'\"]*)['\"]"
        ).find(e)
        if (nsAttrEqMatch != null) {
            return AttrNormalizeSpaceEqualsPredicate(nsAttrEqMatch.groupValues[1], nsAttrEqMatch.groupValues[2])
        }

        // [normalize-space(@attr)]
        val nsAttrExistsMatch = Regex("normalize-space\\s*\\(\\s*@([\\w-]+)\\s*\\)").find(e)
        if (nsAttrExistsMatch != null) {
            return AttrNormalizeSpaceExistsPredicate(nsAttrExistsMatch.groupValues[1])
        }

        // [normalize-space()='val']
        val nsEqMatch = Regex(
            "normalize-space\\s*\\(\\s*\\)\\s*=\\s*['\"]([^'\"]*)['\"]"
        ).find(e)
        if (nsEqMatch != null) {
            return TextNormalizeSpaceEqualsPredicate(nsEqMatch.groupValues[1])
        }

        // ── not() 包单条件 ──
        val notMatch = Regex("not\\s*\\((.+)\\)\\s*$", RegexOption.DOT_MATCHES_ALL).find(e)
        if (notMatch != null) {
            val inner = notMatch.groupValues[1].trim()
            if (hasLogicalOp(inner)) return null
            val innerPred = parseSinglePredicate(inner)
            if (innerPred != null) return NotPredicate(innerPred)
        }

        // [n]
        e.toIntOrNull()?.let { n -> return IndexPredicate(n - 1) }

        // [last()]
        if (e == "last()" || e.matches(Regex("last\\(\\)\\s*"))) return LastPredicate()

        // [position()]
        if (e == "position()" || e.matches(Regex("position\\s*\\(\\s*\\)\\s*"))) {
            return PositionAnyPredicate()
        }

        // [position() op value]
        val posMatch = Regex("position\\s*\\(\\s*\\)\\s*([=!<>]=?)\\s*(\\d+)").find(e)
        if (posMatch != null) {
            return PositionPredicate(posMatch.groupValues[1], posMatch.groupValues[2].toInt())
        }

        // [normalize-space()]
        if (e.matches(Regex("normalize-space\\(\\.?\\)\\s*"))) {
            return TextNormalizeSpacePredicate()
        }

        // [@attr='val'] / [@attr="val"]
        val attrEqMatch = Regex("@([\\w-]+)\\s*=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (attrEqMatch != null) {
            return AttrEqualsPredicate(attrEqMatch.groupValues[1], attrEqMatch.groupValues[2])
        }

        // [@attr!='val']
        val attrNeqMatch = Regex("@([\\w-]+)\\s*!=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (attrNeqMatch != null) {
            return AttrNotEqualsPredicate(attrNeqMatch.groupValues[1], attrNeqMatch.groupValues[2])
        }

        // [@attr]
        val attrExistsMatch = Regex("@([\\w-]+)").find(e)
        if (attrExistsMatch != null) {
            return AttrExistsPredicate(attrExistsMatch.groupValues[1])
        }

        // [text()='val'] / [.='val']
        val textEqMatch = Regex("(?:text\\(\\)|\\.)\\s*=\\s*['\"]([^'\"]*)['\"]").find(e)
        if (textEqMatch != null) {
            return TextEqualsPredicate(textEqMatch.groupValues[1])
        }

        // [text()]
        if (e == "text()" || e.matches(Regex("text\\(\\)\\s*"))) {
            return TextNormalizeSpacePredicate()
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

    private fun hasLogicalOp(input: String): Boolean {
        val (masked, _) = extractQuotedSegments(input)
        return splitByLogicalOp(masked, "and").size > 1 ||
               splitByLogicalOp(masked, "or").size > 1
    }

    // ─── 步骤求值 ──────────────────────────────────────────────

    /**
     * 对当前节点列表执行一步 XPath 求值。
     *
     * 匹配元素按父节点分组，谓词中的 last() / [n] 以同父同类节点为上下文，
     * 符合标准 XPath 语义。
     */
    private fun evaluateStep(nodes: List<Element>, step: XPathStep): List<Element> {
        val byParent = mutableMapOf<Element?, MutableList<Element>>()

        for (node in nodes) {
            when (step.axis) {
                Axis.CHILD -> {
                    val children = node.children()
                    val filtered = if (step.isWildcard) children
                    else children.filter { it.tagName().equals(step.tagName, ignoreCase = true) }
                    if (filtered.isNotEmpty()) {
                        val list = byParent.getOrPut(node) { mutableListOf() }
                        list.addAll(filtered)
                    }
                }

                Axis.DESCENDANT -> {
                    val all = if (step.isWildcard) {
                        node.allElements.filter { it !== node }
                    } else {
                        node.select(step.tagName.lowercase())
                    }
                    for (el in all) {
                        val parent = el.parent()
                        byParent.getOrPut(parent) { mutableListOf() }.add(el)
                    }
                }

                Axis.DESCENDANT_OR_SELF -> {
                    // 1. 检查当前节点自身
                    if (step.isWildcard || node.tagName().equals(step.tagName, ignoreCase = true)) {
                        val doc = node.ownerDocument() ?: node
                        byParent.getOrPut(doc) { mutableListOf() }.add(node)
                    }
                    // 2. 后代节点
                    val descendants = if (step.isWildcard) {
                        node.allElements.filter { it !== node }
                    } else {
                        node.select(step.tagName.lowercase())
                    }
                    for (el in descendants) {
                        val parent = el.parent()
                        byParent.getOrPut(parent) { mutableListOf() }.add(el)
                    }
                }

                Axis.PARENT -> {
                    val p = node.parent()
                    if (p != null) {
                        if (step.isWildcard || p.tagName().equals(step.tagName, ignoreCase = true)) {
                            byParent.getOrPut(p.parent() ?: p) { mutableListOf() }.add(p)
                        }
                    }
                }

                Axis.ANCESTOR -> {
                    var cur: Element? = node.parent()
                    while (cur != null) {
                        if (step.isWildcard || cur.tagName().equals(step.tagName, ignoreCase = true)) {
                            val parent = cur.parent()
                            byParent.getOrPut(parent ?: cur) { mutableListOf() }.add(cur)
                        }
                        cur = cur.parent()
                    }
                }

                Axis.FOLLOWING_SIBLING -> {
                    val parent = node.parent()
                    if (parent != null) {
                        val siblings = parent.children()
                        val start = siblings.indexOf(node) + 1
                        for (i in start until siblings.size) {
                            val el = siblings[i]
                            if (step.isWildcard || el.tagName().equals(step.tagName, ignoreCase = true)) {
                                byParent.getOrPut(parent) { mutableListOf() }.add(el)
                            }
                        }
                    }
                }

                Axis.PRECEDING_SIBLING -> {
                    val parent = node.parent()
                    if (parent != null) {
                        val siblings = parent.children()
                        val end = siblings.indexOf(node)
                        for (i in 0 until end) {
                            val el = siblings[i]
                            if (step.isWildcard || el.tagName().equals(step.tagName, ignoreCase = true)) {
                                byParent.getOrPut(parent) { mutableListOf() }.add(el)
                            }
                        }
                    }
                }
            }
        }

        if (step.predicates.isEmpty()) {
            return byParent.values.flatten().distinct()
        }

        val result = mutableListOf<Element>()
        for ((_, siblings) in byParent) {
            for ((idx, el) in siblings.withIndex()) {
                if (step.predicates.all { it.matches(el, idx, siblings) }) {
                    result.add(el)
                }
            }
        }
        return result.distinct()
    }

    // ─── 序列化辅助 ────────────────────────────────────────────

    fun elementToHtml(el: Element): String = el.outerHtml()
    fun elementToText(el: Element): String = el.text()
    fun textNodeToString(tn: TextNode): String = tn.text().trim()
}
