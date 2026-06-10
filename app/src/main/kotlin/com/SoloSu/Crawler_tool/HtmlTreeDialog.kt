package com.SoloSu.Crawler_tool

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * HTML 树形结构对话框（基于标准 DOM API）。
 *
 * 功能：
 * - 缩进表示层级
 * - 点击展开/折叠，带布局动画
 * - 双指缩放（缩放整体视图，非仅文字）
 * - 实时搜索过滤
 * - 点击展开/折叠，长按复制 XPath
 */
object HtmlTreeDialog {

    // ─── 数据模型 ──────────────────────────────────────────────────────────

    private data class TreeNode(
        val id: Int,
        val depth: Int,
        val xpath: String,
        val children: List<TreeNode>,
        val displayText: String,
        val textContent: String,
        val hasChildren: Boolean
    )

    // ─── 入口 ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show(
        context: Context,
        htmlContent: String,
        onSelectXPath: (xpath: String) -> Unit = {}
    ) {
        try {
            val doc = parseHtmlToDom(htmlContent) ?: run {
                Toast.makeText(context, "未能解析 HTML", Toast.LENGTH_SHORT).show()
                return
            }

            val rootNodes = buildTreeFromDom(doc)

            if (rootNodes.isEmpty()) {
                Toast.makeText(context, "未能解析出有效结构", Toast.LENGTH_SHORT).show()
                return
            }

            // ── 2. 交互状态 ──
            val expandedIds = mutableSetOf<Int>()
            val searchQuery = StringBuilder()
            val matchIds = mutableSetOf<Int>()
            val searchExpandIds = mutableSetOf<Int>()
            val minScale = 0.6f
            val maxScale = 2.5f

            // ── 3. 构建 UI 组件 ──

            // 树容器（带布局动画）
            val treeContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutTransition = LayoutTransition()
                layoutTransition.setDuration(200)
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            }

            // 缩放包装器（实际缩放整个视图）
            val zoomWrapper = createZoomWrapper(context, treeContainer, minScale, maxScale)

            // 滚动容器
            val scrollView = ScrollView(context).apply {
                addView(zoomWrapper)
                clipToPadding = false
                isFillViewport = true
            }

            // ── 4. 渲染函数 ──
            // 视图缓存：避免重复创建 View
            val viewCache = hashMapOf<Int, View>()
            val childContainerCache = hashMapOf<Int, LinearLayout>()

            /**
             * 构建节点行视图（缓存复用）。
             * 点击展开/折叠时只切换子容器可见性，不重建。
             */
            fun buildRow(node: TreeNode): View {
                return viewCache.getOrPut(node.id) {
                    var resultView: View? = null
                    resultView = createRowView(context, node, expandedIds.contains(node.id) || node.id in searchExpandIds, expandedIds, { nowExpanded ->
                        if (nowExpanded) expandedIds.add(node.id) else expandedIds.remove(node.id)
                        // 切换子容器可见性
                        val container = childContainerCache[node.id]
                        if (container != null) {
                            container.visibility = if (nowExpanded) View.VISIBLE else View.GONE
                        }
                        // 刷新箭头符号
                        val arrow = resultView?.findViewWithTag("arrow_${node.id}") as? TextView
                        if (arrow != null && node.hasChildren) {
                            arrow.text = if (nowExpanded) "▼" else "▶"
                        }
                    }, onSelectXPath)
                    resultView!!
                }
            }

            /**
             * 构建子节点容器（始终缓存，后续只切换可见性）。
             * 此容器始终被添加到父容器或 treeContainer（不依赖展开状态），
             * 展开/折叠只切换 View.VISIBLE / View.GONE。
             */
            fun buildChildrenContainer(node: TreeNode): LinearLayout? {
                if (!node.hasChildren) return null
                return childContainerCache.getOrPut(node.id) {
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        visibility = if (expandedIds.contains(node.id) || node.id in searchExpandIds) View.VISIBLE else View.GONE
                        for (child in node.children) {
                            addView(buildRow(child))
                            buildChildrenContainer(child)?.let { addView(it) }
                        }
                    }
                }
            }

            fun render() {
                treeContainer.removeAllViews()
                viewCache.clear()
                childContainerCache.clear()

                val query = searchQuery.toString().lowercase()
                // 搜索时计算匹配节点和祖先展开路径
                matchIds.clear()
                searchExpandIds.clear()

                if (query.isNotEmpty()) {
                    fun walkSearch(node: TreeNode): Boolean {
                        val matches = node.displayText.lowercase().contains(query) ||
                                node.textContent.lowercase().contains(query)
                        val childMatch = node.children.any { walkSearch(it) }
                        if (matches || childMatch) {
                            matchIds.add(node.id)
                            if (childMatch) searchExpandIds.add(node.id)
                            return true
                        }
                        return false
                    }
                    for (root in rootNodes) walkSearch(root)
                }

                // 改写 flattenAndAdd 闭包，捕获 matchIds/searchExpandIds
                fun flattenAndAddSearch(node: TreeNode) {
                    if (query.isNotEmpty() && node.id !in matchIds) return

                    val isMatch = query.isNotEmpty() && (
                        node.displayText.lowercase().contains(query) ||
                        node.textContent.lowercase().contains(query)
                    )

                    treeContainer.addView(buildRow(node))

                    // 搜索匹配行加高亮标记
                    if (isMatch) {
                        treeContainer.getChildAt(treeContainer.childCount - 1)?.let { row ->
                            row.setBackgroundColor(
                                ContextCompat.getColor(context, android.R.color.holo_blue_light)
                            )
                            row.tag = "match_${node.id}"
                        }
                    }

                    if (node.hasChildren) {
                        val container = buildChildrenContainer(node)
                        if (container != null) {
                            treeContainer.addView(container)
                        }
                    }
                }

                for (rootNode in rootNodes) {
                    flattenAndAddSearch(rootNode)
                }

                // 搜索时滚动到第一个匹配项
                if (query.isNotEmpty()) {
                    val firstMatchId = matchIds.firstOrNull() ?: return
                    treeContainer.post {
                        for (i in 0 until treeContainer.childCount) {
                            val child = treeContainer.getChildAt(i)
                            if (child.tag == "match_$firstMatchId") {
                                child.requestFocus()
                                val pos = IntArray(2)
                                child.getLocationInWindow(pos)
                                scrollView.smoothScrollTo(0, (pos[1] - 120).coerceAtLeast(0))
                                break
                            }
                        }
                    }
                }
            }

            // ── 5. 搜索栏 ──
            val searchInput = EditText(context).apply {
                hint = "搜索标签名 / 属性 / 文本内容..."
                textSize = 14f
                setPadding(16, 12, 16, 12)
                isSingleLine = true
                compoundDrawablePadding = 8
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    android.R.drawable.ic_menu_search, 0, 0, 0
                )
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        searchQuery.clear()
                        searchQuery.append(s?.toString() ?: "")
                        render()
                    }
                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {}
                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {}
                })
            }

            // ── 6. 底部缩放控制栏 ──
            val scaleBar = createScaleBar(context, minScale, maxScale) { factor ->
                zoomWrapper.scaleX = factor
                zoomWrapper.scaleY = factor
            }

            // ── 7. 总布局 ──
            val bodyLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(searchInput)
                addView(
                    scrollView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        480.dp(context)
                    )
                )
                addView(scaleBar)
            }

            // ── 8. 标题 ──
            val titleView = TextView(context).apply {
                text = "HTML 结构"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(24, 20, 24, 8)
            }

            // ── 9. 弹出对话框 ──
            MaterialAlertDialogBuilder(
                context,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
            )
                .setCustomTitle(titleView)
                .setView(bodyLayout)
                .setPositiveButton("关闭", null)
                .show()

            // ── 10. 首次渲染 ──
            render()

        } catch (e: Exception) {
            Toast.makeText(context, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── DOM 解析与树构建 ──────────────────────────────────────────────────

    /**
     * 将 HTML 字符串解析为 DOM Document。
     */
    private fun parseHtmlToDom(htmlContent: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(htmlContent)))
        } catch (e: Exception) {
            try {
                val wellFormed = htmlToWellFormedXml(htmlContent)
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = false
                val builder = factory.newDocumentBuilder()
                builder.parse(InputSource(StringReader(wellFormed)))
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * 将非格式良好的 HTML 转为格式良好的 XML。
     */
    private fun htmlToWellFormedXml(html: String): String {
        var s = html
        if (!s.trimStart().startsWith("<")) {
            s = "<root>$s</root>"
        }
        s = s.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        val voidTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )
        s = s.replace(Regex("<(${voidTags.joinToString("|")})([^>]*)>", RegexOption.IGNORE_CASE)) {
            val tag = it.groupValues[1].lowercase()
            val attrs = it.groupValues[2]
            if (attrs.trimEnd().endsWith("/")) "<$tag$attrs>" else "<$tag$attrs />"
        }
        s = s.replace(Regex("""<[^>]+>""")) { match ->
            val fullTag = match.value
            fullTag.replace(Regex("""\s+([a-zA-Z_][-a-zA-Z0-9_:]*)(?=\s|/?>)""")) { attrMatch ->
                val before = fullTag.substring(0, attrMatch.range.first)
                if (before.count { it == '"' } % 2 == 0) {
                    " ${attrMatch.groupValues[1]}=\"${attrMatch.groupValues[1]}\""
                } else {
                    attrMatch.value
                }
            }
        }
        s = s.replace(Regex("&(?!amp;|lt;|gt;|quot;|apos;|#[0-9]+;|#x[0-9a-fA-F]+;)", RegexOption.IGNORE_CASE), "&")
        return s
    }

    /**
     * 从 DOM Document 构建 TreeNode 树。
     */
    private fun buildTreeFromDom(doc: Document): List<TreeNode> {
        var nextId = 0
        val body = doc.documentElement

        fun buildTree(el: Element, depth: Int): TreeNode {
            val id = nextId++
            val xpath = buildXPathSimple(el)
            val display = buildDisplayText(el, depth)
            val text = el.ownTextTrim()
            val kids = getChildElements(el).map { buildTree(it, depth + 1) }
            return TreeNode(id, depth, xpath, kids, display, text, kids.isNotEmpty())
        }

        return getChildElements(body).map { buildTree(it, 0) }
    }

    /**
     * 获取 Element 的直接子 Element。
     */
    private fun getChildElements(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        val children: NodeList = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    private fun Element.tagNameLower(): String = tagName.lowercase()

    private fun Element.hasAttr(name: String): Boolean = hasAttribute(name)

    private fun Element.attr(name: String): String = getAttribute(name)

    private fun Element.classes(): String = getAttribute("class").trim()

    private fun Element.ownTextTrim(): String {
        val sb = StringBuilder()
        val children: NodeList = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                sb.append(node.nodeValue ?: "")
            }
        }
        return sb.toString().trim()
    }

    private fun Element.parentElement(): Element? {
        val p = parentNode
        return if (p is Element) p else null
    }

    private fun Element.siblingIndexForTag(): Int {
        val parent = parentElement() ?: return 0
        val tag = tagNameLower()
        val siblings = getChildElements(parent).filter { it.tagNameLower() == tag }
        return siblings.indexOf(this)
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────

    /**
     * 构建节点显示文本
     */
    private fun buildDisplayText(element: Element, depth: Int): String {
        val tag = element.tagNameLower()
        val idStr = if (element.hasAttr("id")) "#${element.attr("id")}" else ""
        val clsStr = element.classes()
        val classStr = if (clsStr.isNotEmpty()) ".$clsStr" else ""

        val sb = StringBuilder(tag)
        sb.append(idStr).append(classStr)

        // 显示关键属性（最多 2 个）
        var added = 0
        for (attr in listOf("href", "src", "title", "alt", "data-src", "data-original")) {
            if (element.hasAttr(attr)) {
                val v = element.attr(attr)
                sb.append(" $attr=\"${if (v.length <= 28) v else "${v.take(25)}..."}\"")
                if (++added >= 2) break
            }
        }

        // 显示文本内容片段
        val text = element.ownTextTrim().take(24)
        if (text.isNotEmpty()) {
            val escaped = text.replace("\n", " ").replace(Regex("\\s+"), " ")
            sb.append(" \u00AB$escaped\u00BB")
        }

        return "<$sb>"
    }

    /**
     * 从 element 向上构建简洁 XPath
     *
     * 生成规则（优先级递减）：
     * 1. 有 id → `[@id='xxx']`
     * 2. 有 class → `[@class='a b']`
     * 3. 有独特文本 → `[text()='xxx']`
     * 4. 同标签兄弟 → `[n]` 精确索引
     * 5. 有 data-* 属性 → `[@data-xxx='val']`
     */
    private fun buildXPathSimple(element: Element): String {
        val parts = mutableListOf<String>()
        var cur: Element? = element
        while (cur != null && cur.tagNameLower() !in listOf("body", "html")) {
            val tag = cur!!.tagNameLower()
            val preds = mutableListOf<String>()

            // 1. id 属性（唯一标识，优先级最高）
            if (cur!!.hasAttr("id")) {
                preds.add("@id='${cur!!.attr("id")}'")
            }

            // 2. class 属性
            val classStr = cur!!.classes()
            if (classStr.isNotEmpty()) {
                preds.add("@class='$classStr'")
            }

            // 3. 独特文本内容（非空且建议长度 >3，避免过短文本误匹配）
            val ownText = cur!!.ownTextTrim()
            if (preds.isEmpty() && ownText.length > 3) {
                // 过滤文本中的单引号避免破坏 XPath 语法
                val safeText = ownText.filter { it != '\'' }
                preds.add("text()='$safeText'")
            }

            // 4. data-* 属性（当没有 id/class/文本时，作为备用标识）
            if (preds.isEmpty()) {
                val attrs = cur!!.getAttributes()
                var dataKey: String? = null
                var dataVal: String? = null
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i)
                    val name = attr.nodeName ?: ""
                    val value = attr.nodeValue ?: ""
                    if (name.startsWith("data-") && value.isNotBlank()) {
                        dataKey = name
                        dataVal = value
                        break
                    }
                }
                if (dataKey != null && dataVal != null) {
                    preds.add("@$dataKey='$dataVal'")
                }
            }

            // 5. 同标签兄弟 → 精确索引 [n]
            val parent = cur!!.parentElement()
            if (parent != null) {
                val same = getChildElements(parent).filter { it.tagNameLower() == tag }
                if (same.size > 1) {
                    val idx = same.indexOfFirst { it == cur } + 1  // 1-based
                    preds.add("$idx")
                }
            }

            val step = if (preds.isEmpty()) tag else "$tag[${preds.joinToString(" and ")}]"
            parts.add(0, step)
            cur = cur!!.parentElement()
        }
        return "//" + parts.joinToString("/")
    }

    /**
     * 创建单行节点视图
     */
    private fun createRowView(
        context: Context,
        node: TreeNode,
        isExpanded: Boolean,
        expandedIds: Set<Int>,
        onToggle: (Boolean) -> Unit,
        onSelectXPath: (String) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val indentPx = (node.depth * 28 * density).toInt()

        // 箭头 / 圆点
        val arrowView = TextView(context).apply {
            text = when {
                node.hasChildren && isExpanded -> "\u25BC"  // ▼
                node.hasChildren -> "\u25B6"               // ▶
                else -> "\u00B7"                            // ·
            }
            tag = "arrow_${node.id}"
            textSize = 10f
            setTextColor(
                if (node.hasChildren)
                    ContextCompat.getColor(context, R.color.md_primary)
                else
                    ContextCompat.getColor(context, R.color.md_outline)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = (6 * density).toInt()
            }
        }

        // 标签语法高亮
        val tagView = TextView(context).apply {
            text = HtmlUtil.highlightTagHtml(context, node.displayText)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // 行布局
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(indentPx, 6, 12, 6)
            setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.transparent)
            )

            addView(arrowView)
            addView(tagView)

            // 点击展开/折叠（使用实时 expandedIds 判断当前状态）
            setOnClickListener {
                if (node.hasChildren) {
                    onToggle(!expandedIds.contains(node.id))
                }
            }

            // 长按复制 XPath
            setOnLongClickListener {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("XPath", node.xpath)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制 XPath: ${node.xpath}", Toast.LENGTH_SHORT)
                    .show()
                onSelectXPath(node.xpath)
                true
            }
        }

        // 交替行背景
        return rowLayout
    }

    // ─── 缩放相关 ──────────────────────────────────────────────────────────

    private fun createZoomWrapper(
        context: Context,
        child: View,
        minScale: Float,
        maxScale: Float
    ): FrameLayout {
        var currentScale = 1.0f

        return object : FrameLayout(context) {
            private val detector = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        currentScale =
                            (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                        scaleX = currentScale
                        scaleY = currentScale
                        return true
                    }
                }
            )

            override fun onTouchEvent(event: MotionEvent): Boolean {
                detector.onTouchEvent(event)
                return super.onTouchEvent(event)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                detector.onTouchEvent(ev)
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
            addView(child)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * 创建底部缩放控制栏
     */
    private fun createScaleBar(
        context: Context,
        minScale: Float,
        maxScale: Float,
        onScaleChanged: (Float) -> Unit
    ): View {
        var currentScale = 1.0f
        val step = 0.25f

        val resetBtn = MaterialButton(context).apply {
            text = "1x"
            textSize = 12f
            strokeWidth = 1
            setPadding(8, 2, 8, 2)
            setOnClickListener {
                currentScale = 1.0f
                onScaleChanged(currentScale)
            }
        }

        val zoomInBtn = MaterialButton(context).apply {
            text = "\u002B"
            textSize = 16f
            setOnClickListener {
                currentScale = (currentScale + step).coerceAtMost(maxScale)
                onScaleChanged(currentScale)
            }
        }

        val zoomOutBtn = MaterialButton(context).apply {
            text = "\u2212"
            textSize = 16f
            setOnClickListener {
                currentScale = (currentScale - step).coerceAtLeast(minScale)
                onScaleChanged(currentScale)
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
            addView(zoomOutBtn)
            addView(resetBtn.apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 16; marginEnd = 16 }
            })
            addView(zoomInBtn)
        }
    }

    // ─── 扩展辅助 ──────────────────────────────────────────────────────────

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
