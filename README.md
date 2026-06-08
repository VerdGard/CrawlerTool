# Crawler Tool

一款轻量级 Android 网页数据提取工具。输入 URL 和 XPath 表达式，即可从网页中提取结构化数据，支持 HTML 结构可视化浏览和选择器生成。

## 功能特性

### 核心功能
- **XPath 数据提取** — 输入目标网页 URL 和 XPath 表达式，快速提取节点 HTML、属性值或文本内容
- **自定义 XPath 引擎** — 基于 Jsoup DOM 树实现，无需依赖 `javax.xml.xpath`，能处理真实世界中不规范 HTML
- **HTML 树形结构查看** — 可视化 HTML DOM 树，支持点击展开/折叠、双指缩放、长按复制 XPath
- **实时文本搜索** — 在 HTML 树中搜索标签名、属性或文本内容，自动展开匹配路径并高亮定位
- **语法高亮** — XPath 匹配结果中的 HTML 代码自动着色（标签=蓝、属性=红、值=绿）
- **快捷属性标签** — 一键插入 `@href`、`@title`、`@data-original`、`@data-src`、`text()` 等常用 XPath 片段
- **离线 XPath 使用指南** — 内置 XPath 参考文档，无需网络即可查阅

### 体验设计
- **Material 3 设计** — 全局 Material You 风格，完整支持日间/夜间深色主题
- **双指缩放** — HTML 树形视图支持手势缩放（0.6x — 2.5x），大屏/小屏均可用
- **一键复制** — 可复制单个匹配结果或整个 XPath 表达式到剪贴板
- **结果卡片展开/折叠** — 长内容自动折叠，点击展开查看更多

---

## 技术架构

### 技术栈
| 层 | 技术选型 |
|---|---|
| 语言 | Kotlin 100% |
| UI | Material 3 (Material Design Components) + ViewBinding |
| 网络 | OkHttp 4.12.0 (自定义 User-Agent，自动重定向，15s 连接超时) |
| HTML 解析 | Jsoup 1.17.2 |
| XPath 求值 | 自研 JsoupXPathEngine（基于 Jsoup DOM 树） |
| JSON 解析 | Gson 2.10.1 |
| 最低支持 | Android 5.0 (API 21) |
| 目标 SDK | Android 14 (API 34) |

### 项目结构

```
CrawlerTool/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── xpath_rules.md                  # 内置 XPath 使用指南
│   │   ├── kotlin/com/SoloSu/Crawler_tool/
│   │   │   ├── MainActivity.kt                 # 主界面，UI 交互逻辑
│   │   │   ├── CrawlerEngine.kt                # 爬虫引擎：网络请求 + 匹配编排
│   │   │   ├── JsoupXPathEngine.kt             # 自研 XPath 求值引擎（~450 行）
│   │   │   ├── HtmlTreeDialog.kt               # HTML 树形结构对话框
│   │   │   ├── HtmlUtil.kt                     # HTML 语法高亮工具
│   │   │   ├── HtmlTreeActivity.kt             # （已废弃）兼容引用
│   │   │   ├── ResultAdapter.kt                # RecyclerView 适配器 + 高亮渲染
│   │   │   └── ResultItem.kt                   # 数据模型
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml           # 主界面布局
│   │   │   │   └── item_result.xml             # 结果卡片布局
│   │   │   ├── values/
│   │   │   │   ├── colors.xml                  # 日间主题配色
│   │   │   │   ├── strings.xml                 # 字符串资源
│   │   │   │   └── themes.xml                  # 主题定义
│   │   │   ├── values-night/
│   │   │   │   ├── colors.xml                  # 夜间主题配色
│   │   │   │   └── themes.xml                  # 夜间主题定义
│   │   │   ├── menu/
│   │   │   │   └── toolbar_menu.xml            # 工具栏菜单
│   │   │   ├── drawable/                       # 自定义图形
│   │   │   └── mipmap/                         # 应用图标
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle                                # 项目级构建配置
├── settings.gradle                             # 项目设置
└── gradle.properties                           # Gradle 属性
```

### 核心模块说明

#### JsoupXPathEngine — 自研 XPath 引擎

不依赖 `javax.xml.xpath` 或 Android 内置 XML 解析器，直接在 Jsoup DOM 树上求值。

**支持的 XPath 语法：**

