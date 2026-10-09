/**
 * 交互延迟探针 v2（「不跟手」专项，A/B 必须可比）
 * ===================================================================
 * v1 的教训：同一页面里先测基准、再注入样式测 A/B，各阶段的页面状态已经不同
 * （页签已切走、输入框已有内容、首次 hover 的一次性开销已经付过），量出来的
 * 数字不能横着比。v2 改成：**每个变体都整页重新加载一次**，然后跑一字不差的
 * 同一串操作，并且跑两轮以区分「首次交互的一次性开销」和「稳态开销」。
 *
 * 变体（都指向同一个页面，只改一个因素）：
 *   1) 目标路由                       —— 现状
 *   2) 目标路由?map=0                 —— 去掉 Cesium（应用自带开关）
 *   3) 目标路由?map=0 + blur:none     —— 再去掉 backdrop-filter
 *   4) 目标路由 + blur:none           —— 只去 backdrop-filter
 *
 * 指标：
 *   · Event Timing（entryType 'event'）：duration = 硬件时间戳 → 下一帧绘制完成，
 *     就是「点了以后多久画面才响应」（INP 口径）。mousemove/wheel 不在采集范围内。
 *   · hover 扫过 + 滚轮滚动期间的 rAF 帧间隔（>50ms 的帧数）——这两个动作的跟手感。
 *   · 点页签「事件发生 → DOM 真的变了」的间隔（页内 performance.now() 计时）。
 *   · Long Task。
 *
 * 用法：
 *   node perf/perf-input.js http://localhost:3000 /screen/archive
 *   node perf/perf-input.js http://localhost:3000 "/screen/review?tab=query" 1600x900
 */
const { spawn } = require('child_process')
const http = require('http')
const os = require('os')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const ROUTE = process.argv[3] || '/screen/archive'
const WINDOW = process.argv[4] || '1920x1080'
const USERNAME = process.env.PERF_USER || 'admin'
const PASSWORD = process.env.PERF_PWD || '123456'
const USER_DATA = os.tmpdir() + '\\edge-perf-input2-' + Date.now()
const PORT = 10600 + Math.floor(Math.random() * 400)

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
  click (x, y) {
    return this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: Math.round(x), y: Math.round(y), button: 'left', buttons: 1, clickCount: 1 })
      .then(() => this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: Math.round(x), y: Math.round(y), button: 'left', buttons: 0, clickCount: 1 }))
  }
  wheel (x, y, deltaY) { return this.send('Input.dispatchMouseEvent', { type: 'mouseWheel', x: Math.round(x), y: Math.round(y), deltaX: 0, deltaY }) }
  async key (type, opts) { return this.send('Input.dispatchKeyEvent', Object.assign({ type }, opts)) }
  async typeText (text) {
    for (const ch of text) {
      await this.key('keyDown', { text: ch, unmodifiedText: ch })
      await this.key('char', { text: ch, unmodifiedText: ch })
      await this.key('keyUp', { text: ch, unmodifiedText: ch })
    }
  }
}

