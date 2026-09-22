/**
 * 临时性能探针 v2：走真实前端路由，分阶段采集指标。
 * 仅用于本次诊断，诊断完可删除。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3002'
const ROUTE = process.argv[3] || '/map'
const OBSERVE_MS = Number(process.argv[4] || 20000)
const USER_DATA = require('os').tmpdir() + '\\edge-perf-probe2-' + Date.now()
const PORT = 9334

function httpGet(path) {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: PORT, path }, res => {
      let d = ''
      res.on('data', c => (d += c))
      res.on('end', () => resolve(d))
    }).on('error', reject)
  })
}

async function waitForCDP(timeoutMs) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    try { const v = await httpGet('/json/version'); if (v) return JSON.parse(v) } catch (e) {}
    await new Promise(r => setTimeout(r, 300))
  }
  throw new Error('CDP endpoint never came up')
}

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map(); this.handlers = []
    ws.on('message', raw => {
      const msg = JSON.parse(raw)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id); this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      } else if (msg.method) this.handlers.forEach(h => h(msg))
    })
  }
  on(fn) { this.handlers.push(fn) }
  send(method, params = {}, sessionId) {
    const id = ++this.id
    const payload = { id, method, params }
    if (sessionId) payload.sessionId = sessionId
    this.ws.send(JSON.stringify(payload))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); reject(new Error('timeout: ' + method)) } }, 120000)
    })
  }
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const edge = spawn(EDGE, ['--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--window-size=1600,900',
    '--enable-precise-memory-info', 'about:blank'], { stdio: 'ignore' })
  let ws
  try {
    await waitForCDP(30000)
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find(t => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 512 * 1024 * 1024 })
    await new Promise((res, rej) => { ws.on('open', res); ws.on('error', rej) })
    const cdp = new CDP(ws)

    const reqs = new Map()
    let finished = 0, bytes = 0
    const byType = {}
    const perSecond = {}
    const t0 = Date.now()
    const errs = []

    cdp.on(msg => {
      const { method, params } = msg
      if (method === 'Network.requestWillBeSent') {
        reqs.set(params.requestId, { url: params.request.url, type: params.type, t: Date.now() - t0 })
      } else if (method === 'Network.loadingFinished') {
        finished++; bytes += params.encodedDataLength || 0
        const r = reqs.get(params.requestId)
        const sec = Math.floor((Date.now() - t0) / 1000)
        perSecond[sec] = perSecond[sec] || { n: 0, b: 0 }
        perSecond[sec].n++; perSecond[sec].b += params.encodedDataLength || 0
        if (r) {
          byType[r.type] = byType[r.type] || { n: 0, bytes: 0 }
          byType[r.type].n++; byType[r.type].bytes += params.encodedDataLength || 0
        }
      } else if (method === 'Runtime.exceptionThrown') {
        errs.push((params.exceptionDetails.exception && params.exceptionDetails.exception.description || params.exceptionDetails.text).slice(0, 200))
      } else if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
        errs.push('console.error: ' + params.args.map(a => String(a.value || a.description || '')).join(' ').slice(0, 200))
      }
    })

    await cdp.send('Network.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')
    await cdp.send('Performance.enable')
    await cdp.send('Profiler.enable')

    await cdp.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `
        window.__probe = { longtasks: [], marks: [], renders: 0 };
        try { new PerformanceObserver(function(l){ l.getEntries().forEach(function(e){ window.__probe.longtasks.push({d: Math.round(e.duration), s: Math.round(e.startTime)}); }); }).observe({entryTypes:['longtask']}); } catch(e){}
        var _raf = window.requestAnimationFrame;
        window.requestAnimationFrame = function(cb){ window.__probe.renders++; return _raf.call(window, cb); };
      `
    })

    // 1) 打开登录页
    console.log('### 阶段1: 打开 ' + BASE + '/#/user/login')
    let t = Date.now()
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    await sleep(3000)
    const snap1 = await cdp.send('Performance.getMetrics')
    const m1 = {}; snap1.metrics.forEach(x => (m1[x.name] = x.value))
    console.log('  3s 后: 完成请求 ' + finished + ' 传输 ' + (bytes / 1048576).toFixed(1) + 'MB, JS堆 ' + (m1.JSHeapUsedSize / 1048576).toFixed(0) + 'MB, DOM ' + m1.Nodes)

    // 等到网络安静（最多 60s）
    let last = finished
    for (let i = 0; i < 60; i++) {
      await sleep(1000)
      if (finished === last) break
      last = finished
    }
    const quietMs = Date.now() - t
    const snap2 = await cdp.send('Performance.getMetrics')
    const m2 = {}; snap2.metrics.forEach(x => (m2[x.name] = x.value))
    console.log('  网络安静耗时 ' + quietMs + 'ms, 完成请求 ' + finished + ' 传输 ' + (bytes / 1048576).toFixed(1) + 'MB')
    console.log('  JS堆 ' + (m2.JSHeapUsedSize / 1048576).toFixed(0) + 'MB / DOM ' + m2.Nodes + ' 节点')

    // 2) 跳到目标路由
    console.log('\n### 阶段2: 跳到 #' + ROUTE)
    const beforeJumpFinished = finished, beforeJumpBytes = bytes
    t = Date.now()
    await cdp.send('Runtime.evaluate', { expression: `location.hash = '#${ROUTE}'` })
    await sleep(2500)
    let lastF = finished
    for (let i = 0; i < 90; i++) {
      await sleep(1000)
      if (finished === lastF) break
      lastF = finished
    }
    const routeMs = Date.now() - t
    const snap3 = await cdp.send('Performance.getMetrics')
    const m3 = {}; snap3.metrics.forEach(x => (m3[x.name] = x.value))
    console.log('  加载耗时 ' + routeMs + 'ms, 新增请求 ' + (finished - beforeJumpFinished) + ' 新增传输 ' + ((bytes - beforeJumpBytes) / 1048576).toFixed(1) + 'MB')
    console.log('  JS堆 ' + (m3.JSHeapUsedSize / 1048576).toFixed(0) + 'MB / DOM ' + m3.Nodes + ' 节点')

    // 3) 观察空转
    console.log('\n### 阶段3: 空转观察 ' + OBSERVE_MS + 'ms（不做任何操作）')
    const b4 = finished, y4 = bytes
    const c4 = await cdp.send('Performance.getMetrics')
    const mm4 = {}; c4.metrics.forEach(x => (mm4[x.name] = x.value))
    await sleep(OBSERVE_MS)
    const c5 = await cdp.send('Performance.getMetrics')
    const mm5 = {}; c5.metrics.forEach(x => (mm5[x.name] = x.value))
    const dt = OBSERVE_MS / 1000
    console.log('  期间新增请求 ' + (finished - b4) + ' 新增传输 ' + ((bytes - y4) / 1048576).toFixed(2) + 'MB')
    console.log('  空闲 CPU 占用(TaskDuration): ' + (((mm5.TaskDuration - mm4.TaskDuration) / dt) * 100).toFixed(1) + '%  Script: ' + (((mm5.ScriptDuration - mm4.ScriptDuration) / dt) * 100).toFixed(1) + '%  RecalcStyle: ' + (((mm5.RecalcStyleDuration - mm4.RecalcStyleDuration) / dt) * 100).toFixed(1) + '%  Layout: ' + (((mm5.LayoutDuration - mm4.LayoutDuration) / dt) * 100).toFixed(1) + '%')
    console.log('  空闲 JS 堆增长: ' + ((mm5.JSHeapUsedSize - mm4.JSHeapUsedSize) / 1048576).toFixed(1) + 'MB -> ' + (mm5.JSHeapUsedSize / 1048576).toFixed(0) + 'MB')
    console.log('  DOM 节点: ' + mm5.Nodes + '  Layout 次数增量: ' + (mm5.LayoutCount - mm4.LayoutCount) + '  RecalcStyle 增量: ' + (mm5.RecalcStyleCount - mm4.RecalcStyleCount))

    const probe = await cdp.send('Runtime.evaluate', {
      expression: `JSON.stringify({
        hash: location.hash,
        domNodes: document.getElementsByTagName('*').length,
        styleSheets: document.styleSheets.length,
        cssRules: (function(){var n=0;for(var i=0;i<document.styleSheets.length;i++){try{n+=document.styleSheets[i].cssRules.length}catch(e){}}return n})(),
        longtasks: window.__probe.longtasks,
        rafCount: window.__probe.renders,
        canvases: document.getElementsByTagName('canvas').length,
        videos: document.getElementsByTagName('video').length,
        iframes: document.getElementsByTagName('iframe').length,
        listeners: 'n/a'
      })`, returnByValue: true
    })
    const p = JSON.parse(probe.result.value)
    console.log('\n### 页面结构 @' + p.hash)
    console.log('  DOM 节点 ' + p.domNodes + ' | 样式表 ' + p.styleSheets + ' 个 / CSS 规则 ' + p.cssRules + ' 条')
    console.log('  canvas ' + p.canvases + ' | video ' + p.videos + ' | iframe ' + p.iframes + ' | rAF 累计 ' + p.rafCount)

    const lt = p.longtasks || []
    console.log('\n### Long Task (>50ms) 共 ' + lt.length + ' 个, 总 ' + lt.reduce((a, b) => a + b.d, 0) + 'ms')
    lt.sort((a, b) => b.d - a.d).slice(0, 20).forEach(x => console.log('   ' + String(x.d).padStart(6) + 'ms @' + x.s + 'ms'))

    console.log('\n### 传输分布')
    Object.entries(byType).sort((a, b) => b[1].bytes - a[1].bytes).forEach(([k, v]) => {
      console.log('  ' + k.padEnd(12) + String(v.n).padStart(5) + ' 次  ' + (v.bytes / 1048576).toFixed(1) + ' MB')
    })

    console.log('\n### 每秒请求/流量')
    Object.entries(perSecond).sort((a, b) => a[0] - b[0]).forEach(([sec, v]) => {
      if (v.n > 0) console.log('  t=' + String(sec).padStart(3) + 's  ' + String(v.n).padStart(4) + ' 请求  ' + (v.b / 1048576).toFixed(1) + ' MB')
    })

    if (errs.length) {
      console.log('\n### 运行时报错 (前 15 条)')
      errs.slice(0, 15).forEach(e => console.log('  ' + e))
    }
  } catch (e) {
    console.error('PROBE ERROR:', e && e.stack || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { edge.kill() } catch (e) {}
    setTimeout(() => process.exit(0), 500)
  }
})()
