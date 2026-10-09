/**
 * 「点一下要等多久」专项探针
 * ===================================================================
 * perf-input.js 说明悬停/打字/滚动已经跟手了（事件 p90 24ms、帧间隔 p90 6.2ms、
 * 零长任务，且与 Cesium / backdrop-filter 都无关）。剩下最像「不跟手」的是
 * **点一下之后的等待**：切页签、开弹窗、翻页这类会挂载/重建一大片子组件的操作。
 *
 * 本脚本逐个操作量三个数（全部在页内计时，不含 CDP 往返开销）：
 *   sync       el.click() 这一次调用里同步花掉的时间 —— Vue 的挂载/patch 是同步的，
 *              这个数就是「点下去到主线程空出来」的阻塞时长（>50ms 已经是长任务）
 *   quietAfter 点下去到「连续 150ms 没有 DOM 变化」的时长 —— 含接口返回后再渲染
 *   maxGap     点击后 1.5s 内的最大帧间隔 —— 直接对应画面卡住多久
 *   longtasks  点击后 1.5s 内的长任务
 *
 * 用法：
 *   node perf/perf-click-cost.js http://localhost:3000 /screen/archive
 *   node perf/perf-click-cost.js http://localhost:3000 "/screen/review?tab=query"
 *   node perf/perf-click-cost.js http://localhost:3000 /screen/archive /screen/review
 */
const { spawn } = require('child_process')
const http = require('http')
const os = require('os')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const ROUTES = process.argv.slice(3)
if (!ROUTES.length) ROUTES.push('/screen/archive', '/screen/review')
const USERNAME = process.env.PERF_USER || 'admin'
const PASSWORD = process.env.PERF_PWD || '123456'
const USER_DATA = os.tmpdir() + '\\edge-click-cost-' + Date.now()
const PORT = 11000 + Math.floor(Math.random() * 400)

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
}

const HELPERS = `
window.__cc = {
  measure: function (el, holdMs) {
    holdMs = holdMs || 1500
    return new Promise(function (resolve) {
      const t0 = performance.now()
      let lastMut = t0
      let muts = 0
      let maxGap = 0
      let lastFrame = 0
      const lts = []
      const mo = new MutationObserver(function (recs) {
        muts += recs.length; lastMut = performance.now()
      })
      mo.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true })
      const po = new PerformanceObserver(function (l) {
        l.getEntries().forEach(function (e) { lts.push(Math.round(e.duration)) })
      })
      try { po.observe({ entryTypes: ['longtask'] }) } catch (e) {}

      // 同步耗时：Vue 的挂载/patch 是同步的，所以这个数就是主线程被占住的时长
      let sync = -1
      try {
        const a = performance.now()
        el.click()
        sync = +(performance.now() - a).toFixed(1)
      } catch (e) {
        mo.disconnect(); po.disconnect()
        resolve({ error: String(e) })
        return
      }

      let done = false
      const finish = function (reason) {
        if (done) return
        done = true
        mo.disconnect(); po.disconnect()
        resolve({
          sync: sync,
          quietAfter: Math.round(lastMut - t0),
          maxGap: +maxGap.toFixed(1),
          longtasks: lts,
          mutations: muts,
          reason: reason
        })
      }

      const tick = function (ts) {
        if (lastFrame) { const g = ts - lastFrame; if (g > maxGap) maxGap = g }
        lastFrame = ts
        const now = performance.now()
        if (now - t0 > holdMs) { finish('window'); return }
        requestAnimationFrame(tick)
      }
      requestAnimationFrame(tick)
      // 连续 150ms 没有 DOM 变化就算静下来了
      const quietCheck = setInterval(function () {
        if (performance.now() - lastMut > 150 && performance.now() - t0 > 250) {
          clearInterval(quietCheck)
          // 等两帧再收尾，保证 maxGap 采到渲染
          requestAnimationFrame(function () { requestAnimationFrame(function () { finish('quiet') }) })
        }
      }, 60)
      setTimeout(function () { clearInterval(quietCheck); finish('timeout') }, holdMs + 400)
    })
  },
  /** 在页内按文本找按钮（先精确匹配，再前缀匹配），排除隐藏元素 */
  findByText: function (sel, text, containerSel) {
    const root = containerSel ? document.querySelector(containerSel) : document
    if (!root) return null
    const list = Array.from(root.querySelectorAll(sel)).filter(function (el) {
      const r = el.getBoundingClientRect()
      return r.width > 2 && r.height > 2 && !el.disabled
    })
    const norm = function (t) { return String(t || '').replace(/\\s/g, '') }
    let i
    for (i = 0; i < list.length; i++) if (norm(list[i].textContent) === norm(text)) return list[i]
    for (i = 0; i < list.length; i++) if (norm(list[i].textContent).indexOf(norm(text)) > -1) return list[i]
    return null
  },
  navItems: function () {
    return Array.from(document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item')).map(function (el) {
      return (el.textContent || '').trim()
    })
  },
  navItem: function (index) {
    return document.querySelectorAll('.archive-screen__nav-item, .escalation-screen__nav-item')[index] || null
  },
  closeModal: function () {
    const norm = function (t) { return String(t || '').replace(/\\s/g, '') }
    const btns = Array.from(document.querySelectorAll('.screen-modal button, .ant-modal button, button'))
    const b = btns.find(function (x) { return ['取消', '关闭', '关闭×', '×'].indexOf(norm(x.textContent)) > -1 })
    if (b) { b.click(); return 'clicked:' + norm(b.textContent) }
    const x = document.querySelector('.screen-modal__close, .ant-modal-close, [aria-label="关闭"]')
    if (x) { x.click(); return 'clicked-close' }
    return 'no-close-button'
  },
  modalOpen: function () {
    const m = document.querySelector('.screen-modal__dialog, .ant-modal-content')
    return !!m
  }
};
`