const HELPERS = `
window.__inp = {
  events: [], longtasks: [], frames: [], lastClickTs: -1, lastMutationTs: -1,
  reset: function () {
    this.events = []; this.longtasks = []; this.frames = []
    this.lastClickTs = -1; this.lastMutationTs = -1
  },
  box: function (sel, nth) {
    const el = document.querySelectorAll(sel)[nth || 0]
    if (!el) return null
    const r = el.getBoundingClientRect()
    if (r.width < 4 || r.height < 4) return null
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
  },
  // 当前未选中的第一个二级页签：保证每一轮点的都是一次真的切换
  idleTabBox: function () {
    const el = Array.from(document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item'))
      .find(function (e) { return !e.classList.contains('is-active') })
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, text: (el.textContent || '').trim() }
  },
  textBox: function () {
    const el = document.querySelector('.screen-input__field') || document.querySelector('input[type="text"]') || document.querySelector('input')
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
  },
  scrollBox: function () {
    const el = document.querySelector('.screen-data-table__scroll') || document.querySelector('.escalation-query__scroll')
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height * 0.6 }
  },
  // hover 扫过主内容区：每次移动都要重绘 hover 底色
  sweep: function (n) {
    const el = document.querySelector('.screen-data-table__scroll') ||
      document.querySelector('.escalation-query__scroll') || document.body
    const r = el.getBoundingClientRect()
    const pts = []
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1)
      pts.push({ x: r.left + r.width * (0.06 + 0.88 * t), y: r.top + r.height * (0.25 + 0.5 * ((i % 4) / 3)) })
    }
    return pts
  },
  stats: function () {
    const ev = this.events.slice()
    const durs = ev.map(function (e) { return e.d }).sort(function (a, b) { return a - b })
    const pick = function (p) { return durs.length ? durs[Math.min(durs.length - 1, Math.floor(durs.length * p))] : -1 }
    const gaps = []
    for (let i = 1; i < this.frames.length; i++) gaps.push(this.frames[i] - this.frames[i - 1])
    gaps.sort(function (a, b) { return a - b })
    const gp = function (p) { return gaps.length ? +gaps[Math.min(gaps.length - 1, Math.floor(gaps.length * p))].toFixed(1) : -1 }
    const over = function (ms) { return gaps.filter(function (g) { return g > ms }).length }
    // 只看「用户主动触发」的那几类，避免被 mouseover/pointerleave 这类伴随事件稀释
    const KEY = { click: 1, pointerdown: 1, pointerup: 1, keydown: 1, input: 1, beforeinput: 1, keypress: 1 }
    const keyDurs = ev.filter(function (e) { return KEY[e.n] }).map(function (e) { return e.d }).sort(function (a, b) { return a - b })
    return {
      eventCount: ev.length,
      clickLikeP50: keyDurs.length ? keyDurs[Math.floor(keyDurs.length / 2)] : -1,
      clickLikeP90: keyDurs.length ? keyDurs[Math.min(keyDurs.length - 1, Math.floor(keyDurs.length * 0.9))] : -1,
      clickLikeMax: keyDurs.length ? keyDurs[keyDurs.length - 1] : -1,
      clickLikeCount: keyDurs.length,
      eventMax: durs.length ? durs[durs.length - 1] : -1,
      frameCount: gaps.length + 1,
      gapP50: gp(0.5), gapP90: gp(0.9), gapP99: gp(0.99),
      gapOver50: over(50), gapOver100: over(100),
      longtaskMax: this.longtasks.length ? Math.max.apply(null, this.longtasks) : 0,
      longtaskTotal: this.longtasks.reduce(function (a, b) { return a + b }, 0),
      clickToDom: (this.lastClickTs > 0 && this.lastMutationTs > 0) ? Math.round(this.lastMutationTs - this.lastClickTs) : -1
    }
  }
};
try {
  new PerformanceObserver(function (l) {
    l.getEntries().forEach(function (e) { window.__inp.events.push({ n: e.name, d: Math.round(e.duration) }) })
  }).observe({ type: 'event', durationThreshold: 8, buffered: true })
} catch (e) {}
try {
  new PerformanceObserver(function (l) {
    l.getEntries().forEach(function (e) { window.__inp.longtasks.push(Math.round(e.duration)) })
  }).observe({ entryTypes: ['longtask'] })
} catch (e) {}
// 点页签 → DOM 真的变了，这段间隔有多长（两者都取页内时间戳，可直接相减）
document.addEventListener('click', function () { window.__inp.lastClickTs = performance.now() }, true)
document.addEventListener('DOMContentLoaded', function () {
  const obs = new MutationObserver(function () { window.__inp.lastMutationTs = performance.now() })
  const start = function () {
    obs.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] })
  }
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start)
})
;(function tick (ts) { window.__inp.frames.push(ts); requestAnimationFrame(tick) })(performance.now());
`

/** 一字不差的一串真实操作，返回指标 */
async function interactRound (cdp, label) {
  await cdp.evalJs('window.__inp.reset()')

  // ① 鼠标扫过主内容区（看 hover 重绘拖不拖帧）
  const pts = JSON.parse(await cdp.evalJs('JSON.stringify(window.__inp.sweep(26) || [])'))
  for (const p of pts) { await cdp.move(p.x, p.y); await sleep(20) }

  // ② 点一个「当前未选中」的二级页签
  const tab = JSON.parse(await cdp.evalJs('JSON.stringify(window.__inp.idleTabBox())') || 'null')
  if (tab) { await cdp.click(tab.x, tab.y); await sleep(1200) }

  // ③ 在检索框里打字（先清空，保证每轮内容一致）
  const tb = JSON.parse(await cdp.evalJs('JSON.stringify(window.__inp.textBox())') || 'null')
  if (tb) {
    await cdp.click(tb.x, tb.y)
    await sleep(150)
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65 })
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', modifiers: 2, key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65 })
    await cdp.typeText('跟手测试')
    await sleep(300)
  }

  // ④ 面板内滚轮滚动
  const sb = JSON.parse(await cdp.evalJs('JSON.stringify(window.__inp.scrollBox())') || 'null')
  if (sb) { for (let i = 0; i < 8; i++) { await cdp.wheel(sb.x, sb.y, 120); await sleep(40) } }

  const raw = await cdp.evalJs('JSON.stringify(window.__inp.stats())')
  const s = typeof raw === 'string' ? JSON.parse(raw) : raw
  console.log(`    ${label.padEnd(10)} 离散事件 ${String(s.clickLikeCount).padStart(3)} 个：p50=${String(s.clickLikeP50).padStart(4)}ms p90=${String(s.clickLikeP90).padStart(4)}ms max=${String(s.clickLikeMax).padStart(4)}ms` +
    ` | 点页签→DOM更新 ${String(s.clickToDom).padStart(4)}ms | 帧间隔 p90=${String(s.gapP90).padStart(6)}ms >50ms ${String(s.gapOver50).padStart(2)} 帧 | 长任务 ${s.longtaskMax}ms`)
  return s
}

