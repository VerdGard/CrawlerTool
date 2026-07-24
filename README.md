# Crawler Tool

> 轻量级 Android 网页数据提取工具 — 支持 **XPath / CSS 选择器 / 正则表达式** 三种匹配模式，内置 HTML 树形结构查看器，支持批量抓取与多格式导出。

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#)

---

## 功能特性

### 🔍 三元匹配引擎

支持三种匹配模式，一键切换：

| 模式 | 引擎 | 适用场景 |
|------|------|----------|
| **XPath** | 自研 JsoupXPathEngine | 结构化 HTML 数据的精准定位 |
| **CSS 选择器** | Jsoup 原生 `select()` | 类 jQuery 语法，快速筛选元素 |
| **正则表达式** | Kotlin `Regex` | 文本模式匹配，捕获组提取 |

切换模式时输入框提示、快捷标签栏会同步更新，并带有平滑过渡动画。

### 📦 批量抓取

支持分页爬取：在 URL 模板中使用 `{page}` 占位符，指定起始/结束页码即可自动遍历多页抓取。

```
示例: https://example.com/news?page={page}
起始: 1, 结束: 5 → 自动抓取 page=1~5
```

### 🧬 HTML 树形结构查看器

基于 Jsoup DOM 树构建的交互式树形控件：

- 整行点击展开/折叠（不限三角符号）
- 层级缩进指示线，视觉引导清晰
- 标签颜色区分：`div`=蓝, `a`=绿, `img`=橙, 其他=灰
- 有重要属性（`id`/`class`/`data-*`）的节点显示圆点标记
- 双指缩放手势（0.6x–2.5x）
- 实时文本搜索过滤，匹配节点自动展开 + 高亮 + 滚动定位
- 长按任意节点复制绝对 XPath
- 底部工具栏：展开全部 / 折叠全部 / 展开到指定层
- 节点右侧显示子节点数量

### 🎨 Material 3 主题系统

完整支持三种主题模式：

| 模式 | 说明 |
|------|------|
| 🖥 跟随系统 | 自动切换日间/夜间 |
| ☀️ 浅色 | 固定浅色主题 |
| 🌙 深色 | 固定深色主题 |

主题切换时带有淡入淡出动画，全局实时生效。

### ⚙️ 可定制设置

- **User-Agent** — 自定义请求头，应对反爬限制
- **连接超时** — 5–60 秒可调（默认 15s）
- **读取超时** — 10–120 秒可调（默认 30s）
- **主题选择** — 跟随系统 / 浅色 / 深色

### 📊 结果管理

- **分页浏览** — 每页 20 条，支持前后翻页
- **结果卡片** — 序号标签 + 语法高亮 + 属性标签摘要
- **展开/折叠** — 长内容自动折叠，点击展开
- **单条复制** — 一键复制任意结果到剪贴板
- **规则复制** — 复制当前表达式

### 📤 多格式导出

支持三种导出格式：

| 格式 | 说明 |
|------|------|
| **JSON** | 结构化索引 + 内容 |
| **CSV** | 表格格式，含转义处理 |
| **纯文本** | 逐行输出 |

导出文件保存在应用缓存目录，可通过文件管理器获取。

### 🌐 HTML 预览

长按结果卡片，可选择：
- **HTML 树形结构** — 可视化 DOM 树
- **WebView 预览** — 渲染 HTML 页面效果

### 📖 内置 XPath 规则文档

从菜单栏可打开离线 XPath 参考文档，基于 WebView 渲染 Markdown，支持日间/夜间主题自适应。

---

## 技术架构

### 技术栈

| 层 | 技术选型 |
|---|---|
| 语言 | Kotlin 100% |
| UI | Material 3 (Material Design Components) + ViewBinding |
| 网络 | OkHttp 4.12.0（自定义拦截器，自动重定向） |
| HTML 解析 | Jsoup 1.17.2 |
| XPath 求值 | **自研 JsoupXPathEngine**（基于 Jsoup DOM 树，约 500 行） |
| JSON 解析 | Gson 2.10.1 |
| 最低支持 | Android 5.0 (API 21) |
| 目标 SDK | Android 14 (API 34) |
| 构建 | Gradle 9.0+ / Kotlin 2.1+ |

### 项目结构

