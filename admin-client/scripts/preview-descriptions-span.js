/* global Atomics, SharedArrayBuffer */
/**
 * 复现 / 验证 antd Descriptions 的告警：
 *   [antdv: Descriptions] Sum of column `span` in a line exceeds `column` of Descriptions.
 *
 * 为什么要在**真浏览器**里跑而不是靠读源码推断：
 *   ① 告警只在「span 之和 > column」时出现，而 column 写成响应式对象时，
 *      getColumn() 在 screens 为空的**首次渲染**会兜底返回 3；
 *   ② responsiveObserve 依赖 matchMedia，必须在真实视口里才有断点；
 *   ③ antd 的 warningOnce 按**消息文本**去重，同一页面里同一个消息只会报一次。
 *
 * 所以每个用例都放进**独立的 iframe**：
 *   各自加载一份 antd（各自的 warned 去重表）、各自有独立视口（matchMedia 按 iframe 宽度算断点）。
 *   子页面用 postMessage 把结果抛给父页面（file:// 的 iframe 是不透明源，父页面读不了它的变量）。
 *
 * 用法：node scripts/preview-descriptions-span.js
 *      结果图输出到 %TEMP%\descriptions-span.png
 */
const fs = require('fs')
const path = require('path')
const os = require('os')
const { execFileSync } = require('child_process')

const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const antdCss = path.resolve('node_modules/ant-design-vue/dist/antd.css')
const vueJs = path.resolve('node_modules/vue/dist/vue.js')
const antdJs = path.resolve('node_modules/ant-design-vue/dist/antd.js')

const fileUrl = p => 'file:///' + p.replace(/\\/g, '/')

/**
 * 用例字段：
 *   column     —— 直接是 JS 源码片段（对象字面量或数字）
 *   lead       —— 前面几个不带 span 的普通项
 *   midSpan    —— 中间一项的 span（null 表示没有这一项）——对应「办结说明」
 *   lastSpan   —— 最后一项的 span（null 表示不写 :span）——对应「备注」
 *   expectWarning —— 预期是否出现告警
 */
const CASES = [
  {
    id: 'A',
    title: 'DocDetailModal 改前（原样）',
    iframeWidth: 1280,
    column: '{ xxl: 2, xl: 2, lg: 2, md: 2, sm: 1, xs: 1 }',
    lead: 8,
    midSpan: 2,
    lastSpan: 2,
    expectWarning: true,
    note: '响应式对象：首次渲染 column 兜底成 3，中间项 3-1-1-2 = -1 溢出 → 告警'
  },
  {
    id: 'B',
    title: 'DocDetailModal 仅把 column 改成数字 2',
    iframeWidth: 1280,
    column: '2',
    lead: 8,
    midSpan: 2,
    lastSpan: 2,
    expectWarning: false,
    note: 'column 固定 2 后首次渲染就是 2，中间项 2-1-1-2 不溢出'
  },
  {
    id: 'C',
    title: 'DocDetailModal 最终版（column=2 且末项不写 span）',
    iframeWidth: 1280,
    column: '2',
    lead: 8,
    midSpan: 2,
    lastSpan: null,
    expectWarning: false,
    note: '末项不写 :span，其余不变 —— 渲染与 B 完全一致，告警同样没有'
  },
  {
    id: 'D',
    title: 'ArchiveDetail 属性 改前 @1280',
    iframeWidth: 1280,
    column: '{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }',
    lead: 9,
    midSpan: null,
    lastSpan: 3,
    expectWarning: false,
    note: '1280 命中 xl=3，末项 leftSpans 恰好=3 → 3-3=0，不告警（所以之前一直没被发现）'
  },
  {
    id: 'E',
    title: 'ArchiveDetail 属性 改前 @1000',
    iframeWidth: 1000,
    column: '{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }',
    lead: 9,
    midSpan: null,
    lastSpan: 3,
    expectWarning: true,
    note: '1000 命中 lg=2，末项 leftSpans=1 却按 3 记账 → 1-3 = -2 溢出 → 告警（窗口一窄就报）'
  },
  {
    id: 'F',
    title: 'ArchiveDetail 属性 最终版（保留响应式，末项不写 span）@1000',
    iframeWidth: 1000,
    column: '{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }',
    lead: 9,
    midSpan: null,
    lastSpan: null,
    expectWarning: false,
    note: '只去掉末项的 :span —— 渲染与 E 逐像素相同（末项 span 本来就会被 antd 改写），告警消失'
  },
  {
    id: 'G',
    title: '对照：响应式 column 且全程无 span @1000',
    iframeWidth: 1000,
    column: '{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }',
    lead: 9,
    midSpan: null,
    lastSpan: null,
    expectWarning: false,
    note: '没有任何 span>1 时响应式 column 本来就安全，「关联项目」表属于这种'
  }
]