async function runVariant (cdp, name, url, inject) {
  // 清掉上一个变体注入的脚本
  for (const id of cdp._injected || []) { try { await cdp.send('Page.removeScriptToEvaluateOnNewDocument', { identifier: id }) } catch (e) {} }
  cdp._injected = []
  if (inject) {
    const r = await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: inject })
    cdp._injected.push(r.identifier)
  }
  // 整页加载（必须先跳 about:blank，否则同文档导航不会重建组件）
  await cdp.send('Page.navigate', { url: 'about:blank' })
  await sleep(400)
  await cdp.send('Page.navigate', { url })
  await sleep(9000)

  const shape = JSON.parse(await cdp.evalJs(`JSON.stringify({
    hash: location.hash,
    inner: window.innerWidth + 'x' + window.innerHeight,
    panels: document.querySelectorAll('.screen-panel').length,
    blurred: Array.from(document.querySelectorAll('*')).filter(function (el) {
      var s = getComputedStyle(el)
      return (s.backdropFilter && s.backdropFilter !== 'none') || (s.webkitBackdropFilter && s.webkitBackdropFilter !== 'none')
    }).length,
    canvases: document.querySelectorAll('canvas').length,
    loop: window.viewer ? !!window.viewer.useDefaultRenderLoop : null
  })`))
  console.log(`\n--- ${name} ---`)
  console.log(`  页面：panel ${shape.panels} 个 | backdrop-filter ${shape.blurred} 个 | canvas ${shape.canvases} 个 | 视口 ${shape.inner}`)

  const r1 = await interactRound(cdp, '第 1 轮')
  const r2 = await interactRound(cdp, '第 2 轮')
  return { shape, r1, r2 }
}

;(async () => {
  const edge = spawn(EDGE, [
    '--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    '--window-size=' + WINDOW.replace('x', ','), '--enable-gpu', '--use-angle=d3d11',
    '--ignore-gpu-blocklist', 'about:blank'
  ], { stdio: 'ignore' })

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
    await cdp.send('Performance.enable')
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: HELPERS })

    // ---- 登录 ----
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
    console.log('### 已登录，视口 ' + WINDOW + '，目标 ' + ROUTE)

    const NO_BLUR = `(function () {
      var s = document.createElement('style')
      s.textContent = '*,*::before,*::after{backdrop-filter:none !important;-webkit-backdrop-filter:none !important}'
      ;(document.head || document.documentElement).appendChild(s)
    })()`

    const results = []
    results.push(['现状', await runVariant(cdp, '变体 1／现状（Cesium + backdrop-filter）', BASE + '/#' + ROUTE, null)])
    results.push(['去 Cesium', await runVariant(cdp, '变体 2／?map=0（无 Cesium，blur 保留）', BASE + '/#' + ROUTE + (ROUTE.indexOf('?') > -1 ? '&' : '?') + 'map=0', null)])
    results.push(['去 Cesium+blur', await runVariant(cdp, '变体 3／?map=0 + backdrop-filter:none', BASE + '/#' + ROUTE + (ROUTE.indexOf('?') > -1 ? '&' : '?') + 'map=0', NO_BLUR)])
    results.push(['只去 blur', await runVariant(cdp, '变体 4／有 Cesium + backdrop-filter:none', BASE + '/#' + ROUTE, NO_BLUR)])

    console.log('\n### 汇总（稳态取第 2 轮；数字越小越跟手）')
    console.log('  变体                    事件 p90/max   点页签→DOM   帧间隔p90  >50ms帧   长任务')
    results.forEach(([k, v]) => {
      const r = v.r2
      console.log(`  ${k.padEnd(20)} ${String(r.clickLikeP90).padStart(4)}/${String(r.clickLikeMax).padStart(4)}ms` +
        `  ${String(r.clickToDom).padStart(6)}ms` +
        `  ${String(r.gapP90).padStart(7)}ms` +
        `  ${String(r.gapOver50).padStart(4)} 帧` +
        `  ${String(r.longtaskMax).padStart(5)}ms`)
    })
    console.log('\n  注：clickLike* 只统计 click/pointerdown/up/keydown/input 这类用户主动触发的事件；')
    console.log('      第一轮数字单独看下面各变体的「第 1 轮」，用来区分首次交互的一次性开销。')
  } catch (e) {
    console.error('PROBE ERROR:', (e && e.stack) || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { spawn('taskkill', ['/PID', String(edge.pid), '/T', '/F'], { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 800)
  }
})()
