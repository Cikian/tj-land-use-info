# 详情页改版 · 验证样例（perf/detail-redesign）

本目录是**详情页改版的离线验证样例**，不是运行时代码，不会被应用打包或引用。
它用项目自带的 `less` + `vue-template-compiler` 把组件的 `<style scoped>` **真实编译**出来，
再按 vue-loader 的规则补上 `[data-v-xxx]`，然后无头浏览器截图逐项核对行高 / 分栏 / 分隔线。
这样量出来的是「生产等效」的版面，而不是凭感觉。

## 文件

| 文件 | 用途 |
| --- | --- |
| `compare.html` / `compare.png` | 前后对比总览（原始版面 / 中途出错的版面 / 改后） |
| `user-original.png` | 第一版改前的提级论证项目详情（用户提供截图，只读留档） |
| `user-broken.png` | **中途出错**的档案详情（用户反馈的「更混乱」那一版，只读留档） |
| `final-escalation.html` / `.png` | 改后的提级论证项目详情 |
| `final-archive.html` / `.png` | 改后的档案详情 · 基本信息 |
| `regression-variants.html` / `.png` | `ScreenDescriptions` 五种组合回归对比 |
| `generate-protos.js` | 生成上面两个整页样例（真实 scoped CSS） |
| `render-sfc.js` | 生成回归对比页（真实 scoped CSS） |
| `screenshot.ps1` | 出图脚本（校验尺寸并按需重试，见下方「出图陷阱」） |
| `verify-templates.js` | 编译改动过的 SFC 模板，确认 Vue 2 单根节点等约束 |
| **`measure.js` / `measure-*.png`** | **量版工具**：真实视口下 `getBoundingClientRect` 量每行的行高 / 标签宽 / 值宽 / 是否溢出，把数字画进页面再截图 |
| **`inspect-chunk.js`** | 从 **dev server 真编译出来的 chunk** 里抠出运行时代码做自检（证明改动真的编进去了） |
| **`shot-url.js`** | 给任意 URL（含真实 dev server）出图，用来截「跑起来的应用」 |
| `find-chunk.js` / `peek-chunk.js` | 在 dev server 的 533 个 chunk 里定位某个字符串 / 看上下文 |
| `app-live.png` | 真实 dev server（`http://127.0.0.1:3000/`）的截图；详情弹窗在登录之后，所以这里只有登录页，用来证明「应用能起来、改动没把它编挂」 |

## 怎么复跑

```bash
# 1) 模板 + 样式能不能编译（含关键规则自检）
node perf/detail-redesign/verify-templates.js

# 2) 用真实编译的 CSS 出整页样例 + 回归页
node perf/detail-redesign/generate-protos.js
node perf/detail-redesign/render-sfc.js

# 3) 出图（Windows 自带 Edge 即可，不必装 puppeteer）
pwsh -NoProfile -File perf/detail-redesign/screenshot.ps1

# 4) 量真实几何（行高 / 标签宽 / 值宽 / 是否被省略号截断）
node perf/detail-redesign/measure.js --width 1912 --height 1250

# 5) 证明改动已经编译进 dev server 的产物
node perf/detail-redesign/find-chunk.js --needle "--sd-label-max"
node perf/detail-redesign/inspect-chunk.js --chunk /0.js     # 应输出「与源码契约一致」

# 6) 截真实应用（登录页 / 任意可达路由）
node perf/detail-redesign/shot-url.js --url http://127.0.0.1:3000/ --out perf/detail-redesign/app-live.png
```

### 出图陷阱（本目录踩过）

- `--window-size` 会被 **偶发忽略**，输出被缩成约 754×487（等于按默认窗口出图），
  且尺寸不报错、只体现为「图变小 + 响应式降列」。所以 `screenshot.ps1`
  对每张图校验目标尺寸，不符合就换 `--headless` / `--headless=new`
  与全新 `--user-data-dir` 重试。
- **不要用 `--force-device-scale-factor=2` 去放大**：它会把 CSS 视口宽度砍半，
  触发 `--sd-cols` 的响应式降列，量出来就不是目标宽度下的版面了。
- 复用同一个 `--user-data-dir` 容易拿到被缩放的结果，每次都用新的临时目录。
- **`--dump-dom` 在这台机器上拿不到任何输出**（实测 stdout / 重定向都是 0 字节）。
  Edge 是「提权启动 → 自己再拉起降权子进程」的模式，父进程立刻退出，
  子进程的 stdout 接不回来。所以量版改成「把数字画进页面再截图」。
- 删临时 profile 会偶发 `EBUSY`（降权子进程还占着 `Session Storage/LOCK`）。
  删不掉就留着，别让它把出图判断带崩。
- **`<script>` 正文里不能出现闭合标签的字面量**（哪怕写在 JS 字符串里），
  浏览器会当场结束脚本，后面的代码全变成正文 —— 现象是整段函数源码被
  `window.onerror` 当错误信息打出来。用拼接避开。

## 版面契约（改 ScreenDescriptions 前必读）

