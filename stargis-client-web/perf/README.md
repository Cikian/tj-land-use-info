# 前端性能诊断工具

这里的脚本是「tianjin 土地利用」项目（`code/new/stargis-client-web`）性能优化时写的
一次性诊断工具，用**无头 Edge + Chrome DevTools Protocol** 采集真实浏览器数据，
不依赖任何额外 npm 依赖（复用项目里已有的 `ws`）。

## 用法

先启动开发服务器（本项目 Node 18+ 需要 `--openssl-legacy-provider`）：

```bash
set NODE_OPTIONS=--openssl-legacy-provider
npm run serve
```

脚本默认连 `http://localhost:3002`，实际端口以 dev-server 打印的 `Local:` 为准。

```bash
# 1) 单个路由：请求数/传输量/长任务/CPU热点/帧间隔（最常用）
node perf/perf-probe3.js http://localhost:3002

# 2) 分阶段：登录页 → 目标路由 → 空转观察，看空闲时 CPU 是否被持续占用
node perf/perf-probe2.js http://localhost:3002 /map 15000

# 3) 最终验证：只看本次导航（禁用缓存），列出字体请求、分包数量、CSS 规则数
node perf/final-check.js http://localhost:3002 /user/login 12000

# 4) 统计样式模块重复编译情况（检查 less 是否被重复注入）
node perf/css-dup.js http://localhost:3002/app.js

# 5) 通用 CPU profile + 帧率 + 网络分布
node perf/perf-probe.js http://localhost:3002/ 10000 10000

# 6) 【大屏卡顿】自动登录后进任意大屏路由，做「停 Cesium / 关 backdrop-filter」A/B
node perf/perf-lag.js http://localhost:3100 /screen/archive 10000
node perf/perf-lag.js http://localhost:3100 "/screen/review?tab=query" 10000

# 7) 【大屏卡顿】用真实点击走 首页 → 档案管理 → 首页，断言地图暂停/恢复
node perf/verify-map-pause.js http://localhost:3100

# 8) 【不跟手】交互延迟：每个变体整页重新加载后跑同一串操作（事件延迟 + 帧间隔）
node perf/perf-input.js http://localhost:3000 /screen/archive

# 9) 【不跟手】逐个操作量「点一下要等多久」（同步阻塞 / 静下来 / 最大帧间隔 / 长任务）
node perf/perf-click-cost.js http://localhost:3000 /screen/archive /screen/review

# 10) 【不跟手】绘制/合成成本 A/B：现状 / 关 blur / 关 shadow / 都关 + 现状对照
#     第 4 个参数是 device-scale-factor（模拟 Windows 显示缩放）；
#     第 5 个参数 sw 走软件渲染（--disable-gpu），用来复现「浏览器没开硬件加速」那一档
node perf/perf-raster-ab.js http://localhost:3000 /screen/archive 1.5
node perf/perf-raster-ab.js http://localhost:3000 /screen/archive 1.5 sw
```

## ⚠ 排查这类问题前，先确认浏览器有没有开硬件加速

这个项目连着踩了两次同一个坑：**用「有硬件加速」的探针环境去量「没有硬件加速」的现场**，
结果两次都只找到一半原因。

```powershell
# Edge 有没有关掉硬件加速（enabled:false 就是关了）
Select-String -Path "$env:LOCALAPPDATA\Microsoft\Edge\User Data\Local State" -Pattern 'hardware_acceleration_mode'
```

```js
// 或者直接在出问题的浏览器控制台里跑：打印出来是 SwiftShader / WARP / Basic Render
const c = document.createElement('canvas'); const gl = c.getContext('webgl')
const e = gl.getExtension('WEBGL_debug_renderer_info')
console.log(gl.getParameter(e.UNMASKED_RENDERER_WEBGL))
```

浏览器一旦没开加速（或集显、云桌面、驱动被拉黑）：

- 合成与光栅化全部落到 CPU，`backdrop-filter` 的代价被放大 **20 倍以上**（见下面第二轮实测）；
- Cesium 的 WebGL 走软件光栅化（WARP），一个渲染循环就能吃掉十几个核，
  整机 CPU 打满之后**所有**操作都滞后，跟前端代码没什么关系。

`perf-lag.js` / `verify-map-pause.js` / `perf-raster-ab.js` 里那些
「浏览器没开加速」的变体就是为这一档准备的。

`perf-lag.js` / `verify-map-pause.js` 会自己登录（默认 `admin` / `123456`，
可用环境变量 `PERF_USER` / `PERF_PWD` 覆盖），**必须连到已经编译完当前源码的
dev-server**：源码改了要先等它 recompile，否则量到的是旧构建。

