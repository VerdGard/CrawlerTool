package com.SoloSu.Crawler_tool

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import com.google.android.material.slider.Slider
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.graphics.drawable.GradientDrawable
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText

/**
 * HTML 树形结构对话框 — 增强交互版
 *
 * 交互特性：
 * - 整行点击展开/折叠（不限于三角符号）
 * - 层级缩进指示线
 * - 底部工具栏：展开全部、折叠全部、展开到指定层
 * - 节点右侧显示子节点数量
 * - 双击复制 XPath
 * - 标签颜色区分（div=蓝, a=绿, img=橙, 其他=灰）
 * - 有属性标记的节点显示小圆点
 */
object HtmlTreeDialog {

    private data class TreeNode(
        val id: Int,
        val depth: Int,
        val xpath: String,
        val children: List<TreeNode>,
        val displayText: String,
        val textContent: String,
        val hasChildren: Boolean,
        val tagName: String,
        val hasImportantAttr: Boolean  // 是否有 id/class/data-*
    )

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show(
        context: Context,
        htmlContent: String,
        onSelectXPath: (xpath: String) -> Unit = {}
    ) {
        try {
            val doc = Jsoup.parse(htmlContent)
            val rootNodes = buildTreeFromJsoup(doc)

            if (rootNodes.isEmpty()) {
                Toast.makeText(context, "未能解析出有效结构", Toast.LENGTH_SHORT).show()
                return
            }

            // 交互状态
            val expandedIds = mutableSetOf<Int>()
            val searchQuery = StringBuilder()
            val matchIds = mutableSetOf<Int>()
            val searchExpandIds = mutableSetOf<Int>()
            val minScale = 0.6f
            val maxScale = 2.5f

            // UI 组件
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

            val zoomWrapper = createZoomWrapper(context, treeContainer, minScale, maxScale)

            val scrollView = ScrollView(context).apply {
                addView(zoomWrapper)
                clipToPadding = false
                isFillViewport = true
                setPadding(4, 8, 4, 8)
            }

            // 缓存
            val viewCache = hashMapOf<Int, View>()
            val childContainerCache = hashMapOf<Int, LinearLayout>()

            // ===== 核心：构建节点行（增强版） =====
            fun buildRow(node: TreeNode): View {
                return viewCache.getOrPut(node.id) {
                    val density = context.resources.displayMetrics.density
                    val indentPx = (node.depth * 24 * density).toInt()

                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(indentPx, 8, 12, 8)
                        minimumHeight = (36 * density).toInt()
                        // 交替背景
                        setBackgroundColor(
                            if (node.depth % 2 == 0)
                                ContextCompat.getColor(context, android.R.color.transparent)
                            else
                                Color.parseColor("#0A000000")
                        )
                        // 整行点击
                        isClickable = true
                        isFocusable = true
                        foreground = context.theme.obtainStyledAttributes(
                            intArrayOf(android.R.attr.selectableItemBackground)
                        ).getDrawable(0)
                    }

                    // ---- 缩进指示线（视觉引导） ----
                    if (node.depth > 0) {
                        val indicator = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                (16 * density).toInt(),
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(Color.parseColor("#30B0B0B0"))
                        }
                        rowLayout.addView(indicator)
                    }

                    // ---- 有属性标记：小圆点 ----
if (node.hasImportantAttr) {
    val dot = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            (8 * density).toInt(),
            (8 * density).toInt()
        ).apply {
            marginEnd = (6 * density).toInt()
        }
        // 用 GradientDrawable 设置圆形背景
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.md_primary))
        }
        background = drawable
    }
    rowLayout.addView(dot)
}

                    // ---- 三角箭头（点击区域已由整行接管） ----
                    val arrowView = TextView(context).apply {
                        text = when {
                            node.hasChildren -> if (expandedIds.contains(node.id)) "▼" else "▶"
                            else -> "·"
                        }
                        tag = "arrow_${node.id}"
                        textSize = 12f
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
                    rowLayout.addView(arrowView)

                    // ---- 标签名（颜色区分） ----
                    val tagNameView = TextView(context).apply {
                        text = node.tagName
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(getTagColor(context, node.tagName))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (4 * density).toInt()
                        }
                    }
                    rowLayout.addView(tagNameView)

                    // ---- 属性/文本内容（灰色） ----
                    val detailView = TextView(context).apply {
                        text = node.displayText
                        textSize = 11f
                        typeface = Typeface.MONOSPACE
                        setTextColor(ContextCompat.getColor(context, R.color.md_on_surface_variant))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginEnd = (8 * density).toInt()
                        }
                    }
                    rowLayout.addView(detailView)

                    // ---- 子节点数量徽标 ----
                    if (node.hasChildren) {
                        val countView = TextView(context).apply {
                            text = "${node.children.size}"
                            textSize = 10f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            setPadding(
                                (6 * density).toInt(),
                                (2 * density).toInt(),
                                (6 * density).toInt(),
                                (2 * density).toInt()
                            )
                            background = ContextCompat.getDrawable(context, R.drawable.expand_btn_bg)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        rowLayout.addView(countView)
                    }

                    // ---- 整行点击展开/折叠 ----
                    rowLayout.setOnClickListener {
                        if (node.hasChildren) {
                            val nowExpanded = !expandedIds.contains(node.id)
                            if (nowExpanded) expandedIds.add(node.id) else expandedIds.remove(node.id)
                            // 切换子容器可见性
                            val container = childContainerCache[node.id]
                            if (container != null) {
                                container.visibility = if (nowExpanded) View.VISIBLE else View.GONE
                            }
                            // 刷新箭头
                            arrowView.text = if (nowExpanded) "▼" else "▶"
                            // 更新计数徽标背景色
                            if (nowExpanded) {
                                rowLayout.setBackgroundColor(
                                    Color.parseColor("#15" + Integer.toHexString(
                                        ContextCompat.getColor(context, R.color.md_primary)
                                    ).substring(2))
                                )
                            } else {
                                rowLayout.setBackgroundColor(
                                    if (node.depth % 2 == 0)
                                        ContextCompat.getColor(context, android.R.color.transparent)
                                    else
                                        Color.parseColor("#0A000000")
                                )
                            }
                        }
                    }

                    // ---- 双击复制 XPath ----
                    var lastClickTime = 0L
                    rowLayout.setOnLongClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("XPath", node.xpath)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制 XPath: ${node.xpath}", Toast.LENGTH_SHORT).show()
                        onSelectXPath(node.xpath)
                        true
                    }

                    rowLayout
                }
            }

            fun buildChildrenContainer(node: TreeNode): LinearLayout? {
                if (!node.hasChildren) return null
                return childContainerCache.getOrPut(node.id) {
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        visibility = if (expandedIds.contains(node.id) || node.id in searchExpandIds)
                            View.VISIBLE else View.GONE
                        for (child in node.children) {
                            addView(buildRow(child))
                            buildChildrenContainer(child)?.let { addView(it) }
                        }
                    }
                }
            }

            // ===== 渲染函数 =====
            fun render() {
                treeContainer.removeAllViews()
                viewCache.clear()
                childContainerCache.clear()

                val query = searchQuery.toString().lowercase()
                matchIds.clear()
                searchExpandIds.clear()

                if (query.isNotEmpty()) {
                    fun walkSearch(node: TreeNode): Boolean {
                        val matches = node.displayText.lowercase().contains(query) ||
                                node.textContent.lowercase().contains(query) ||
                                node.tagName.lowercase().contains(query)
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

                fun flattenAndAddSearch(node: TreeNode) {
                    if (query.isNotEmpty() && node.id !in matchIds) return

                    val isMatch = query.isNotEmpty() && (
                            node.displayText.lowercase().contains(query) ||
                            node.textContent.lowercase().contains(query) ||
                            node.tagName.lowercase().contains(query)
                    )

                    treeContainer.addView(buildRow(node))

                    if (isMatch) {
                        treeContainer.getChildAt(treeContainer.childCount - 1)?.let { row ->
                            row.setBackgroundColor(
                                Color.parseColor("#40" + Integer.toHexString(
                                    ContextCompat.getColor(context, android.R.color.holo_blue_light)
                                ).substring(2))
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

            // ===== 搜索栏 =====
            val searchInputLayout = TextInputLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                hint = "搜索标签名 / 属性 / 文本内容..."
                isHintEnabled = true
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                startIconDrawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, android.R.drawable.ic_menu_search)
            }
            val searchInput = TextInputEditText(context).apply {
                setSingleLine(true)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        searchQuery.clear()
                        searchQuery.append(s?.toString() ?: "")
                        render()
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            searchInputLayout.addView(searchInput)

            // ===== 底部工具栏（增强版） =====
            val toolbarLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // 第一行：展开/折叠控制
            val controlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val btnExpandAll = MaterialButton(context).apply {
                text = "全部展开"
                textSize = 12f
                setPadding(12, 4, 12, 4)
                setOnClickListener {
                    // 展开所有节点
                    fun expandAll(node: TreeNode) {
                        expandedIds.add(node.id)
                        node.children.forEach { expandAll(it) }
                    }
                    rootNodes.forEach { expandAll(it) }
                    render()
                }
            }

            val btnCollapseAll = MaterialButton(context).apply {
                text = "全部折叠"
                textSize = 12f
                setPadding(12, 4, 12, 4)
                setOnClickListener {
                    expandedIds.clear()
                    render()
                }
            }

            val btnExpandToLevel = MaterialButton(context).apply {
                text = "展开到..."
                textSize = 12f
                setPadding(12, 4, 12, 4)
                setOnClickListener {
                    showLevelPicker(context, rootNodes, expandedIds) { render() }
                }
            }

            controlRow.addView(btnExpandAll)
            controlRow.addView(btnCollapseAll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12; marginEnd = 12 })
            controlRow.addView(btnExpandToLevel)

            toolbarLayout.addView(controlRow)

            // 第二行：缩放控制
            val scaleRow = createScaleBar(context, minScale, maxScale) { factor ->
                zoomWrapper.scaleX = factor
                zoomWrapper.scaleY = factor
            }
            toolbarLayout.addView(scaleRow)

// ===== 总布局 =====
            val bodyLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(searchInputLayout)
                addView(
                    scrollView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        400.dp(context)
                    )
                )
                addView(toolbarLayout)
            }

            val titleView = TextView(context).apply {
                text = "HTML 结构"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(24, 16, 24, 8)
                setTextColor(ContextCompat.getColor(context, R.color.md_primary))
            }

            MaterialAlertDialogBuilder(
                context,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
            )
                .setCustomTitle(titleView)
                .setView(bodyLayout)
                .setPositiveButton("关闭", null)
                .show()

            render()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ===== 展开到指定层级对话框 =====
    private fun showLevelPicker(
        context: Context,
        rootNodes: List<TreeNode>,
        expandedIds: MutableSet<Int>,
        onComplete: () -> Unit
    ) {
        val maxDepth = rootNodes.maxOfOrNull { getMaxDepth(it) } ?: 1
        val levels = (1..maxDepth).map { "展开到第 ${it} 层" }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("选择展开层级")
            .setItems(levels) { _, which ->
                val targetLevel = which + 1
                expandedIds.clear()
                fun expandToLevel(node: TreeNode, depth: Int) {
                    if (depth < targetLevel) {
                        expandedIds.add(node.id)
                        node.children.forEach { expandToLevel(it, depth + 1) }
                    }
                }
                rootNodes.forEach { expandToLevel(it, 0) }
                onComplete()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getMaxDepth(node: TreeNode): Int {
        return if (node.children.isEmpty()) node.depth + 1
        else node.children.maxOf { getMaxDepth(it) }
    }

    // ===== 标签颜色 =====
    private fun getTagColor(context: Context, tagName: String): Int {
        return when (tagName.lowercase()) {
            "div", "span", "section", "article", "header", "footer", "main", "nav" ->
                ContextCompat.getColor(context, R.color.html_tag_color)  // 蓝
            "a", "link" ->
                Color.parseColor("#2E7D32")  // 绿
            "img", "video", "audio", "source", "picture" ->
                Color.parseColor("#E65100")  // 橙
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "b", "strong", "i", "em" ->
                Color.parseColor("#6A1B9A")  // 紫
            "button", "input", "form", "select", "textarea", "label" ->
                Color.parseColor("#00695C")  // 青绿
            "script", "style", "noscript" ->
                Color.parseColor("#757575")  // 灰
            else -> ContextCompat.getColor(context, R.color.md_on_surface)
        }
    }

    // ===== Jsoup 树构建 =====
    private fun buildTreeFromJsoup(doc: Document): List<TreeNode> {
        var nextId = 0
        val body = doc.body() ?: doc

        fun buildTree(el: Element, depth: Int): TreeNode {
            val id = nextId++
            val xpath = buildXPathSimple(el)
            val display = buildDisplayText(el)
            val text = el.ownText().trim()
            val kids = el.children().map { buildTree(it, depth + 1) }
            val hasImportantAttr = el.hasAttr("id") || el.hasAttr("class") ||
                    el.attributes().asList().any { it.key.startsWith("data-") }
            return TreeNode(id, depth, xpath, kids, display, text, kids.isNotEmpty(), el.tagName(), hasImportantAttr)
        }

        return body.children().map { buildTree(it, 0) }
    }

    private fun buildDisplayText(element: Element): String {
        val sb = StringBuilder()
        var added = 0
        for (attr in listOf("href", "src", "title", "alt", "data-src", "data-original", "class", "id")) {
            if (element.hasAttr(attr)) {
                val v = element.attr(attr)
                if (v.isNotBlank()) {
                    sb.append(" $attr=\"${if (v.length <= 20) v else "${v.take(18)}..."}\"")
                    if (++added >= 2) break
                }
            }
        }
        val text = element.ownText().trim().take(20)
        if (text.isNotEmpty()) {
            val escaped = text.replace("\n", " ").replace(Regex("\\s+"), " ")
            sb.append(" \"$escaped\"")
        }
        return sb.toString()
    }

    private fun buildXPathSimple(element: Element): String {
        val parts = mutableListOf<String>()
        var cur: Element? = element
        while (cur != null && cur.tagName() !in listOf("body", "html")) {
            val tag = cur.tagName()
            val preds = mutableListOf<String>()
            if (cur.hasAttr("id")) {
                preds.add("@id='${cur.id()}'")
            }
            val classStr = cur.className()
            if (classStr.isNotEmpty()) {
                preds.add("@class='$classStr'")
            }
            val ownText = cur.ownText().trim()
            if (preds.isEmpty() && ownText.length > 3) {
                val safeText = ownText.filter { it != '\'' }
                preds.add("text()='$safeText'")
            }
            if (preds.isEmpty()) {
                for (attr in cur.attributes()) {
                    val name = attr.key
                    val value = attr.value
                    if (name.startsWith("data-") && value.isNotBlank()) {
                        preds.add("@$name='$value'")
                        break
                    }
                }
            }
            val parent = cur.parent()
            if (parent != null) {
                val same = parent.children().filter { it.tagName() == tag }
                if (same.size > 1) {
                    val idx = same.indexOfFirst { it == cur } + 1
                    preds.add("$idx")
                }
            }
            val step = if (preds.isEmpty()) tag else "$tag[${preds.joinToString(" and ")}]"
            parts.add(0, step)
            cur = cur.parent()
        }
        return "//" + parts.joinToString("/")
    }

    // ===== 缩放 =====
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
                        currentScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
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

    private fun createScaleBar(
        context: Context,
        minScale: Float,
        maxScale: Float,
        onScaleChanged: (Float) -> Unit
    ): View {
        var currentScale = 1.0f
        val step = 0.25f

        val zoomOutBtn = MaterialButton(context).apply {
            text = "−"
            textSize = 18f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                currentScale = (currentScale - step).coerceAtLeast(minScale)
                onScaleChanged(currentScale)
            }
        }

        val resetBtn = MaterialButton(context).apply {
            text = "1×"
            textSize = 12f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                currentScale = 1.0f
                onScaleChanged(currentScale)
            }
        }

        val zoomInBtn = MaterialButton(context).apply {
            text = "+"
            textSize = 18f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                currentScale = (currentScale + step).coerceAtMost(maxScale)
                onScaleChanged(currentScale)
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
            addView(zoomOutBtn)
            addView(resetBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 16; marginEnd = 16 })
            addView(zoomInBtn)
        }
    }

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
}