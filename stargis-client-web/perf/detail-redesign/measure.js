/**
 * 量版工具：把 ScreenDescriptions（真实编译的 scoped CSS）渲染出来，
 * 在真实视口下用 getBoundingClientRect 量每一行的几何，再把结果**画进页面**并截图。
 *
 * 为什么需要它：详情页排版问题（行高 / 分散 / 对齐）都是「几何问题」，
 * 靠看图推断会反复出错（本目录的 user-broken.png 就是那么来的）。
 *
 * ★ 为什么不用 Edge --dump-dom 取 stdout：
 *   这台机器上 Edge 是「提权启动 → 自己再拉起降权子进程」的模式（screenshot.ps1 里记过），
 *   父进程立刻退出，--dump-dom 的内容一个字节都拿不到（实测 stdout / 重定向都是 0 字节）。
 *   所以改成「把量到的数字写成页面上的可见文本，再截图」，用同一套 Edge 出图链路。
 *
 * 用法：
 *   node perf/detail-redesign/measure.js                     # 1912x1000 视口
 *   node perf/detail-redesign/measure.js --width 1440 --height 1100
 *   pwsh -File perf/detail-redesign/measure-shot.ps1         # 出图（校验尺寸）
 */
const fs = require('fs')
const path = require('path')
const os = require('os')
const { execFileSync } = require('child_process')
const compiler = require('vue-template-compiler')
const less = require('less')

const root = path.resolve(__dirname, '../..')
const SCOPE = 'data-v-measure'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'

/** 与 generate-protos.js 同一套 scoped 变换 */
function scopeSelectors (css) {
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '')
  let out = ''
  let i = 0
  const addScope = (sel) => sel.split(',').map((s) => {
    const t = s.trim()
    if (!t) return t
    return t.replace(/(::?[a-z-]+(\([^)]*\))?)*$/, (m) => `[${SCOPE}]${m}`)
  }).join(', ')
  while (i < clean.length) {
    const open = clean.indexOf('{', i)
    if (open === -1) { out += clean.slice(i); break }
    const head = clean.slice(i, open)
    const isAtRule = /^\s*@/.test(head)
    out += isAtRule ? head : addScope(head) + ' '
    let depth = 1
    let j = open + 1
    while (j < clean.length && depth > 0) {
      if (clean[j] === '{') depth += 1
      else if (clean[j] === '}') depth -= 1
      j += 1
    }
    const body = clean.slice(open, j)
    if (isAtRule && /^@(media|supports)/.test(head.trim())) {
      out += '{' + scopeSelectors(body.slice(1, -1)) + '}'
    } else {
      out += body
    }
    i = j
  }
  return out
}

async function compileScoped (rel) {
  const file = path.join(root, rel)
  const sfc = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  const style = sfc.styles.find((s) => s.scoped) || sfc.styles[0]
  let raw = style.content.replace(/@import[^;]+;/g, '')
  raw = raw.replace(/\.screen-ellipsis\(\);/g, 'overflow:hidden;white-space:nowrap;text-overflow:ellipsis;')
  raw = raw.replace(/\.screen-placeholder\(\);/g, 'color:var(--screen-text-mute);')
  const out = await less.render(raw, { filename: file })
  return scopeSelectors(out.css)
}

/** 真实数据：档案详情「档案属性」12 项（与 ArchiveDetailModal.attributeItems 等价） */
const ARCH_ATTR = [
  { label: '档案号', value: 'DA-2026-DEMO04', tone: 'accent' },
  { label: '档案类型', value: '电子档案' },
  { label: '密级', value: '秘密' },
  { label: '保管期限', value: '永久' },
  { label: '档案年度', value: '2026', tone: 'number' },
  { label: '归档日期', value: '2026-06-30', tone: 'number' },
  { label: '责任部门', value: '住建' },
  { label: '配套负责人', value: '李伟' },
  { label: '档案状态', value: '已归档' },
  { label: '卷内文件', value: '2 个 · 2.3 MB' },
  { label: '档案名称', value: '富锦路道路工程移交档案', stack: true },
  { label: '档案类别', value: '工程建设手续 / 移交 / 道路工程移交、其他手续 / 其他', stack: true }
]

/** 档案详情「关联项目」6 项 */
const ARCH_PROJECT = [
  { label: '出让宗地编号', value: '津丽（挂）2019-02', tone: 'accent' },
  { label: '所属行政区', value: '东丽区' },
  { label: '配套设施类别', value: '道路' },
  { label: '项目来源', value: '手工录入' },
  { label: '地块名称', value: '军粮城', stack: true },
  { label: '配套项目', value: '富锦路（北旺道—兴业道）道路工程', stack: true }
]

