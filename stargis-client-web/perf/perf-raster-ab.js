/**
 * 绘制/合成成本 A/B 探针
 * ===================================================================
 * perf-click-cost.js 的结论：所有点击的「同步阻塞」都是 ~0ms（JS 没卡），
 * 但**画面最大帧间隔**高达 90~157ms —— 主线程空闲却没有帧产出，
 * 说明瓶颈在 paint / raster / composite，不在 JavaScript。
 *
 * 本脚本针对这个结论做 A/B：每个变体都整页重新加载（原因见 verify-map-pause.js），
 * 然后跑一字不差的四段真实交互，记录帧间隔分布：
 *
 *   HOVER   鼠标扫过表格 30 次（每次移动都要重绘 hover 底色）
 *   CLICK   点「新增」打开弹窗（此前测得 157ms 帧间隔）
 *   TAB     切二级页签（此前测得 79~91ms 帧间隔）
 *   SCROLL  面板内滚轮滚动 10 次
 *
 * 变体（只改一个因素）：
 *   现状 / 关 backdrop-filter / 关 box-shadow / 两个都关
 *
 * 同时给每个变体存一张截图，用来确认「关掉之后画面到底变了多少」——
 * 面板底色是 rgba(8,22,42,0.92)（92% 不透明），12px 的背景模糊本来就看不太出来。
 *
 * 用法：
 *   node perf/perf-raster-ab.js http://localhost:3000 /screen/archive 1.5
 *   node perf/perf-raster-ab.js http://localhost:3000 "/screen/review?tab=query" 1.25
 */
const { spawn } = require('child_process')
const http = require('http')
const os = require('os')
const fs = require('fs')
const path = require('path')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const ROUTE = process.argv[3] || '/screen/archive'
const DSF = process.argv[4] || '1' // 模拟 Windows 显示缩放：1.25 / 1.5
/**
 * 第 5 个参数 sw=1 时用**软件渲染**跑（--disable-gpu）。
 * 用途：这个仓库所在的机器是 RTX 3070 Ti，硬件加速下 blur/shadow 都量不出掉帧；
 * 但现场机器很可能是「浏览器没开硬件加速 / 集显 / 云桌面」，那种情况下光栅化全压
 * 在 CPU 上，backdrop-filter 的代价会被放大几十倍。软件渲染就是用来复现这一档的。
 */
const SOFTWARE = process.argv[5] === 'sw'
const USERNAME = process.env.PERF_USER || 'admin'
const PASSWORD = process.env.PERF_PWD || '123456'
const SHOT_DIR = path.join(os.tmpdir(), 'raster-ab-shots-' + Date.now())
const USER_DATA = os.tmpdir() + '\\edge-raster-ab-' + Date.now()
const PORT = 11500 + Math.floor(Math.random() * 300)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function httpGet (path) {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: PORT, path }, (res) => {
      let d = ''
      res.on('data', (c) => (d += c))
      res.on('end', () => resolve(d))
    }).on('error', reject)
  })
}

async function waitForCDP (timeoutMs) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    try { const v = await httpGet('/json/version'); if (v) return JSON.parse(v) } catch (e) {}
    await sleep(300)
  }
  throw new Error('CDP endpoint never came up')
}

class CDP {
  constructor (ws) {
    this.ws = ws; this.id = 0; this.pending = new Map()
    ws.on('message', (raw) => {
      const msg = JSON.parse(raw)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id); this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    })
  }
  send (method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); reject(new Error('timeout: ' + method)) } }, 180000)
    })
  }
  async evalJs (expression, awaitPromise = false) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise })
    if (r.exceptionDetails) return { __error: (r.exceptionDetails.exception || {}).description || r.exceptionDetails.text }
    return r.result.value
  }
  move (x, y) { return this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: Math.round(x), y: Math.round(y), buttons: 0 }) }
  wheel (x, y, deltaY) { return this.send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: Math.round(x), y: Math.round(y), deltaX: 0, deltaY }) }
}

