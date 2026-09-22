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
```

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