| 语法 | 示例 | 说明 |
|------|------|------|
| 标签选择 | `//div`, `/html/body` | 任意层级 / 直接子级 |
| 属性访问 | `//a/@href`, `//*[@id]` | 提取属性值或检测属性存在 |
| 文本获取 | `//div/text()`, `//div/normalize-space()` | 提取文本节点 |
| 位置谓词 | `[n]`, `[last()]`, `[position()<3]` | 按位置过滤 |
| 属性相等 | `[@class='foo']`, `[@id="bar"]` | 支持单/双引号 |
| 属性包含 | `[contains(@class, 'list')]` | 字符串包含 |
| 属性开头 | `[starts-with(@href, 'https')]` | 字符串前缀匹配 |
| 文本匹配 | `[text()='标题']`, `[contains(text(),'关键')]` | 文本内容过滤 |
| 逻辑组合 | `[@class='a' and @id='b']`, `[@class='a' or @class='b']` | 与/或 |
| 通配符/父级 | `*`, `..`, `.` | 通配、父节点、当前节点 |

**表达式缓存：** 自动缓存最近 16 个已解析的 XPath 表达式，避免重复解析开销。

#### HtmlTreeDialog — HTML 树形结构查看器

基于标准 DOM API (`javax.xml.parsers`) 的纯 Kotlin 树形控件，无需第三方树组件。

特性：
- 递归构建 DOM 树 → 扁平化渲染（LinearLayout）
- 展开/折叠带布局动画（LayoutTransition）
- 双指缩放手势（ScaleGestureDetector）
- 实时文本搜索过滤，匹配节点自动展开+高亮+自动滚动定位
- 长按任意节点复制绝对 XPath
- 视图缓存（viewCache / childContainerCache），展开/折叠不重建 View

#### CrawlerEngine — 爬虫引擎

- 基于 OkHttp 的 HTTP 客户端，15s 连接/30s 读取超时
- 移动端标准 User-Agent + Accept 头
- 自动跟随重定向（HTTP → HTTPS）
- 请求/匹配分离：`fetchAndMatch()` 和 `matchOnly()` 两个入口

---

## 使用方式

### 快速开始

1. 在 URL 输入框输入目标网页地址（如 `https://example.com`）
2. 在表达式输入框输入 XPath 表达式（如 `//a/@href`）
3. 点击「匹配」按钮或键盘上的 Done/Go 键
4. 查看匹配结果，长按结果卡片可查看 HTML 树形结构

### 常用 XPath 示例

```xpath
//a                        # 选取所有链接标签
//a/@href                  # 提取所有链接地址
//a/text()                 # 提取所有链接文本
//img/@src                 # 提取所有图片地址
//div[@class='title']      # 选取 class 为 title 的 div
//div[contains(@class,'list')]  # class 包含 list 的 div
//a[starts-with(@href,'https')] # 以 https 开头的链接
//*[@data-original]/@data-original  # 提取懒加载图片地址
//ul/li/a                  # 列表中的链接
//div[@id='main']//a       # 指定容器内的所有链接
```

### 快捷属性标签

快捷插入常用 XPath 片段，Cursor 位置决定插入点。

---

## 依赖

```kotlin
// build.gradle (app)
dependencies {
    implementation 'androidx.core:core-ktx:latest'
    implementation 'androidx.appcompat:appcompat:latest'
    implementation 'com.google.android.material:material:latest'
    implementation 'androidx.constraintlayout:constraintlayout:latest'

    // Jsoup - HTML 解析
    implementation 'org.jsoup:jsoup:1.17.2'
    
    // OkHttp - 网络请求
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Gson - JSON 解析
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

---

## 构建

### 环境要求
- Android Studio / AndroidIDE
- JDK 17+
- Android SDK 36+
- Gradle 9.0+

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/<你的用户名>/CrawlerTool.git
cd CrawlerTool

# 使用 Gradle 构建
./gradlew assembleDebug

# 或使用 Android Studio 打开后 Build > Rebuild Project
```

APK 生成路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 开源协议

本项目基于 **MIT License** 开源。

```
MIT License

Copyright (c) 2025 SoloSu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 作者

**SoloSu**

---

## 致谢

- [Jsoup](https://jsoup.org/) — 强大的 HTML 解析库
- [OkHttp](https://square.github.io/okhttp/) — 高效的 HTTP 客户端
- [Material Design 3](https://m3.material.io/) — 现代设计语言
- [Gson](https://github.com/google/gson) — JSON 序列化/反序列化