/** 提级论证「关联地块信息」里的长标签用例 */
const ESC_LAND = [
  { label: '行政区划', value: '滨海新区' },
  { label: '功能区', value: '高新区' },
  { label: '规划用地性质', value: '工业用地' },
  { label: '是否土地整理项目', value: '否' },
  { label: '地块面积（㎡）', value: '62800', tone: 'number' },
  { label: '关联出让宗地编号', value: '津滨高（挂）2018-17', tone: 'accent' }
]

/** 提级论证「论证结果与办理状态」7 项（含最长标签） */
const ESC_RESULT = [
  { label: '办理状态', value: '未办理' },
  { label: '论证结果', value: '—' },
  { label: '论证组织单位', value: '—' },
  { label: '论证会日期', value: '—' },
  { label: '论证完成日期', value: '—' },
  { label: '材料数 / 意见记录数', value: '2 个 / 0 条', tone: 'number' },
  { label: '创建信息', value: '系统管理员 · 2026-09-10 09:12' }
]

function cell (it, scope) {
  const cls = ['screen-descriptions__item']
  if (it.stack) cls.push('is-stack')
  const dt = 'screen-descriptions__label' + (it.stack ? ' is-stack' : '')
  const dd = 'screen-descriptions__value' + (it.stack ? ' is-stack' : '') +
    (it.tone ? ' is-' + it.tone : '')
  return `<div class="${cls.join(' ')}" ${scope} data-label="${it.label}">` +
    `<dt class="${dt}" ${scope}><span class="screen-descriptions__label-text" ${scope}>${it.label}</span></dt>` +
    `<dd class="${dd}" ${scope}>${it.value}</dd></div>`
}

function descList (items, { variant = 'flat', columns = 4, labelMax = '96px' }) {
  const cls = ['screen-descriptions', 'is-md', variant === 'flat' ? 'is-flat' : 'is-bordered']
  const style = `--sd-input-cols:${columns};--sd-label-max:${labelMax}`
  return `<dl class="${cls.join(' ')}" ${SCOPE} style="${style}" data-cols="${columns}" data-labelmax="${labelMax}">\n` +
    items.map((it) => cell(it, SCOPE)).join('\n') + `\n</dl>`
}

const TOKENS = `:root{
  --screen-bg:rgba(1,14,17,.72); --screen-panel-bg:rgba(8,22,42,.92); --screen-panel-bg-solid:#08182e;
  --screen-panel-head-bg:rgba(9,36,66,.85); --screen-row-alt:rgba(55,132,215,.08);
  --screen-border:rgba(55,132,215,.32); --screen-border-soft:rgba(55,132,215,.18);
  --screen-border-strong:rgba(130,198,255,.62);
  --screen-accent:#82c6ff; --screen-accent-soft:#66afd4; --screen-accent-deep:#1d335d; --screen-accent-glow:rgba(130,198,255,.32);
  --screen-text:#fff; --screen-text-sub:#66afd4; --screen-text-mute:#4a7396;
  --screen-success:#43e972; --screen-warning:#f5a524; --screen-danger:#f2624c; --screen-info:#55b9ff;
  --screen-radius-sm:3px; --screen-radius:4px; --screen-radius-lg:6px; --screen-radius-pill:999px;
  --screen-space-1:4px; --screen-space-2:8px; --screen-space-3:12px; --screen-space-4:16px; --screen-space-5:20px;
  --screen-font-xs:12px; --screen-font-sm:13px; --screen-font-md:14px; --screen-font-lg:16px; --screen-font-xl:18px;
  --screen-shadow:0 8px 24px rgba(0,0,0,.45); --screen-shadow-inset:inset 0 0 24px rgba(130,198,255,.06);
  --screen-blur:12px;
  --screen-font-family:'Source Han Sans SC','PingFang SC','Microsoft YaHei',-apple-system,sans-serif;
  --screen-font-number-family:var(--screen-font-family);
}*{box-sizing:border-box}
body{margin:0;font-family:var(--screen-font-family);color:var(--screen-text);background:#04091a}
.host{padding:16px}
.card{padding:12px 16px 8px;background:var(--screen-panel-bg);border:1px solid var(--screen-border-soft);border-radius:4px;margin-bottom:12px}
.card h4{display:flex;align-items:center;gap:8px;margin:0 0 8px;font-size:13px;font-weight:600}
.card h4::before{content:'';flex:none;width:3px;height:12px;border-radius:999px;background:var(--screen-accent)}
#probe{font:12px/1.6 Consolas,monospace;white-space:pre;color:#9fe8b0;padding:12px;background:#000;border:1px solid #294;margin:12px 0}`