- **外层 Grid**：把宽度均分成 `--sd-cols` 条**等宽**轨道（`minmax(0, 1fr)`）。
- **单元格**：每条轨道一个 `.screen-descriptions__item`，内部再切两轨
  `auto minmax(0, 1fr)` = 「标签（内容宽，封顶 `--sd-label-max`） | 值（剩余全部）」。
  底线画在**单元格**上，所以值折行把单元格撑高时分隔线仍在行底。
- **对齐靠 `align-items: baseline`**：标签与值字号相同、`line-height` 相同
  （`--sd-line-h`），基线对齐才真的是「标签和值在同一条线上」。
  用 `stretch` 时两者各自垂直居中，字高不同就会看出错位。
- **行高是常量**：值默认 `white-space: nowrap` + 省略号，
  所以每个单元永远一行；需要通读的长文本用 `item.stack` 独占整行（唯一会变高的条目）。

### 四条已经踩过的坑，不要再回退

1. **标签列用定宽**（旧实现 108px / 112px）
   3 字标签后面拖一大段空白 → 用户说的「文字太分散」；标签一长就被 nowrap 截断。
2. **标签列用 `max-content` 不封顶**
   长标签（如「材料数 / 意见记录数」）能把单元格吃满，值列只剩十几像素，
   值逐个字换行、行高翻倍 —— 这正是 `user-broken.png` 之后用户又反馈的那一版。
   必须 `min(内容宽, 封顶)`，而封顶只能靠 `max-width: var(--sd-label-max)` 写在
   内层 `.screen-descriptions__label-text` 上：写在轨道尺寸上做不到，
   `minmax()` 的下限就是 min-content，中文一列只有 1 个字宽。
3. **外层切「标签轨 + 值轨」+ 单元格内嵌套 Grid**
   内层 `1fr` 会被压到 min-content，值列只剩几十像素、长文本逐字竖排。
4. **单元格 `display: contents`，`dt`/`dd` 直接落外层格子**
   依赖 `contents` 生效；一旦不生效（构建 / 内核 / 缓存差异），`dt`/`dd`
   退化成两个块级子元素，整页变成「标签一行、值一行」的超高版面。

### 两个 Less 语法坑（会静默改坏布局）

1. `grid-column: 1 / -1;` → Less 把 `1 /` 当除法，编译成 `grid-column: 1`。
   `-1 / -1` 同理被吃掉。**必须写成转义字符串**：
   ```less
   grid-column: ~'1 / -1';
   ```
   否则单元格只占 1 条轨道，还会多生成一条隐式轨道，把整行的等宽分布彻底打乱。
2. SFC 顶层的文档注释要写在 `<template>` **里面**。写在 `<template>` 之前时，
   `parseComponent` 会把注释里出现的示例标签（如 `<screen-descriptions ...>`）
   当成模板内容，报「Component template should contain exactly one root element」。

## 量出来的关键数字（1912px 视口，实测）

| 指标 | 改前 | 改后 |
| --- | --- | --- |
| 描述行高（单行） | 34px，且每格都有描边 + 标签淡底 | **29px**（18 行高 + 5×2 内边距 + 1 底线），且**不随内容变化** |
| 标签列 | 写死 108 / 112px，3 字标签后面全是空白 | `min(内容宽, 封顶)`；实测 24 / 36 / 48 / 60 / 72 / 96px，短标签零浪费 |
| 标签与值的间距 | 视字号与折行而定，看着忽远忽近 | 固定 8px（`--sd-pair-gap`），整块只有这一个间距 |
| 标签 / 值对齐 | 值列被挤成几十像素后折行 → 标签与值错位 | 同行同字号同 `line-height` + `align-items: baseline`，值起始位置逐行对齐 |
| 值换行 | 长标签把值列吃光 → 逐字竖排、行高翻倍 | 单行 + 省略号（溢出的才补 `title` 显示全文） |
| 短字段列数 | 4 列（每格 ~450px，标签就能吃掉 380px） | 档案属性 2 列；关联项目 / 其他信息 3 列（值宽实测 850 / 546px） |
| 长文本字段 | 和其它字段混在同一节奏里，把整行撑高 | `stack` 单元格独占整行、标签在上值在下，可折行通读 |
| 分组卡内边距 | 12 / 16 / 8px，块间距 12px | 8 / 12 / 4px，块间距 8px |

视口自适应（同一份代码实测，均无省略号截断）：

| 视口宽 | 每格值宽（2 列） | 行高 |
| --- | --- | --- |
| 1912 | 850px | 29px |
| 1440 | ~630px | 29px |
| 1180 | 484px | 29px |

## 验证口径提醒

写这类离线样例时，两件事必须对上，否则量出来的版面是假的：

1. 给模板元素补上 `[data-v-xxx]`（否则 scoped CSS 全部命中不了）；
2. 同一个元素上不要出现重复的 `class` / `style` 属性（浏览器会丢掉前一个，
   例如 `class="card" ... class="is-flat"` 会让 variant 静默失效）。

另外，「离线样例对了」不等于「应用里对了」：样例用的是**你刚写的**源码，
而浏览器跑的是 **dev server 编出来的产物**，两者之间还隔着 scoped id、
autoprefixer、css-loader 的字符串转义。所以改完务必再跑一次
`find-chunk.js` + `inspect-chunk.js`，确认改动真的进了 chunk。