const HELPERS = `
window.__g = {
  frames: [], longtasks: [], on: false,
  start: function () { this.frames = []; this.longtasks = []; this.on = true; this._last = 0 },
  stats: function () {
    const f = this.frames
    const gaps = []
    for (let i = 1; i < f.length; i++) gaps.push(f[i] - f[i - 1])
    gaps.sort(function (a, b) { return a - b })
    const p = function (q) { return gaps.length ? +gaps[Math.min(gaps.length - 1, Math.floor(gaps.length * q))].toFixed(1) : -1 }
    return {
      frames: f.length,
      p50: p(0.5), p90: p(0.9), p99: p(0.99),
      max: gaps.length ? +gaps[gaps.length - 1].toFixed(1) : -1,
      over33: gaps.filter(function (g) { return g > 33 }).length,
      over50: gaps.filter(function (g) { return g > 50 }).length,
      longtaskMax: this.longtasks.length ? Math.max.apply(null, this.longtasks) : 0
    }
  },
  sweepPts: function (n) {
    const el = document.querySelector('.screen-data-table__scroll') ||
      document.querySelector('.escalation-query__scroll') ||
      document.querySelector('.archive-maintain__list') || document.body
    const r = el.getBoundingClientRect()
    const pts = []
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1)
      pts.push({ x: r.left + r.width * (0.05 + 0.9 * t), y: r.top + r.height * (0.2 + 0.6 * ((i % 5) / 4)) })
    }
    return pts
  },
  scrollPoint: function () {
    const el = document.querySelector('.screen-data-table__scroll') || document.querySelector('.escalation-query__scroll')
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height * 0.6 }
  },
  idleTabIndex: function () {
    const list = Array.from(document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item'))
    for (let i = 0; i < list.length; i++) if (!list[i].classList.contains('is-active')) return i
    return -1
  },
  navText: function (i) {
    const el = document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item')[i]
    return el ? (el.textContent || '').trim() : ''
  },
  clickByIdx: function (i) {
    const el = document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item')[i]
    if (!el) return false
    el.click(); return true
  },
  clickText: function (selList, text) {
    const norm = function (t) { return String(t || '').replace(/\\s/g, '') }
    const list = Array.from(document.querySelectorAll(selList)).filter(function (el) {
      const r = el.getBoundingClientRect()
      return r.width > 2 && r.height > 2 && !el.disabled
    })
    let i
    for (i = 0; i < list.length; i++) if (norm(list[i].textContent) === norm(text)) { list[i].click(); return true }
    for (i = 0; i < list.length; i++) if (norm(list[i].textContent).indexOf(norm(text)) > -1) { list[i].click(); return true }
    return false
  },
  closeModal: function () {
    const norm = function (t) { return String(t || '').replace(/\\s/g, '') }
    const b = Array.from(document.querySelectorAll('button')).find(function (x) { return ['取消', '关闭', '×'].indexOf(norm(x.textContent)) > -1 })
    if (b) { b.click(); return true }
    const x = document.querySelector('.screen-modal__close, .ant-modal-close')
    if (x) { x.click(); return true }
    return false
  },
  rasterInfo: function () {
    const all = Array.from(document.querySelectorAll('*'))
    let blurred = 0, shadowed = 0
    all.forEach(function (el) {
      const s = getComputedStyle(el)
      if ((s.backdropFilter && s.backdropFilter !== 'none') || (s.webkitBackdropFilter && s.webkitBackdropFilter !== 'none')) blurred++
      if (s.boxShadow && s.boxShadow !== 'none') shadowed++
    })
    return { blurred: blurred, shadowed: shadowed, nodes: all.length,
      dpr: window.devicePixelRatio, inner: window.innerWidth + 'x' + window.innerHeight,
      override: !!document.getElementById('__ab_css') }
  },
  /**
   * 注入覆盖样式。
   * ⚠ 不能用 Page.addScriptToEvaluateOnNewDocument 在 document-start 里 appendChild：
   *   那时 document.documentElement 还是 null，appendChild 直接抛错、样式静默消失
   *   （第一次跑这组 A/B 就踩了这个坑，四个变体其实一模一样）。
   *   改为页面加载完成后注入，并回读计算样式确认真的生效。
   */
  applyCss: function (text) {
    const old = document.getElementById('__ab_css')
    if (old) old.remove()
    const s = document.createElement('style')
    s.id = '__ab_css'
    s.textContent = text
    document.head.appendChild(s)
    return true
  }
};
(function tick (ts) {
  if (window.__g.on) window.__g.frames.push(ts)
  requestAnimationFrame(tick)
})(performance.now());
try {
  new PerformanceObserver(function (l) {
    if (!window.__g.on) return
    l.getEntries().forEach(function (e) { window.__g.longtasks.push(Math.round(e.duration)) })
  }).observe({ entryTypes: ['longtask'] })
} catch (e) {}
`