/** 逐项量一遍点击成本 */
async function measureRoute (cdp, route) {
  await cdp.send('Page.navigate', { url: 'about:blank' })
  await sleep(400)
  await cdp.send('Page.navigate', { url: BASE + '/#' + route })
  await sleep(9000)

  const info = JSON.parse(await cdp.evalJs(`JSON.stringify({
    hash: location.hash,
    inner: window.innerWidth + 'x' + window.innerHeight,
    nav: window.__cc.navItems(),
    panels: document.querySelectorAll('.screen-panel').length
  })`))
  console.log(`\n======== ${route}  (${info.inner}, panel ${info.panels} 个) ========`)
  console.log('二级页签: ' + JSON.stringify(info.nav))

  const rows = []
  const report = (name, r) => {
    if (!r || r.error) { console.log(`  ${name.padEnd(24)} 失败: ${r && r.error}`); return }
    const lt = r.longtasks.length ? `长任务 ${r.longtasks.join(',')}ms` : '无长任务'
    console.log(`  ${name.padEnd(24)} 同步阻塞 ${String(r.sync).padStart(7)}ms | 静下来 ${String(r.quietAfter).padStart(5)}ms | 最大帧间隔 ${String(r.maxGap).padStart(6)}ms | ${lt}`)
    rows.push({ name, sync: r.sync, quiet: r.quietAfter, gap: r.maxGap })
  }

  // ① 逐个二级页签（每切一个都量一次；已访问过的走 keep-alive，会明显更快）
  const navCount = info.nav.length
  for (let i = 0; i < navCount; i++) {
    const name = '切页签 → ' + info.nav[i]
    const r = await cdp.evalJs(`(async () => {
      const el = window.__cc.navItem(${i})
      if (!el) return JSON.stringify({ error: 'nav item ' + ${i} + ' 不存在' })
      return JSON.stringify(await window.__cc.measure(el))
    })()`, true)
    report(name, typeof r === 'string' ? JSON.parse(r) : r)
    await sleep(600)
  }

  // ② 回第一个页签，保证后面测的是「档案维护」/「项目录入」这种主页面
  await cdp.evalJs(`(() => { const el = window.__cc.navItem(0); if (el) el.click(); return 1 })()`)
  await sleep(1200)

  // ③「更多条件」
  const more = await cdp.evalJs(`(async () => {
    const el = window.__cc.findByText('button', '更多条件')
    if (!el) return JSON.stringify({ error: '没找到「更多条件」' })
    return JSON.stringify(await window.__cc.measure(el))
  })()`, true)
  report('展开「更多条件」', typeof more === 'string' ? JSON.parse(more) : more)
  await sleep(500)

  // ④ 表格里第一个「详情」/「查看」按钮
  const detail = await cdp.evalJs(`(async () => {
    const el = window.__cc.findByText('.screen-data-table__link, .screen-data-table__action', '详情', '.screen-data-table') ||
               window.__cc.findByText('.screen-data-table__link, .screen-data-table__action', '查看', '.screen-data-table')
    if (!el) return JSON.stringify({ error: '表格里没有详情按钮（可能没数据）' })
    return JSON.stringify(await window.__cc.measure(el, 2500))
  })()`, true)
  report('打开表格「详情」弹窗', typeof detail === 'string' ? JSON.parse(detail) : detail)
  await sleep(800)
  const close1 = await cdp.evalJs('window.__cc.closeModal()')
  console.log('    （关闭详情弹窗: ' + close1 + '）')
  await sleep(800)

  // ⑤ 工具条上的「新增」
  const add = await cdp.evalJs(`(async () => {
    const el = window.__cc.findByText('button', '新增')
    if (!el) return JSON.stringify({ error: '没有新增按钮' })
    return JSON.stringify(await window.__cc.measure(el, 2500))
  })()`, true)
  report('打开「新增」弹窗', typeof add === 'string' ? JSON.parse(add) : add)
  await sleep(800)
  const close2 = await cdp.evalJs('window.__cc.closeModal()')
  console.log('    （关闭新增弹窗: ' + close2 + '）')
  await sleep(500)

  // ⑥ 分页翻页
  const page = await cdp.evalJs(`(async () => {
    const el = window.__cc.findByText('.screen-pagination button, .screen-pagination__item', '2', '.screen-pagination') ||
               window.__cc.findByText('.screen-pagination button, .screen-pagination__item', '下一页', '.screen-pagination')
    if (!el) return JSON.stringify({ error: '没有第 2 页/下一页按钮' })
    return JSON.stringify(await window.__cc.measure(el, 2500))
  })()`, true)
  report('翻到第 2 页', typeof page === 'string' ? JSON.parse(page) : page)
  await sleep(500)

  return rows
}

;(async () => {
  const edge = spawn(EDGE, [
    '--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    '--window-size=1920,1080', '--enable-gpu', '--use-angle=d3d11', '--ignore-gpu-blocklist',
    'about:blank'
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
    console.log('### 已登录')

    const all = []
    for (const r of ROUTES) {
      const rows = await measureRoute(cdp, r)
      all.push({ route: r, rows })
    }

    console.log('\n### 汇总：超过 50ms 的操作（50ms 是长任务门槛，也是「不跟手」的起点）')
    let found = 0
    all.forEach(({ route, rows }) => {
      rows.filter((r) => r.sync > 50 || r.quiet > 400 || r.gap > 50).forEach((r) => {
        found++
        console.log(`  ${route}  ${r.name.padEnd(26)} 同步 ${String(r.sync).padStart(7)}ms  静下来 ${String(r.quiet).padStart(5)}ms  帧间隔 ${String(r.gap).padStart(6)}ms`)
      })
    })
    if (!found) console.log('  （没有任何操作超过门槛）')
  } catch (e) {
    console.error('PROBE ERROR:', (e && e.stack) || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { spawn('taskkill', ['/PID', String(edge.pid), '/T', '/F'], { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 800)
  }
})()
