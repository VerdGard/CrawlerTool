# Crawler Tool

> 轻量级 Android 网页数据提取工具 — 支持 **XPath / CSS 选择器 / 正则表达式** 三种匹配模式，内置 HTML 树形结构查看器，支持批量抓取、JS 渲染、代理、历史记录、自定义请求头与多格式导出。

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#)
[![Version](https://img.shields.io/badge/Version-1.3-blue)](#)

---

## ✨ 功能特性

### 🔍 三元匹配引擎

支持三种匹配模式，一键切换，切换时输入框提示、快捷标签栏同步更新：

| 模式 | 引擎 | 适用场景 |
|------|------|----------|
| **XPath** | 自研 JsoupXPathEngine（~700 行） | 结构化 HTML 数据的精准定位 |
| **CSS 选择器** | Jsoup 原生 `select()` | 类 jQuery 语法，快速筛选元素 |
| **正则表达式** | Kotlin `Regex` | 文本模式匹配，捕获组提取 |

### 📦 批量抓取

支持分页爬取，在 URL 模板中使用 `{page}` 占位符，自动遍历多页：

```
示例: https://example.com/news?page={page}
起始: 1, 结束: 5 → 自动抓取 page=1~5
```

可配置**请求延迟**（0~5 秒）和**失败重试次数**（0~5 次），降低被封风险。

### 🧬 HTML 树形结构查看器

基于 Jsoup DOM 树构建的交互式树形控件（增强版）：

- 整行点击展开/折叠（不限三角符号）
- 层级缩进指示线，视觉引导清晰
- 标签颜色区分：`div`=蓝, `a`=绿, `img`=橙, `h1~h6`=紫, 表单=青绿, 脚本=灰
- 有重要属性（`id`/`class`/`data-*`）的节点显示圆点标记
- 双指缩放手势（0.6x–2.5x）及按钮缩放控制
- 实时文本搜索过滤，匹配节点自动展开 + 高亮 + 滚动定位
- **长按任意节点复制绝对 XPath**
- **底部工具栏**：展开全部 / 折叠全部 / **展开到指定层**
- 节点右侧显示子节点数量徽标

### 🎨 Material 3 主题系统

完整支持三种主题模式，主题切换实时生效：

| 模式 | 说明 |
|------|------|
| 🖥 跟随系统 | 自动切换日间/夜间（默认，支持 Material You 动态取色） |
| ☀️ 浅色 | 固定浅色主题 |
| 🌙 深色 | 固定深色主题 |

新增 **`BaseActivity` 基类**，统一管理所有 Activity 的主题应用逻辑，进入设置页切换主题后自动 `recreate()` 生效。

### ⚙️ 可定制设置（大幅增强）

- **User-Agent** — 自定义请求头，支持预设快捷选择（Chrome Android / Edge Desktop / iPhone Safari）
- **连接超时** — 5–60 秒可调（默认 15s）
- **读取超时** — 10–120 秒可调（默认 30s）
- **主题选择** — 跟随系统 / 浅色 / 深色
- **自定义请求头** — 内联添加/删除/编辑任意 HTTP 请求头，支持启用/禁用开关
- **代理设置** — 支持 HTTP 代理，配置主机和端口
- **JavaScript 渲染** — 使用 WebView 渲染 SPA 页面，等待 JS 执行完成后再提取 HTML（延迟 1~10 秒可调）
- **批量请求延迟** — 0~5 秒，控制批量抓取间隔
- **失败重试次数** — 0~5 次，请求失败自动重试

### 📖 历史记录管理

**全新 HistoryManager（P0.1），自动保存最近 100 条记录：**

- 每次匹配成功自动保存 URL + 表达式 + 匹配模式 + 结果数量
- 菜单栏可打开历史记录弹窗，点击一键回填
- 自动去重，相同 URL+表达式+模式不重复保存
- 支持清空所有历史

### 📊 结果管理

- **分页浏览** — 每页 20 条，支持前后翻页
- **结果卡片** — 序号标签 + 语法高亮 + 属性标签摘要
- **展开/折叠** — 长内容自动折叠（超过 3 行），点击展开/收起
- **单条复制** — 一键复制任意结果到剪贴板
- **规则复制** — 复制当前表达式
- **长按结果卡片** — 打开 HTML 树形结构查看器

### 📤 多格式导出

支持三种导出格式（v1.3 新增 MediaStore 兼容导出，支持 Android 10+）：

| 格式 | 说明 |
|------|------|
| **JSON** | 结构化索引 + 内容 |
| **CSV** | 表格格式，含转义处理 |
| **纯文本** | 逐行输出 |

导出位置：`/storage/emulated/0/Crawler-Tool/`（自动创建目录，Android 10+ 通过 MediaStore 写入 **Downloads/Crawler-Tool**）

### 🌐 HTML 预览 & XPath 规则手册

- **长按结果卡片** → 打开 HTML 树形结构查看器
- **菜单栏 → 查看结构** → 抓取当前 URL 的 DOM 树
- **菜单栏 → XPath 规则** → 全新的 `XPathRulesActivity`，离线渲染 Markdown 手册
  - 支持 H1~H3 标题、代码块、引用、表格、有序/无序列表
  - 代码块可一键复制
  - 自动适配日间/夜间主题
  - 内联代码着色 + 加粗渲染

### 🧪 JavaScript 渲染引擎

`JsRenderEngine`（P2.11）使用 WebView 加载 SPA 页面，等待 JS 渲染完成后再提取 HTML：

- 支持设置等待时间（1~10 秒）
- 协程 API，超时保护（等待 + 5 秒）
- Unicode 转义解码
- 适用于反爬虫检测、动态加载内容的页面

### 🔐 Cookie 与请求头管理

`HeaderManager` 提供完整的 HTTP 请求头管理：

- 自定义 Header 的增/删/改/查
- 开关控制是否启用自定义头
- Cookie 快捷存取
- 自动注入到 OkHttp 请求拦截器

### 📦 通知渠道

`NotificationHelper` 为定时抓取任务创建通知渠道，确保通知正常显示（Android 8.0+）。

---

## 🔄 v1.3 更新内容（本期版本 vs 云端 v1.2）

| 分类 | 新增内容 | 详细说明 |
|------|----------|----------|
| **架构** | `BaseActivity` 基类 | 统一管理主题加载逻辑，子类自动继承 |
| **架构** | ViewModel+Repository 模式 | `MainViewModel` + `SettingsViewModel`，`CrawlerRepository`、`SettingsRepository`、`HistoryRepository` 三层架构 |
| **架构** | MVVM 状态驱动 | `MainUiState` + `MainEvent` 分离 UI 状态与事件 |
| **历史** | `HistoryManager` | 自动保存最近 100 条记录的 URL/表达式/模式/结果数，支持一键回填、去重 |
| **历史** | 历史记录 UI | 菜单栏历史按钮，弹窗显示所有记录，点击即加载 |
| **设置-请求头** | `HeaderManager` | 自定义 HTTP 请求头的增删改查，JSON 序列化持久化 |
| **设置-请求头** | 内联 Header 编辑 | 设置页内联添加/删除 Header 条目，支持开关 |
| **设置-代理** | HTTP 代理支持 | 设置页配置代理主机/端口，OkHttp 拦截器注入 |
| **设置-JS** | JS 渲染引擎 | `JsRenderEngine` 基于 WebView，协程 API，等待渲染完成 |
| **设置-延迟** | 批量请求延迟 | 0~5 秒可调，降低反爬风险 |
| **设置-重试** | 失败重试次数 | 0~5 次自动重试 |
| **设置-UA** | UA 快捷预设 | Chrome Android / Edge Desktop / iPhone Safari 三选一 |
| **文档** | `XPathRulesActivity` | 离线渲染 `xpath_rules.md`，支持代码块复制、表格、主题自适应 |
| **文档** | 设置页入口 | 设置页底部入口跳转 XPath 规则手册 |
| **导出** | MediaStore 兼容 | Android 10+ 通过 MediaStore 导出到 Downloads，无需文件权限 |
| **导出** | 存储权限管理 | Android 11+ 请求 MANAGE_EXTERNAL_STORAGE，Android 10 请求 WRITE_EXTERNAL_STORAGE |
| **UI-主题** | 三主题分离 | `AppTheme_System` / `AppTheme_Light` / `AppTheme_Dark` 各自独立 |
| **UI-主题** | Dynamic Color | 系统主题支持 Material You 动态取色 |
| **UI-风格** | 圆角统一 | 卡片 12dp/16dp，按钮 16dp/22dp，芯片 18dp |
| **UI-风格** | colorSurfaceContainer | 使用 Material 3 表面容器色 |
| **UI-树** | 增强树控件 | 搜索过滤+高亮、展开到指定层、缩放按钮、标签颜色扩展、深色模式适配 |
| **UI-树** | 扩展标签色 | 新增表单元素（青绿）、文本标签（紫）、脚本（灰）类别 |
| **适配** | `android:windowSoftInputMode="adjustNothing"` | 避免键盘弹出挤压布局 |
| **适配** | `android11+` 权限适配 | 全新存储权限检查流程 |
| **打包** | versionCode 6 / versionName 1.3 | 构建版本升级 |
| **编译** | compileSdk 36 / targetSdk 34 | SDK 升级 |
| **依赖** | Coil 图片加载 | `io.coil-kt:coil:2.6.0` |
| **依赖** | Lifecycle 库 | `lifecycle-livedata-ktx:2.9.0` |
| **代码** | `ResultItem` 数据模型 | index + content + expanded 状态 |
| **代码** | 结果语法高亮缓存 | 32 条 WeakReference 缓存，避免重复正则解析 |
| **代码** | XPath 引擎增强 | 新增 `[last()>1]` 比较、`[position()=last()]`、`[normalize-space(@attr)]`、隐式轴 `//` 中间步骤解析 |
| **代码** | 表达式缓存 | LinkedHashMap 缓存最近 16 个已解析 XPath 步骤 |
| **通知** | `NotificationHelper` | 创建通知渠道，为定时任务做准备 |
| **资源** | strings.xml 大幅扩充 | 新增 80+ 国际化字符串资源 |
| **资源** | 新增 `activity_xpath_rules.xml` | XPath 规则界面布局 |
| **资源** | 新增 `item_header_entry.xml` | 自定义 Header 条目布局 |
| **脚本** | `fix_main.py` | Python 修复脚本 |

---

## 技术架构

### 技术栈

| 层 | 技术选型 |
|---|---|
| 语言 | Kotlin 100% |
| 架构 | MVVM（ViewModel + LiveData + Repository） |
| UI | Material 3 (Material Design Components) + ViewBinding |
| 网络 | OkHttp 4.12.0（自定义拦截器，自动重定向，代理支持） |
| HTML 解析 | Jsoup 1.17.2 |
| XPath 求值 | **自研 JsoupXPathEngine**（~700 行，表达式缓存） |
| JS 渲染 | WebView（协程封装，超时保护） |
| 图片加载 | Coil 2.6.0 |
| JSON 处理 | Gson 2.10.1 + org.json |
| 数据持久化 | SharedPreferences（JSON 序列化） |
| 最低支持 | Android 5.0 (API 21) |
| 目标 SDK | Android 14 (API 34) |
| 编译 SDK | Android 16 (API 36) |
| 构建 | Gradle 9.0+ / Kotlin 2.1+ |

### 项目结构

```
CrawlerTool/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── xpath_rules.md                          # 内置 XPath 使用指南（离线文档）
│   │   ├── kotlin/com/SoloSu/Crawler_tool/
│   │   │   ├── BaseActivity.kt                          # [新] Activity 基类，统一主题加载
│   │   │   ├── MainActivity.kt                          # 主界面：UI 交互 + ViewModel 绑定
│   │   │   ├── SettingsActivity.kt                      # [增强] 设置页：UA/超时/主题/代理/Header/JS渲染
│   │   │   ├── XPathRulesActivity.kt                    # [新] XPath 规则手册（离线 Markdown 渲染）
│   │   │   ├── CrawlerEngine.kt                         # [增强] 爬虫引擎：三种匹配 + 延迟/Cookie/代理/统计/FetchStats
│   │   │   ├── JsoupXPathEngine.kt                      # [增强] 自研 XPath 引擎（~700行，表达式缓存，增强谓词）
│   │   │   ├── JsRenderEngine.kt                        # [新] JS 渲染引擎（WebView + 协程）
│   │   │   ├── HeaderManager.kt                         # [新] HTTP 请求头 & Cookie 管理器
│   │   │   ├── HistoryManager.kt                        # [新] 历史记录 & 收藏管理器（100条自动保存）
│   │   │   ├── NotificationHelper.kt                    # [新] 通知渠道辅助工具
│   │   │   ├── HtmlTreeDialog.kt                        # [增强] HTML 树形结构对话框（搜索/缩放/展开到层/标签色扩展）
│   │   │   ├── ResultAdapter.kt                         # [增强] 结果列表适配器（高亮缓存/属性标签/展开折叠）
│   │   │   ├── ResultItem.kt                            # [新] 数据模型（index + content + expanded）
│   │   │   ├── HtmlUtil.kt                              # HTML 语法高亮工具
│   │   │   ├── repository/
│   │   │   │   ├── SettingsRepository.kt                # [新] 设置数据持久化层
│   │   │   │   ├── CrawlerRepository.kt                 # [新] 爬虫数据仓库
│   │   │   │   └── HistoryRepository.kt                 # [新] 历史记录仓库
│   │   │   ├── viewmodel/
│   │   │   │   ├── MainViewModel.kt                     # [新] 主界面 ViewModel（MVVM 状态管理）
│   │   │   │   └── SettingsViewModel.kt                 # [新] 设置页 ViewModel
│   │   │   └── util/                                    # [新] 工具目录（预留）
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml                    # [增强] 主界面布局
│   │   │   │   ├── activity_settings.xml                # [增强] 设置布局（Header/代理/JS渲染/延迟/重试）
│   │   │   │   ├── activity_xpath_rules.xml             # [新] XPath 规则手册布局
│   │   │   │   ├── item_header_entry.xml                # [新] 内联 Header 条目布局
│   │   │   │   └── item_result.xml                      # 结果卡片布局
│   │   │   ├── anim/
│   │   │   │   ├── fade_in.xml
│   │   │   │   └── fade_out.xml
│   │   │   ├── drawable/                                # 自定义图形
│   │   │   ├── drawable-night/                           # 深色模式专属图形
│   │   │   ├── values/                                  # 主题配色（含 Dynamic Color）
│   │   │   ├── values-night/                            # 夜间主题配色
│   │   │   └── menu/
│   │   │       └── toolbar_menu.xml                     # 工具栏菜单（历史/导出/结构/规则/设置/关于）
│   │   └── AndroidManifest.xml                          # [增强] 权限声明（INTERNET/存储/通知）
│   └── build.gradle                                     # versionCode 6, versionName 1.3
├── build.gradle                                         # 项目级构建配置
├── settings.gradle                                      # 项目设置
└── gradle.properties                                    # Gradle 属性
```

### 核心模块详解

#### JsoupXPathEngine — 自研 XPath 引擎（v1.3 增强）

不依赖 `javax.xml.xpath`，直接在 Jsoup DOM 树上求值。新增特性：

- **表达式缓存**：LinkedHashMap 缓存最近 16 个已解析步骤
- **[last() 比较]**：`[last()>1]`、`[last()<3]` 等与上下文大小的比较
- **[position()=last()]**：匹配最后一个元素
- **[normalize-space(@attr)]**：属性值归一化后判断非空 / 相等
- **隐式轴解析**：`//` 中间步骤正确解析为 `descendant-or-self::node()`
- **增强标签颜色**：表单元素（青绿）、文本标签（紫）、脚本（灰）

#### CrawlerEngine — 爬虫引擎（v1.3 增强）

- **FetchStats 统计**：记录请求数、成功/失败数、耗时
- **JS 渲染入口**：`fetchAndMatchWithJs()` 使用 WebView 渲染后匹配
- **延迟请求支持**：`delayMs` 参数控制请求间隔
- **自定义 Header 注入**：通过 `HeaderManager.buildHeaderMap()` 注入
- **代理支持**：通过 `SettingsRepository.getProxySettings()` 配置

#### BaseActivity — 全新基类

统一管理主题加载逻辑：

```kotlin
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()  // 在 super.onCreate 之前调用
        super.onCreate(savedInstanceState)
    }
}
```

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

各模式下快捷芯片自适应显示：

- **XPath 模式**：`@title` `@href` `@data-original` `@data-src` `text()`
- **CSS 模式**：`[title]` `[href]` `[data-original]` `[data-src]` `:contains(文本)`
- **正则模式**：`[^<]+?` `https?://...` `[^"']+` `[^"']+` `.*?`

### 批量抓取

1. 点击「批量」按钮展开批量区域
2. 输入 URL 模板，用 `{page}` 替代页码
3. 设置起始和结束页码
4. 点击「开始批量抓取」→ 自动遍历所有页面
5. 结果自动合并，支持导出

### 历史记录

- 每次匹配成功自动保存
- 菜单栏 → 历史记录，显示最近记录
- 点击条目自动回填 URL、表达式和模式
- 最多保存 100 条，自动去重

### 自定义请求头

1. 设置页 → 自定义请求头 → 开启开关
2. 点击「添加请求头」输入 Key/Value
3. 支持编辑和删除，自动保存

### JS 渲染模式

对于 SPA 或动态加载页面：

1. 设置页 → 开启 JavaScript 渲染
2. 设置等待时间（1~10 秒）
3. 返回主界面正常匹配 → 引擎会用 WebView 加载并等待渲染

### 导出结果

菜单栏 → 导出 → 选择格式（JSON / CSV / 纯文本）
导出位置：`/storage/emulated/0/Crawler-Tool/`

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
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.9.0'
    implementation 'org.jsoup:jsoup:1.17.2'              // HTML 解析
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'   // 网络请求
    implementation 'io.coil-kt:coil:2.6.0'                // 图片加载
}
```

---

## 版本历史

| 版本 | 新特性 |
|------|--------|
| **v1.3** | MVVM 架构重构、BaseActivity 基类、历史记录、自定义请求头(HeaderManager)、代理设置、JS 渲染引擎(JsRenderEngine)、XPath 规则手册(XPathRulesActivity)、批量延迟 & 重试、UA 快捷预设、增强树控件(搜索/展开到层/缩放按钮)、MediaStore 导出兼容、通知渠道、结果语法高亮缓存、XPath 引擎增强(表达式缓存+谓词扩展)、strings.xml 扩充80+ |
| **v1.2** | 设置页面、主题系统、批量抓取、多格式导出、CSS/正则模式 |
| **v1.1** | HTML 树形结构查看器、XPath 引擎增强、语法高亮 |
| **v1.0** | 基础 XPath 匹配、结果展示 |

---

## 关于

**作者：** SoloSu  
**技术栈：** Kotlin + Material 3 + MVVM + Jsoup + OkHttp + Coil  
**最低支持：** Android 5.0 (API 21)  
**目标 SDK：** Android 14 (API 34)  
**当前版本：** v1.3 (build 6)