/** 打开某个弹窗并统计帧间隔，然后关掉 */
async function clickPhase (cdp, label) {
  await cdp.evalJs('window.__g.start()')
  const ok = await cdp.evalJs(`window.__g.clickText('button', ${JSON.stringify(label)})`)
  await sleep(2000)
  const s = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.stats())'))
  s.ok = ok
  await cdp.evalJs('window.__g.closeModal()')
  await sleep(1000)
  return s
}

/** 四段交互，每段单独统计帧间隔 */
async function measurePhases (cdp) {
  const out = {}

  // ① HOVER
  await cdp.evalJs('window.__g.start()')
  const pts = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.sweepPts(30))'))
  for (const p of pts) { await cdp.move(p.x, p.y); await sleep(18) }
  out.HOVER = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.stats())'))

  // ② CLICK：打开「新增」弹窗（连做两次：第一次是冷启动，第二次是热态）
  out.CLICK1 = await clickPhase(cdp, '新增')
  out.CLICK2 = await clickPhase(cdp, '新增')

  // ③ TAB：切到「当前未选中」的第一个页签
  await cdp.evalJs('window.__g.start()')
  const idx = await cdp.evalJs('window.__g.idleTabIndex()')
  await cdp.evalJs(`window.__g.clickByIdx(${idx})`)
  await sleep(2000)
  out.TAB = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.stats())'))
  out.TAB.name = await cdp.evalJs(`window.__g.navText(${idx})`)

  // ④ SCROLL
  await cdp.evalJs('window.__g.start()')
  const sp = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.scrollPoint())') || 'null')
  if (sp) { for (let i = 0; i < 10; i++) { await cdp.wheel(sp.x, sp.y, 120); await sleep(40) } }
  out.SCROLL = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.stats())'))
  out.SCROLL.ok = !!sp

  return out
}

async function runVariant (cdp, name, css) {
  await cdp.send('Page.navigate', { url: 'about:blank' })
  await sleep(400)
  await cdp.send('Page.navigate', { url: BASE + '/#' + ROUTE })
  await sleep(9000)

  // 先注入覆盖样式并回读，确认真的生效，再开始量
  let info = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.rasterInfo())'))
  if (css) {
    await cdp.evalJs(`window.__g.applyCss(${JSON.stringify(css)})`)
    await sleep(800)
    const after = JSON.parse(await cdp.evalJs('JSON.stringify(window.__g.rasterInfo())'))
    console.log(`\n--- ${name} ---`)
    console.log(`  样式注入确认：backdrop-filter ${info.blurred} → ${after.blurred} 个，box-shadow ${info.shadowed} → ${after.shadowed} 个`)
    info = after
    if (css.indexOf('backdrop-filter') > -1 && after.blurred > 0) {
      console.log('  ⚠ 模糊元素数没有降到 0，这个变体的结论不可信')
    }
  } else {
    console.log(`\n--- ${name} ---`)
  }

  const shot = await cdp.send('Page.captureScreenshot', { format: 'png' })
  const shotPath = path.join(SHOT_DIR, name + '.png')
  fs.writeFileSync(shotPath, Buffer.from(shot.data, 'base64'))

  const phases = await measurePhases(cdp)
  console.log(`  页面：DOM ${info.nodes} 节点 | backdrop-filter ${info.blurred} 个 | 有 box-shadow 的元素 ${info.shadowed} 个 | dpr ${info.dpr} 视口 ${info.inner}`)
  console.log(`  截图：${shotPath}`)
  ;['HOVER', 'CLICK1', 'CLICK2', 'TAB', 'SCROLL'].forEach((k) => {
    const s = phases[k]
    const extra = s.ok === false ? '（该操作没找到目标元素）' : ''
    console.log(`  ${k.padEnd(7)} 帧间隔 p50=${String(s.p50).padStart(6)}ms p90=${String(s.p90).padStart(6)}ms max=${String(s.max).padStart(7)}ms` +
      `  >33ms ${String(s.over33).padStart(3)} 帧 / >50ms ${String(s.over50).padStart(3)} 帧 | 长任务 ${s.longtaskMax}ms ${extra}`)
  })
  return { name, info, phases }
}