/**
 * ★ HTML 解析陷阱：script 元素的正文里只要出现闭合标签的字面量（哪怕在 JS 字符串里），
 *   浏览器就当场结束脚本，后面的代码全部变成正文文本 —— 实测表现为整段函数源码
 *   被 window.onerror 当成错误信息打印出来（"JS ERROR: function () { …"）。
 *   所以这里用拼接避开这个字面量。
 */
const CLOSE_SCRIPT = '</' + 'script>'

const MEASURE_JS = `
(function () {
  var out = []
  function num (n) { return Math.round(n * 100) / 100 }
  out.push('viewport=' + window.innerWidth + 'x' + window.innerHeight +
    '  dpr=' + window.devicePixelRatio)
  var lists = document.querySelectorAll('.screen-descriptions')
  Array.prototype.forEach.call(lists, function (dl, li) {
    var cols = dl.getAttribute('data-cols')
    var dlRect = dl.getBoundingClientRect()
    out.push('')
    out.push('--- list #' + li + '  cols=' + cols + '  width=' + num(dlRect.width) + ' ---')
    var items = dl.querySelectorAll('.screen-descriptions__item')
    var rows = {}
    var order = []
    Array.prototype.forEach.call(items, function (item) {
      var r = item.getBoundingClientRect()
      var key = Math.round(r.top / 2) * 2
      if (!rows[key]) { rows[key] = []; order.push(key) }
      rows[key].push({ item: item, rect: r })
    })
    order.sort(function (a, b) { return a - b })
    var prevTop = null
    order.forEach(function (k, ri) {
      var group = rows[k]
      var h = num(group[0].rect.height)
      var pitch = prevTop === null ? '-' : num(k - prevTop)
      prevTop = k
      out.push('row' + ri + '  top=' + num(group[0].rect.top) + '  h=' + h + '  pitch=' + pitch)
      group.forEach(function (g) {
        var lab = g.item.querySelector('.screen-descriptions__label')
        var val = g.item.querySelector('.screen-descriptions__value')
        var lr = lab.getBoundingClientRect()
        var vr = val.getBoundingClientRect()
        var lcs = getComputedStyle(lab)
        var vcs = getComputedStyle(val)
        var clipped = val.scrollWidth > val.clientWidth + 1 ? ' CLIPPED' : ''
        out.push('    ' + pad(lab.textContent.trim(), 12) +
          ' lab[x=' + num(lr.left) + ' w=' + num(lr.width) + ']' +
          '  val[x=' + num(vr.left) + ' w=' + num(vr.width) + ' h=' + num(vr.height) +
          ' fs=' + vcs.fontSize + ']' +
          '  gap=' + num(vr.left - lr.right) +
          '  dyTop=' + num(vr.top - lr.top) + clipped)
      })
    })
  })
  function pad (s, n) {
    s = String(s)
    var w = 0
    for (var i = 0; i < s.length; i++) w += s.charCodeAt(i) > 255 ? 2 : 1
    var need = n * 2 - w
    while (need > 0) { s += ' '; need -= 1 }
    return s
  }
  var pre = document.createElement('pre')
  pre.id = 'result'
  pre.textContent = out.join(String.fromCharCode(10))
  document.body.appendChild(pre)
  var raw = []
  Array.prototype.forEach.call(document.querySelectorAll('.screen-descriptions__item'), function (item) {
    var lab = item.querySelector('.screen-descriptions__label')
    var val = item.querySelector('.screen-descriptions__value')
    var lr = lab.getBoundingClientRect()
    var vr = val.getBoundingClientRect()
    var ir = item.getBoundingClientRect()
    raw.push({
      label: lab.textContent.trim(),
      itemW: num(ir.width), itemH: num(ir.height),
      labelW: num(lr.width), labelX: num(lr.left),
      valueX: num(vr.left), valueW: num(vr.width), valueH: num(vr.height),
      gap: num(vr.left - lr.right), dyTop: num(vr.top - lr.top)
    })
  })
  var pre2 = document.createElement('pre')
  pre2.id = 'json'
  pre2.textContent = JSON.stringify({ viewport: window.innerWidth, items: raw }, null, 1)
  document.body.appendChild(pre2)
})();
`

