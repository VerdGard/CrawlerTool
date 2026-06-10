# XPath 表达式 使用指南

XPath 是一种在 XML/HTML 文档中查找信息的语言。本工具先将 HTML 转为 XHTML 后使用 XPath 解析。

## 基本路径表达式

| 表达式 | 说明 | 示例 |
|--------|------|------|
| `nodename` | 选取节点 | `div` 选取所有 div 节点 |
| `/` | 从根节点选取 | `/html/body` |
| `//` | 从任意位置选取 | `//a` 选取所有 a 标签 |
| `.` | 当前节点 | — |
| `..` | 父节点 | — |
| `@` | 选取属性 | `//a/@href` 获取所有链接地址 |

## 谓语（条件过滤）

| 表达式 | 说明 |
|--------|------|
| `//div[@class]` | 选取所有带 class 属性的 div |
| `//div[@class="title"]` | 选取 class 精确等于 title 的 div |
| `//div[@class!='bar']` | class 不等于 bar 的 div |
| `//div[contains(@class, "title")]` | class 包含 title 的 div |
| `//a[position()=1]` | 第一个 a 标签 |
| `//a[position()<3]` | 前两个 a 标签 |
| `//a[position()]` | 所有 a 标签（[position()] 恒为 true） |
| `//ul/li[last()]` | 最后一个 li |
| `//li[2]` | 简写：第二个 li |
| `//*[@id="main"]` | id 为 main 的任何元素 |
| `//div[@class='a' and @id='b']` | 多个条件同时满足（and） |
| `//div[@class='a' or @class='b']` | 满足任一条件（or） |
| `//div[not(@class='hidden')]` | class 不等于 hidden 的 div |
| `//a[not(contains(@class,'disabled'))]` | class 不包含 disabled 的链接 |
| `[text()='标题']`, `[contains(text(),'关键')]`, `[text()]` | 文本内容过滤（[text()] 选中非空文本节点） |
| `[@class='row' and position()=2]` 等价于 `[@class='row'][2]` | 两写法等价 |
| `child::div`, `descendant::span` | 显式轴前缀 |

## 常用函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `text()` | 获取文本内容 | `//a/text()` |
| `contains(a,b)` | 包含判断 | `//div[contains(@class, "list")]` |
| `starts-with(a,b)` | 开头判断 | `//a[starts-with(@href, "https")]` |
| `normalize-space()` | 去除空格 | `//a[normalize-space(text())="链接"]` |
| `count()` | 计数 | `count(//a)` |

## 实际案例

```html
<div id="main" class="container">
  <ul class="list">
    <li><a href="/page/1" title="页面1">链接1</a></li>
    <li><a href="/page/2" title="页面2">链接2</a></li>
  </ul>
</div>
```

常用提取规则：
- `//a` → 选取所有 a 标签
- `//a/@href` → 提取所有链接地址
- `//a/text()` → 提取所有链接文本
- `//a/@title` → 提取所有 title
- `//div[@class="container"]//a` → 容器内的所有链接
- `//ul/li/a` → 列表中的链接
- `//a[starts-with(@href, "/detail")]` → 以 /detail 开头的链接
- `//div[@class='list']/div[@class='row'][2]` → 第二个 row 行
- `//div[@class='list']/div[not(@class='hidden')]` → 排除隐藏行
- `//ul/li[position()]` → 所有 li（含文本的）
- `//div[@class='item' and position()<4]` → 前 3 个 item
- `child::div` 等价于 `/div`, `descendant::a` 等价于 `//a`

## 快捷标签对应

- `@title` → `//*[@title]/@title`
- `@href` → `//a/@href`
- `@data-original` → `//*[@data-original]/@data-original`
- `@data-src` → `//*[@data-src]/@data-src`
- `text()` → `//*/text()`

> 提示：XPath 模式下，HTML 会被转为 XML 再解析，某些不标准的 HTML 可能导致路径匹配失败。建议先用 Jsoup 模式试试。