```
CrawlerTool/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── xpath_rules.md                       # 内置 XPath 使用指南
│   │   ├── kotlin/com/SoloSu/Crawler_tool/
│   │   │   ├── MainActivity.kt                      # 主界面：UI 交互 + 功能编排
│   │   │   ├── SettingsActivity.kt                  # 设置页面：UA/超时/主题
│   │   │   ├── CrawlerEngine.kt                     # 爬虫引擎：网络请求 + 三种匹配模式 + 导出
│   │   │   ├── JsoupXPathEngine.kt                  # 自研 XPath 求值引擎（~500 行）
│   │   │   ├── HtmlTreeDialog.kt                    # HTML 树形结构对话框
│   │   │   ├── ResultAdapter.kt                     # 结果列表适配器 + 语法高亮渲染
│   │   │   ├── ResultItem.kt                        # 数据模型
│   │   │   └── HtmlUtil.kt                          # HTML 语法高亮工具
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml                # 主界面布局（输入区 + 模式选择 + 批量区）
│   │   │   │   ├── activity_settings.xml            # 设置布局
│   │   │   │   └── item_result.xml                  # 结果卡片布局
│   │   │   ├── anim/
│   │   │   │   ├── fade_in.xml                      # 主题切换淡入动画
│   │   │   │   └── fade_out.xml                     # 主题切换淡出动画
│   │   │   ├── drawable/                            # 自定义图形（渐变背景、玻璃卡片等）
│   │   │   ├── drawable-night/                      # 深色模式专属图形
│   │   │   ├── values/                              # 日间主题配色、字符串、主题定义
│   │   │   ├── values-night/                        # 夜间主题配色
│   │   │   └── menu/
│   │   │       └── toolbar_menu.xml                 # 工具栏菜单（导出、结构查看、设置、关于）
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle                                     # 项目级构建配置
├── settings.gradle                                  # 项目设置
└── gradle.properties                                # Gradle 属性
```

### 核心模块详解

#### JsoupXPathEngine — 自研 XPath 引擎

不依赖 `javax.xml.xpath`，直接在 Jsoup DOM 树上求值，能处理真实世界中不规范 HTML。

**支持的 XPath 语法：**

| 语法 | 示例 | 说明 |
|------|------|------|
| 标签选择 | `//div`, `/html/body` | 任意层级 / 直接子级 |
| 属性访问 | `//a/@href`, `//*[@id]` | 提取属性值或检测属性存在 |
| 文本获取 | `//div/text()`, `//div/normalize-space()` | 提取文本节点 |
| 位置谓词 | `[n]`, `[last()]`, `[position()<3]` | 按位置过滤 |
| 属性相等/不等 | `[@class='foo']`, `[@class!='bar']` | 支持单/双引号 |
| 属性包含 | `[contains(@class, 'list')]` | 字符串包含 |
| 属性开头 | `[starts-with(@href, 'https')]` | 字符串前缀匹配 |
| 文本匹配 | `[text()='标题']`, `[contains(text(),'关键')]` | 文本内容过滤 |
| 逻辑组合 | `[@class='a' and @id='b']`, `[A or B]` | 与/或 |
| 否定谓词 | `[not(@class)]`, `[not(contains(@class,'hidden'))]` | 取反 |
| 通配符/父级 | `*`, `..`, `.` | 通配、父节点、当前节点 |
| 显式轴 | `child::div`, `descendant::span`, `parent::`, `ancestor::`, `following-sibling::`, `preceding-sibling::` | 完整轴支持 |
| last() 比较 | `[last()>1]`, `[position()=last()]` | 与上下文大小比较 |
| normal-space | `[normalize-space()]`, `[normalize-space(@attr)]` | 合并空白后判断 |
| 复合谓词 | `[@class='row' and position()=2]`, `[@class='row'][2]` | 两写法等价 |
| 命名空间跳过 | `ns:div` → 自动跳过前缀 | 处理带命名空间的 XML |

**表达式缓存：** 自动缓存最近 16 个已解析的 XPath 步骤，避免重复解析。

#### CrawlerEngine — 爬虫引擎

- **三种匹配模式：** XPath / CSS 选择器 / 正则表达式，通过 `sealed class MatchMode` 统一抽象
- **双入口设计：** `fetchAndMatch()` 先请求再匹配，`matchOnly()` 在已有 HTML 上重匹配
- **OkHttp 拦截器：** 自动注入移动端 User-Agent、Accept、Accept-Language 头
- **自动重定向：** 同步跟随 HTTP→HTTPS 和 SSL 重定向
- **多格式导出：** `exportResults()` 支持 JSON / CSV / 纯文本

#### HtmlTreeDialog — HTML 树形结构查看器

纯 Kotlin 实现的树形控件，无需第三方库：