async function main () {
  const argv = process.argv.slice(2)
  const getArg = (name, def) => {
    const i = argv.indexOf(name)
    return i > -1 && argv[i + 1] ? argv[i + 1] : def
  }
  const width = parseInt(getArg('--width', '1912'), 10)
  const height = parseInt(getArg('--height', '900'), 10)

  const css = await compileScoped('src/components/screen/ScreenDescriptions.vue')
  const html = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>measure</title>
<style>${TOKENS}</style>
<style>${css}</style>
</head><body>
<pre id="probe">measuring…</pre>
<pre id="canary" style="font:16px monospace;color:#000;background:#ff0;margin:0;padding:2px">canary: script did not run</pre>
<div class="host">
  <div class="card">
    <h4>档案属性（2 列 / 标签封顶 96px）— ArchiveDetailModal 采用值</h4>
    ${descList(ARCH_ATTR, { columns: 2, labelMax: '96px' })}
  </div>
  <div class="card">
    <h4>档案属性（3 列 / 标签封顶 96px）— 对照</h4>
    ${descList(ARCH_ATTR, { columns: 3, labelMax: '96px' })}
  </div>
  <div class="card">
    <h4>关联项目（3 列 / 标签封顶 76px）</h4>
    ${descList(ARCH_PROJECT, { columns: 3, labelMax: '76px' })}
  </div>
  <div class="card">
    <h4>关联地块信息（3 列 / 标签封顶 96px，含 8 字标签）</h4>
    ${descList(ESC_LAND, { columns: 3, labelMax: '96px' })}
  </div>
  <div class="card">
    <h4>论证结果与办理状态（2 列 / 标签封顶 120px，含最长标签）</h4>
    ${descList(ESC_RESULT, { columns: 2, labelMax: '120px' })}
  </div>
</div>
<script>
var canaryEl = document.getElementById('canary')
canaryEl.textContent = 'canary: script start OK'
window.onerror = function (msg, src, line, col) {
  canaryEl.textContent = 'canary: ERROR ' + String(msg).slice(0, 140) + ' @' + line + ':' + col
}
try {
${MEASURE_JS}
  var pre = document.getElementById('result')
  var json = document.getElementById('json')
  if (pre) { document.getElementById('probe').textContent = pre.textContent }
  if (json) { json.remove() }
  if (pre) { pre.remove() }
  canaryEl.remove()
} catch (e) {
  canaryEl.textContent = 'canary: MEASURE_JS threw ' + e.name + ': ' + e.message
}
${CLOSE_SCRIPT}
</body></html>`

  const outHtml = path.join(__dirname, 'measure.html')
  fs.writeFileSync(outHtml, html, 'utf8')

  if (argv.includes('--no-shot')) {
    console.log('已生成 measure.html（未截图）')
    return
  }

  // 出图：与 screenshot.ps1 同一套「校验尺寸，不符就重试」的策略
  const outPng = path.join(__dirname, 'measure.png')
  const deadline = Date.now() + 30000
  const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
  for (let attempt = 1; attempt <= 6; attempt += 1) {
    if (fs.existsSync(outPng)) fs.unlinkSync(outPng)
    const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-measure-'))
    const mode = attempt % 2 === 1 ? '--headless=new' : '--headless'
    try {
      execFileSync(EDGE, [
        mode, '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--hide-scrollbars', '--force-device-scale-factor=1',
        `--window-size=${width},${height}`,
        '--user-data-dir=' + profile,
        '--virtual-time-budget=3000',
        '--screenshot=' + outPng,
        'file:///' + outHtml.replace(/\\/g, '/')
      ], { stdio: 'ignore' })
    } catch (e) { /* Edge 父进程立刻退出是正常的 */ }
    let last = -1
    while (Date.now() < deadline) {
      const size = fs.existsSync(outPng) ? fs.statSync(outPng).size : 0
      if (size > 0 && size === last) break
      last = size
      sleep(250)
    }
    if (fs.existsSync(outPng) && fs.statSync(outPng).size > 0) {
      cleanupProfile(profile)
      console.log(`出图：${outPng}（${width}x${height}, attempt ${attempt}, ${mode}）`)
      return
    }
    cleanupProfile(profile)
  }
  console.error('6 次都没出图，请检查 Edge 是否可用')
  process.exit(2)
}

/**
 * 删临时 profile。
 * ★ Edge 的降权子进程可能还占着 Session Storage/LOCK，直接 rmSync 会抛 EBUSY
 *   （实测踩过）。删不掉就留着，让系统自己清临时目录，不要因此判断失败。
 */
function cleanupProfile (dir) {
  try {
    fs.rmSync(dir, { recursive: true, force: true })
  } catch (e) {
    // 忽略：临时目录残留不影响出图结果
  }
}

main().catch((e) => { console.error(e); process.exit(1) })