/** 生成单个用例的 iframe 页面 */
function caseHtml (c) {
  const items = []
  for (let n = 1; n <= c.lead; n++) {
    items.push(`    <a-descriptions-item label="字段${n}">值 ${n}</a-descriptions-item>`)
  }
  if (c.midSpan !== null && c.midSpan !== undefined) {
    items.push(`    <a-descriptions-item label="办结说明" :span="${c.midSpan}">这是一段会占满整行的较长办结说明文本</a-descriptions-item>`)
  }
  items.push(c.lastSpan === null || c.lastSpan === undefined
    ? '    <a-descriptions-item label="备注">这是一段会占满整行的较长备注文本</a-descriptions-item>'
    : `    <a-descriptions-item label="备注" :span="${c.lastSpan}">这是一段会占满整行的较长备注文本</a-descriptions-item>`)

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="${fileUrl(antdCss)}">
<script>window.process = { env: { NODE_ENV: 'development' } }</script>
<script src="${fileUrl(vueJs)}"></script>
<script src="${fileUrl(antdJs)}"></script>
<style>
  body { margin: 0; padding: 8px; font: 12px/1.5 -apple-system, "Microsoft YaHei", sans-serif; }
  .ant-descriptions-item-label, .ant-descriptions-item-content { font-size: 12px; }
</style>
</head>
<body>
<div id="app">
  <a-descriptions size="small" bordered :column="col">
${items.join('\n')}
  </a-descriptions>
</div>
<script>
  var captured = []
  var origError = console.error
  console.error = function () {
    captured.push(Array.prototype.map.call(arguments, String).join(' '))
    origError.apply(console, arguments)
  }
  try {
    new Vue({
      el: '#app',
      data: function () { return { col: ${c.column} } }
    })
    // 等 responsiveObserve 推一次真实断点，再把结果抛给父页面
    setTimeout(function () {
      var payload = {
        status: 'done',
        id: ${JSON.stringify(c.id)},
        title: ${JSON.stringify(c.title)},
        width: ${c.iframeWidth},
        warnings: captured.filter(function (m) { return m.indexOf('Sum of column') !== -1 }),
        allErrors: captured
      }
      try { parent.postMessage(payload, '*') } catch (e) {}
    }, 400)
  } catch (e) {
    try { parent.postMessage({ status: 'error', id: ${JSON.stringify(c.id)}, message: String(e) }, '*') } catch (e2) {}
  }
</script>
</body>
</html>`
}

// ---- 写各用例页面 ----
const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'desc-span-'))
const frames = CASES.map(c => {
  const f = path.join(tmpRoot, `case-${c.id}.html`)
  fs.writeFileSync(f, caseHtml(c), 'utf8')
  return { case: c, file: f }
})

const frameHeight = 192
const rowHeight = frameHeight + 34
const bodyHeight = frames.length * rowHeight + 220

const parentHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="${fileUrl(antdCss)}">
<style>
  html, body { margin: 0; background: #eef2f7; font: 13px/1.5 -apple-system, "Microsoft YaHei", sans-serif; }
  .wrap { padding: 12px; }
  h1 { font-size: 15px; margin: 0 0 4px; color: #0f172a; }
  .sub { font-size: 12px; color: #64789a; margin-bottom: 10px; }
  .row { background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; margin-bottom: 10px; overflow: hidden; }
  .row-head { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-bottom: 1px solid #eef2f7; }
  .row-id { font-weight: 700; color: #2e7cf6; }
  .row-title { font-weight: 600; color: #0f172a; }
  .row-note { color: #64789a; font-size: 11px; }
  .badge { margin-left: auto; font-weight: 700; padding: 1px 8px; border-radius: 10px; font-size: 11px; white-space: nowrap; }
  .badge.warn { background: #fff1f0; color: #cf1322; border: 1px solid #ffa39e; }
  .badge.ok { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
  .badge.pending { background: #f5f5f5; color: #8c8c8c; border: 1px solid #d9d9d9; }
  iframe { display: block; border: 0; background: #fff; }
  #summary { background: #0f172a; color: #e2e8f0; padding: 10px; border-radius: 6px; font: 12px/1.7 Consolas, monospace; white-space: pre; margin: 0; }
</style>
</head>
<body>
<div class="wrap">
  <h1>antd Descriptions 「span 之和超过 column」告警复现 / 验证</h1>
  <div class="sub">每个用例一个 iframe：独立 antd 实例（warningOnce 各去各的重）+ 独立视口（matchMedia 按 iframe 宽度算断点）</div>
  ${frames.map(f => `
  <div class="row">
    <div class="row-head">
      <span class="row-id">${f.case.id}</span>
      <span class="row-title">${f.case.title}</span>
      <span class="row-note">column=${f.case.column.replace(/</g, '&lt;')} · 宽=${f.case.iframeWidth} · ${f.case.note}</span>
      <span class="badge pending" id="badge-${f.case.id}">…</span>
    </div>
    <iframe id="frame-${f.case.id}" src="${fileUrl(f.file)}" width="${f.case.iframeWidth}" height="${frameHeight}"></iframe>
  </div>`).join('')}
  <pre id="summary">等待中…</pre>
</div>
<script>
  var EXPECT = ${JSON.stringify(CASES.map(c => ({ id: c.id, expectWarning: c.expectWarning })))}
  var RESULTS = {}
  window.addEventListener('message', function (ev) {
    var d = ev.data
    if (d && d.id) { RESULTS[d.id] = d; render() }
  })
  function render () {
    var lines = []
    var doneCount = 0
    EXPECT.forEach(function (e) {
      var badge = document.getElementById('badge-' + e.id)
      var r = RESULTS[e.id]
      if (!r) {
        badge.className = 'badge pending'
        badge.textContent = '…'
        lines.push(e.id + '  等待中')
        return
      }
      doneCount++
      var got = r.status === 'done' && r.warnings.length > 0
      var pass = got === e.expectWarning
      badge.className = 'badge ' + (got ? 'warn' : 'ok')
      badge.textContent = got ? '有告警' : '无告警'
      lines.push(e.id + '  期望' + (e.expectWarning ? '有告警' : '无告警') +
        '   实际' + (got ? '有告警' : '无告警') + '   ' + (pass ? 'PASS' : '*** FAIL ***'))
    })
    var allPass = doneCount === EXPECT.length && lines.every(function (l) { return l.indexOf('FAIL') === -1 })
    document.getElementById('summary').textContent =
      doneCount + '/' + EXPECT.length + ' 完成' + (allPass ? '   ——   全部符合预期' : '') + '\\n' + lines.join('\\n')
  }
  render()
</script>
</body>
</html>`

const parentFile = path.join(tmpRoot, 'index.html')
fs.writeFileSync(parentFile, parentHtml, 'utf8')

const outPng = path.join(os.tmpdir(), 'descriptions-span.png')
if (fs.existsSync(outPng)) fs.unlinkSync(outPng)

const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-shot-'))
execFileSync(edge, [
  '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
  '--force-device-scale-factor=1', '--allow-file-access-from-files',
  // 让虚拟时间快进，否则截图会发生在各 iframe 的 400ms 定时器之前，结果还来不及回传
  '--virtual-time-budget=8000',
  `--user-data-dir=${profileDir}`,
  `--window-size=1500,${bodyHeight}`,
  `--screenshot=${outPng}`,
  fileUrl(parentFile)
], { stdio: 'inherit' })

// Edge 在管理员身份下会 RunDeElevated 后再拉起子进程，父进程秒退、退出码仍是 0，
// 截图是子进程稍后写的 —— 所以必须轮询等文件，不能信退出码。
const sleep = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
const deadline = Date.now() + 30000
let lastSize = -1
while (Date.now() < deadline) {
  const size = fs.existsSync(outPng) ? fs.statSync(outPng).size : 0
  if (size > 0 && size === lastSize) break
  lastSize = size
  sleep(300)
}
if (!fs.existsSync(outPng) || fs.statSync(outPng).size === 0) {
  throw new Error('等待 30s 仍没有产出截图：' + outPng)
}
fs.rmSync(profileDir, { recursive: true, force: true })
console.log('截图：' + outPng + '（' + fs.statSync(outPng).size + ' 字节）')