- 递归构建 DOM 树 → 扁平化 LinearLayout 渲染
- 展开/折叠带 LayoutTransition 动画
- 双指缩放（ScaleGestureDetector）
- 实时搜索过滤，匹配节点自动展开+高亮+滚动
- 长按复制绝对 XPath
- 视图缓存（viewCache / childContainerCache），避免重复 inflate

#### ResultAdapter — 结果列表

- **语法高亮：** 基于正则的 HTML 标签着色（标签=蓝，属性=红，值=绿）
- **高亮缓存：** 32 条 WeakReference 缓存，避免重复解析
- **属性标签摘要：** 自动提取结果中的 `@href`、`@src` 等属性标签
- **长内容折叠：** 超过 3 行自动折叠，点击展开

---

## 使用方式

### 快速开始

1. 输入目标网页 URL（如 `https://example.com`）
2. 选择匹配模式：**XPath** / **CSS 选择器** / **正则表达式**
3. 输入表达式，点击「匹配」或键盘 Done/Go 键
4. 浏览匹配结果，支持分页翻页、单条复制、导出

### 常用表达式示例

**XPath：**
```xpath
//a                        # 所有链接标签
//a/@href                  # 提取所有链接地址
//a/text()                 # 提取所有链接文本
//img/@src                 # 提取所有图片地址
//div[@class='title']      # class 为 title 的 div
//div[contains(@class,'list')]  # class 包含 list 的 div
//a[starts-with(@href,'https')]  # 以 https 开头的链接
//*[@data-original]/@data-original  # 懒加载图片地址
//div[@id='main']//a       # 指定容器内所有链接
//ul/li/a                  # 列表中的链接
```

**CSS 选择器：**
```css
a                           /* 所有链接 */
a[href]                     /* 有 href 属性的链接 */
div.title                   /* class 为 title 的 div */
#content                    /* id 为 content 的元素 */
div > a                     /* div 的直接子链接 */
a[href^="https"]            /* 以 https 开头的链接 */
img[data-src]               /* 有 data-src 的图片 */
```

**正则表达式：**
```regex
href="([^"]+)"              /* 提取所有链接地址 */
<img[^>]+src="([^"]+)"      /* 提取图片地址 */
<h2>([^<]+)</h2>            /* 提取标题文本 */
<div class="title">([\s\S]*?)</div>  /* 提取 div 内容 */
```

### 快捷属性标签

XPath 模式下，输入框下方显示快捷属性芯片，点击即可在光标位置插入：

`@href` `@title` `@data-original` `@data-src` `text()`

### 批量抓取

1. 输入 URL 模板，用 `{page}` 替代页码（如 `https://news.com/page/{page}`）
2. 设置起始页码和结束页码
3. 点击「开始批量抓取」，自动遍历所有页面
4. 结果自动合并，支持导出

### 查看 HTML 结构

- **菜单栏 → 查看结构** — 直接抓取当前 URL 的 HTML 树
- **长按结果卡片** — 查看该条结果的 HTML 结构树
- 在树中可搜索、缩放、复制节点 XPath

### 导出结果

菜单栏 → 导出 → 选择格式（JSON / CSV / 纯文本）

---

## 构建

### 环境要求

- Android Studio 或 AndroidIDE
- JDK 17+
- Android SDK 36+
- Gradle 9.0+

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/你的用户名/CrawlerTool.git
cd CrawlerTool

# 使用 Gradle 构建
./gradlew assembleDebug

# 或使用 IDE 打开后 Build > Rebuild Project
```

APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

### 依赖

```kotlin
dependencies {
    implementation 'androidx.core:core-ktx:latest'
    implementation 'androidx.appcompat:appcompat:latest'
    implementation 'com.google.android.material:material:latest'
    implementation 'androidx.constraintlayout:constraintlayout:latest'
    implementation 'org.jsoup:jsoup:1.17.2'          // HTML 解析
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'  // 网络请求
    implementation 'com.google.code.gson:gson:2.10.1'    // JSON 解析
}
```

---

## 版本历史

| 版本 | 新特性 |
|------|--------|
| v1.2 | 设置页面、主题系统、批量抓取、多格式导出、CSS/正则模式 |
| v1.1 | HTML 树形结构查看器、XPath 引擎增强、语法高亮 |
| v1.0 | 基础 XPath 匹配、结果展示 |

---

## 关于

**作者：** SoloSu  
**技术栈：** Kotlin + Material 3 + Jsoup + OkHttp  
**最低支持：** Android 5.0 (API 21)  
**目标 SDK：** Android 14 (API 34)