⚠ `verify-map-pause.js` 触发的是 `.screen-header__nav-item` 上的**真实点击**，
不是直接改 hash —— `/`、`/screen/archive`、`/screen/review` 共用同一个组件实例，
只改 hash 属于同文档导航，组件不会重建，`activeMenu` 会一直停在首页，
量出来的会是首页而不是目标页。

脚本会把 headless Edge 的 profile 建在系统临时目录，退出时用 `taskkill /T /F`
连子进程一起清理。如果中途 Ctrl+C，可能残留浏览器进程，可用下面命令清理：

```powershell
Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" |
  Where-Object { $_.CommandLine -match 'edge-(perf|font|final|why)' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

## 各脚本读什么指标

| 指标 | 含义 / 判读 |
| --- | --- |
| 请求数、传输 MB | 首屏总代价；dev 态重点看有没有 300+ 个 prefetch 小请求 |
| 单请求 TOP | 揪出「一个文件吃掉几十 MB」的元凶（source map、字体、Cesium） |
| DOMContentLoaded / FCP | 首屏可见时间 |
| Long Task (>50ms) | 主线程被阻塞的时段，卡顿的直接证据 |
| TaskDuration / ScriptDuration | 观察窗口内 CPU 忙的比例；空转应接近 0% |
| rAF 帧间隔 | 帧率；平均间隔长期 >33ms 即掉帧 |
| CSS 规则数 / 样式表数 | 判断 less 是否被重复注入（正常应远小于 1 万条） |
| css-dup.js | 同一 less/css 模块被编译成几份，正常必须是 1 份 |
| 事件循环延迟（setTimeout 链） | 「点了没反应」的直接指标；主线程被占住时用户的点击/输入回调就要排这么久（Chromium 对嵌套 setTimeout 有 4ms 地板） |
| rAF 帧间隔 p99 | 掉帧的直接证据；长期远离 p50 说明有帧被主线程拖长 |
| 进程 CPU（GPU / renderer） | `SystemInfo.getProcessInfo`（browser 级 CDP）——判断开销落在主线程还是合成/GPU |

## 优化前后实测（禁用缓存，登录页首屏）

| 指标 | 优化前 | 优化后 |
| --- | --- | --- |
| 请求数 | 368 | 65 |
| 传输量 | 156.8 MB | 54.5 MB |
| `app.js` | 88.9 MB | 37.8 MB |
| 字体下载 | 15.65 MB（sy.otf） | 无 |
| 首个 Long Task | 788 ms | — |
| DOMContentLoaded | 3926 ms | 2115 ms |

> 生产构建产物里仍保留 `stargis-function` 包自带的 `sy.2748ff40.otf` 文件
> （约 15.65 MB），但已确认浏览器不会再请求它；如需进一步瘦身，可在打包后
> 直接删除 `dist/stargisWebGL/fonts/` 下未被引用的字体文件。

---

## 第二轮：大屏子页面「整个系统都卡顿，所有操作都滞后」

### 症状

首页 / 档案管理 / 提级论证管理整站发卡，其中档案管理与提级论证管理
「所有操作都滞后」（输入、切页签、翻页都要等一下）。

### 结论：Cesium 的默认渲染循环在这两个整页模块**后面**一直按 60fps 重绘整屏

`views/maps/S3dmViewer.vue` 里 `new Cesium.Viewer()` 用的是 Cesium 默认的
`useDefaultRenderLoop = true` —— 只要 viewer 存在就无条件每帧 `render()`，
不管镜头有没有动、画面有没有被人看见。

而 `views/screen/index.vue` 的地图层 `<s3dm-viewer v-if="mapEnabled">` 与顶栏菜单
**无关**：档案管理 / 提级论证管理这两个整页模块（`.land-screen__module { inset: 0 }`
把 1920×1080 画布整个盖住，地图只从面板之间 12px 的缝里露出一点）挂上来以后，
Cesium 仍然在按 60fps 重绘全屏。渲染进程的主线程是整页共用的，于是用户在这个
页面上点的每一下都要排在 Cesium 的渲染片后面。

实测 `/screen/archive`（1920×1080，headless Edge + 真实 GPU，`perf/perf-lag.js`）：

| 指标 | 现状（Cesium 渲染循环开着） | 停掉渲染循环 | 只关 backdrop-filter |
| --- | --- | --- | --- |
| 主线程 TaskDuration | **44.8%** | 4.1% | 45.8% |
| 其中 ScriptDuration | **39.5%** | 0.6% | 39.3% |
| Long Task (>50ms) | 最长 **107ms** | 无 | 最长 102ms |
| rAF 帧间隔 p99 | **18.2ms** | 6.7ms | 18.2ms |
| 事件循环延迟 p90 | **8.9ms** | 6.9ms | 7.7ms |

两点结论：

1. **45% 全部来自 Cesium**，与业务数据量、表格行数、DOM 规模无关
   （档案管理页 DOM 只有 1136 个节点、样式重算/布局增量都是 0）。
   停掉渲染循环后主线程 45% → 2%，Long Task 消失，帧间隔 p99 回到 6.3ms。
2. **`backdrop-filter: blur(12px)` 不是瓶颈**：实测该页面只有 3 个模糊元素
   （左侧栏 + 两个面板），关掉它主线程占用不变（44.8% ↔ 45.8%，在噪声内）。
   所以玻璃质感按设计稿保留，不用为了性能牺牲视觉。

### 修复

| 文件 | 改动 |
| --- | --- |
| `src/views/maps/S3dmViewer.vue` | 新增 `paused` prop（默认 `false`，既有调用方行为不变）+ `applyRenderLoop()`；`mounted` 与 `paused` 变化时把 `viewer.useDefaultRenderLoop` 同步成 `!paused`，`beforeDestroy` 时停掉 |
| `src/views/screen/index.vue` | 新增 `mapPaused` 计算属性，`<s3dm-viewer :paused="mapPaused">` |

`mapPaused` 的判据只有一条：**地图此刻是否被人看见**。
成立的有两种情况 —— 档案管理 / 提级论证管理这两个整页模块，
以及首页那个全画布的地块预警弹窗（`HomePlotModal` 也是 `inset: 0` 遮罩）。
注意不能写成「非 home 即暂停」：顶栏还有若干「待接入」菜单，点它们时
`activeMenu` 会变成那个 key，但页面回落到首页版式、地图是可见的。

### 修复后实测（`perf/verify-map-pause.js`，真实点击顶栏走一遍）

```
首页（地图应当渲染）    useDefaultRenderLoop=true   主线程忙 33.7%   改镜头→画面变了
档案管理（应被暂停）    useDefaultRenderLoop=false  主线程忙  0%     改镜头→画面没变（已冻结）
回到首页（应恢复）      useDefaultRenderLoop=true   主线程忙 23.8%   改镜头→画面变了
通过 6/6
```

`/screen/archive` 深链进入（挂载即暂停）复测：

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 主线程 TaskDuration | 44.8% | **2.0%** |
| ScriptDuration | 39.5% | 0.2% |
| Long Task (>50ms) | 最长 107ms | 无 |
| rAF 帧间隔 p99 | 18.2ms | 6.3ms |
| 事件循环延迟 p90 / max | 8.9ms / 19ms | 5.5ms / 6.1ms |
| `useDefaultRenderLoop` | true | false |

提级论证管理（页面最重的一个页签：13 个 panel、14 个 backdrop-filter 元素、
DOM 1329 节点）修复后同样是：主线程忙 **2.3%**、Long Task 无、
事件循环延迟 p90 **6.0ms**、帧间隔 p99 **6.2ms**。
这同时再次说明模糊层数翻了几倍也没带来主线程开销 —— 瓶颈自始至终只有 Cesium。

### 还剩什么

首页地图本身仍然占主线程 24~45%（镜头不动时也在渲染，这是 Cesium 默认行为）。
要进一步降，只能二选一，都需要产品决策，本轮没有动：

- `viewer.scene.requestRenderMode = true`：Cesium 官方的「按需渲染」，
  省得最彻底，但要求**所有**改场景的代码路径都记得 `scene.requestRender()`，
  否则会出现「图例/图层改了地图不刷新」——本项目 `views/maps/**` 那套
  旧地图交互很多，风险不小。
- 自己驱动渲染循环并把帧率压到 30/20fps：`useDefaultRenderLoop = false`
  之后自己做 `viewer.resize(); viewer.render();` 的限频 rAF 循环，
  没有正确性风险，但地图手感会变糙。

---

## 第三轮：「操作不跟手」（第二轮修完之后用户仍反馈卡）

### 为什么第二轮没修好：**量错了指标，也量错了环境**

- **指标错**：第二轮量的是「空转时主线程占用」，那是「卡」的证据，不是「不跟手」的证据。
  不跟手是**事件处理到下一帧绘制**的延迟。补上 `perf-input.js` /
  `perf-click-cost.js` 之后才看清：所有点击的**同步阻塞都是 0ms**（JS 完全没卡），
  但**画面最大帧间隔**高达 90~270ms —— 主线程空闲却没有帧产出，瓶颈在 paint/raster/composite。
- **环境错**：探针都用 `--enable-gpu --use-angle=d3d11` 跑，走的是真实 GPU；
  而现场那台机器的 Edge **关掉了硬件加速**：

  ```
  Edge\User Data\Local State
  "hardware_acceleration_mode": {"enabled": false}
  ```

  于是它的 GPU 进程是 `--use-angle=d3d11-warp-webgl`（WARP 软件光栅化），
  实测**持续占用 12.7 个 CPU 核心**（20 核机器整机 75%+）。
  有硬件加速时由 GPU 吸收的开销，在软件合成下会被放大到几十倍 ——
  这解释了「为什么开发机量不出来、现场却卡成那样」。

教训写进上面那一节了：**先确认浏览器有没有开硬件加速，再谈代码。**

### 关键数据：软件渲染下 `backdrop-filter` 的代价

`perf-raster-ab.js`，1920×1080 + `--force-device-scale-factor=1.5`（模拟 Windows 150% 缩放），
**软件渲染**（`sw` 参数），每个变体整页重新加载后跑同一串操作：

| 变体 | 悬停帧间隔 p50 | 悬停掉帧(>50ms) | 滚动 | 切页签 | 打开弹窗 |
| --- | --- | --- | --- | --- | --- |
| 现状（有 blur） | **116ms** | **28 帧** | 267ms | 267ms | 267ms |
| 关 backdrop-filter | **16.7ms** | **0 帧** | 17ms | 33ms | 50ms |
| 只关 box-shadow | 83ms | 29 帧 | 250ms | 267ms | 300ms |
| 都关 | 16.7ms | 0 帧 | 17ms | 33ms | 67ms |
| **现状对照（CSS 与现状完全相同）** | 117ms | 29 帧 | 283ms | 283ms | 283ms |

- 「悬停 p50 = 116ms」= 鼠标每移动一下要 116ms 才出一帧，就是「鼠标划过表格发涩」；
  滚动 / 切页签 / 开弹窗分别是 267ms，四项症状全对上。
- **最后一个「现状对照」是必须的**：它和第一个变体跑的是同一份 CSS。
  第一次跑这组 A/B 时没加对照，把 218ms→12ms 记成了 blur 的功劳，
  其实是「首次打开弹窗」的冷启动成本；加了对照才把两者分开。
- 只关 box-shadow 没有改善（还是 29 帧掉帧）→ **瓶颈就是 blur，不是阴影**。

为什么偏偏是档案管理和提级论证管理最卡：`backdrop-filter` 元素数
**首页 0 个 / 档案管理 3 个 / 提级论证管理 14 个**（首页那些面板用的是切图背景）。
全是整屏大小的大面板。

### 修复：去掉 UI 里的 `backdrop-filter`

| 文件 | 改动 |
| --- | --- |
| `components/screen/styles/screen-mixins.less` | `.screen-glass()` 不再输出 `backdrop-filter`（覆盖 ScreenPanel / DocSendPanel / DocReceivePanel / ArchiveStatistics），并写清原因与实测数据 |
| `components/screen/ScreenModal.vue` | 整屏遮罩去掉 `blur(2px)` |
| `views/screen/archive/index.vue`、`views/screen/escalation/index.vue` | 左侧二级导航去掉 blur |
| `ArchiveDetailModal.vue`、`EscalationDetailModal.vue`、`DocDetailModal.vue` | 弹窗内卡片去掉 blur |

**为什么敢去掉**：面板底色本来就是 `--screen-panel-bg` = `rgba(8,22,42,0.92)`，
92% 不透明，背后那点模糊几乎全被盖住。逐像素比对（同一页面同一状态、
硬件加速、150% 缩放，去掉前 vs 去掉后）：

```
平均差 0.046/255   p99 1.00/255   差异 >2/255 的像素 0.043%   差异 >16/255 的像素 0.013%
```

**画面看不出区别，卡顿差二十倍。**

### 修复后复测（软件渲染，`sw`）

| 阶段 | 修复前 | 修复后 |
| --- | --- | --- |
| 悬停 帧间隔 p50 / 掉帧 | 116ms / 28 帧 | **16.7ms / 0 帧** |
| 滚动 max | 266ms | 33ms |
| 切页签 max | 266ms | 83~166ms |
| 打开弹窗 max | 266ms | 100~200ms（仅首次，含组件冷挂载） |

剩下的是「首次打开某个弹窗 / 首次切到某个重面板」的一次性成本，
所有 CSS 变体都一样，与 blur 无关。

### 现场还需要做的一件事

代码修复只能把应用侧的开销压掉，**浏览器自身的硬件加速必须打开**，
否则 Cesium 的 WebGL 仍然在 WARP 上软件渲染（首页地图会非常慢，且吃满 CPU）：

1. `edge://settings/system` → 打开「使用硬件加速(如可用)」→ 重启 Edge；
2. `edge://gpu` 确认 WebGL / Compositing 是 `Hardware accelerated` 而不是 `Software only`；
3. 顺手关掉那些在修复前打开、还挂着旧代码的大屏标签页（它们仍在后台满速渲染）。