;(async () => {
  fs.mkdirSync(SHOT_DIR, { recursive: true })
  const flags = [
    '--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    '--window-size=1920,1080', '--force-device-scale-factor=' + DSF
  ]
  if (SOFTWARE) {
    flags.push('--disable-gpu', '--disable-gpu-compositing', '--disable-accelerated-2d-canvas')
  } else {
    flags.push('--enable-gpu', '--use-angle=d3d11', '--ignore-gpu-blocklist')
  }
  flags.push('about:blank')
  const edge = spawn(EDGE, flags, { stdio: 'ignore' })

  let ws
  try {
    await waitForCDP(40000)
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find((t) => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 512 * 1024 * 1024 })
    await new Promise((res, rej) => { ws.on('open', res); ws.on('error', rej) })
    const cdp = new CDP(ws)
    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: HELPERS })

    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    let filled = false
    for (let i = 0; i < 120; i++) {
      await sleep(1000)
      const r = await cdp.evalJs(`(() => {
        const inputs = Array.from(document.querySelectorAll('input'))
        const u = inputs.find(i => i.type !== 'password'); const p = inputs.find(i => i.type === 'password')
        if (!u || !p) return 'NO'
        u.value = ${JSON.stringify(USERNAME)}; u.dispatchEvent(new Event('input', { bubbles: true }))
        p.value = ${JSON.stringify(PASSWORD)}; p.dispatchEvent(new Event('input', { bubbles: true }))
        return 'OK'
      })()`)
      if (r === 'OK') { filled = true; break }
    }
    if (!filled) throw new Error('登录页表单没出现')
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)
    let ok = false
    for (let i = 0; i < 60; i++) {
      await sleep(1000)
      const s = JSON.parse(await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const b = Array.from(document.querySelectorAll('.ant-modal button')).find(x => norm(x.textContent) === '确认')
        if (b) b.click()
        return JSON.stringify({ hash: location.hash, viewer: !!window.viewer })
      })()`))
      if (s.hash.indexOf('login') === -1 && s.viewer) { ok = true; break }
    }
    if (!ok) throw new Error('没能进入大屏')
    console.log('### 已登录  route=' + ROUTE + '  dsf=' + DSF + '  渲染=' + (SOFTWARE ? '软件（--disable-gpu）' : '硬件加速'))

    const NO_BLUR = '*,*::before,*::after{backdrop-filter:none !important;-webkit-backdrop-filter:none !important}'
    const NO_SHADOW = '*,*::before,*::after{box-shadow:none !important}'
    const NO_BOTH = '*,*::before,*::after{backdrop-filter:none !important;-webkit-backdrop-filter:none !important;box-shadow:none !important}'

    // 最后一个变体是「现状对照」：如果它也变快，说明前面看到的差异是冷/热启动
    // 造成的，而不是那两条 CSS 造成的。
    const results = []
    results.push(await runVariant(cdp, '1-现状', null))
    results.push(await runVariant(cdp, '2-关blur', NO_BLUR))
    results.push(await runVariant(cdp, '3-关shadow', NO_SHADOW))
    results.push(await runVariant(cdp, '4-都关', NO_BOTH))
    results.push(await runVariant(cdp, '5-现状对照', null))

    console.log('\n### 汇总：各段「最大帧间隔」（越小越跟手）')
    console.log('  变体                    HOVER    CLICK1    CLICK2       TAB    SCROLL')
    results.forEach(({ name, phases }) => {
      console.log(`  ${name.padEnd(22)} ${String(phases.HOVER.max).padStart(7)}ms ${String(phases.CLICK1.max).padStart(7)}ms ${String(phases.CLICK2.max).padStart(7)}ms ${String(phases.TAB.max).padStart(7)}ms ${String(phases.SCROLL.max).padStart(7)}ms`)
    })
    console.log('\n### 汇总：>50ms 的掉帧数')
    console.log('  变体                    HOVER    CLICK1    CLICK2       TAB    SCROLL')
    results.forEach(({ name, phases }) => {
      console.log(`  ${name.padEnd(22)} ${String(phases.HOVER.over50).padStart(7)}   ${String(phases.CLICK1.over50).padStart(7)}   ${String(phases.CLICK2.over50).padStart(7)}   ${String(phases.TAB.over50).padStart(7)}   ${String(phases.SCROLL.over50).padStart(7)}`)
    })
    console.log('\n截图目录：' + SHOT_DIR)
  } catch (e) {
    console.error('PROBE ERROR:', (e && e.stack) || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { spawn('taskkill', ['/PID', String(edge.pid), '/T', '/F'], { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 800)
  }
})()
