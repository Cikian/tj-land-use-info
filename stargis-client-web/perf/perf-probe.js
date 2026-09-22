/**
 * 临时性能探针：用无头 Edge + CDP 采集页面运行时指标。
 * 仅用于本次诊断，诊断完可删除。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const URL_TARGET = process.argv[2] || 'http://localhost:3002/'
const OBSERVE_MS = Number(process.argv[3] || 12000)
const PROFILE_MS = Number(process.argv[4] || 10000)
const USER_DATA = require('os').tmpdir() + '\\edge-perf-probe-' + Date.now()
const PORT = 9333

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
    try {
      const v = await httpGet('/json/version')
      if (v) return JSON.parse(v)
    } catch (e) { /* not up yet */ }
    await new Promise(r => setTimeout(r, 300))
  }
  throw new Error('CDP endpoint never came up')
}

class CDP {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.handlers = []
    ws.on('message', raw => {
      const msg = JSON.parse(raw)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      } else if (msg.method) {
        this.handlers.forEach(h => h(msg))
      }
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
      setTimeout(() => {
        if (this.pending.has(id)) { this.pending.delete(id); reject(new Error('timeout: ' + method)) }
      }, 60000)
    })
  }
}

;(async () => {
  const edge = spawn(EDGE, [
    '--headless=new',
    '--remote-debugging-port=' + PORT,
    '--user-data-dir=' + USER_DATA,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--window-size=1600,900',
    '--enable-precise-memory-info',
    'about:blank'
  ], { stdio: 'ignore' })

  let ws
  try {
    await waitForCDP(30000)
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find(t => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 512 * 1024 * 1024 })
    await new Promise((res, rej) => { ws.on('open', res); ws.on('error', rej) })
    const cdp = new CDP(ws)

    // ---- 网络与运行时统计 ----
    const reqs = new Map()
    let finished = 0
    let transferred = 0
    const byType = {}
    const consoleErrors = []
    const longTasks = []
    const navTiming = {}

    cdp.on(msg => {
      const { method, params } = msg
      if (method === 'Network.requestWillBeSent') {
        reqs.set(params.requestId, { url: params.request.url, type: params.type })
      } else if (method === 'Network.loadingFinished') {
        finished++
        transferred += params.encodedDataLength || 0
        const r = reqs.get(params.requestId)
        if (r) {
          byType[r.type] = byType[r.type] || { n: 0, bytes: 0 }
          byType[r.type].n++
          byType[r.type].bytes += params.encodedDataLength || 0
        }
      } else if (method === 'Runtime.consoleAPICalled' && (params.type === 'error' || params.type === 'warning')) {
        consoleErrors.push(params.type + ': ' + params.args.map(a => a.value || a.description || a.type).join(' ').slice(0, 200))
      } else if (method === 'Runtime.exceptionThrown') {
        consoleErrors.push('EXCEPTION: ' + (params.exceptionDetails.exception && params.exceptionDetails.exception.description || params.exceptionDetails.text).slice(0, 300))
      } else if (method === 'PerformanceObserver') {
        // not used
      }
    })

    await cdp.send('Network.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')
    await cdp.send('Performance.enable')
    await cdp.send('Profiler.enable')

    // 注入 PerformanceObserver，抓 longtask + 帧间隔
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `
        window.__probe = { longtasks: [], frames: [], layoutShifts: [] };
        try {
          new PerformanceObserver(function (l) {
            l.getEntries().forEach(function (e) { window.__probe.longtasks.push({ d: Math.round(e.duration), name: e.name, start: Math.round(e.startTime) }); });
          }).observe({ entryTypes: ['longtask'] });
        } catch (e) {}
        try {
          new PerformanceObserver(function (l) {
            l.getEntries().forEach(function (e) { window.__probe.layoutShifts.push(Math.round(e.value * 1000) / 1000); });
          }).observe({ entryTypes: ['layout-shift'] });
        } catch (e) {}
        (function loop() {
          var last = performance.now();
          function tick(now) { window.__probe.frames.push(Math.round((now - last) * 100) / 100); last = now; requestAnimationFrame(tick); }
          requestAnimationFrame(tick);
        })();
      `
    })

    const t0 = Date.now()
    await cdp.send('Page.navigate', { url: URL_TARGET })
    await cdp.send('Page.loadEventFired').catch(() => {})
    const tLoad = Date.now() - t0
    console.log('== loadEventFired after ' + tLoad + 'ms ==')

    // 观察窗口
    await new Promise(r => setTimeout(r, OBSERVE_MS))

    const metrics1 = await cdp.send('Performance.getMetrics')
    const m = {}
    metrics1.metrics.forEach(x => (m[x.name] = x.value))

    // ---- CPU Profile ----
    const consoleApi = []
    cdp.on(msg => {
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'log') {
        consoleApi.push(msg.params.args.map(a => String(a.value || a.description || '')).join(' '))
      }
    })

    await cdp.send('Profiler.start')
    await new Promise(r => setTimeout(r, PROFILE_MS))
    const prof = await cdp.send('Profiler.stop')

    const metrics2 = await cdp.send('Performance.getMetrics')
    const m2 = {}
    metrics2.metrics.forEach(x => (m2[x.name] = x.value))

    const probe = await cdp.send('Runtime.evaluate', {
      expression: `JSON.stringify({
        longtasks: window.__probe.longtasks,
        frames: window.__probe.frames,
        layoutShifts: window.__probe.layoutShifts,
        domNodes: document.getElementsByTagName('*').length,
        styleSheets: document.styleSheets.length,
        cssRules: (function(){ var n=0; for (var i=0;i<document.styleSheets.length;i++){ try { n += document.styleSheets[i].cssRules.length } catch(e){} } return n })(),
        scripTags: document.scripts.length,
        vue: !!(window.Vue),
        memory: performance.memory ? { usedMB: Math.round(performance.memory.usedJSHeapSize/1048576), totalMB: Math.round(performance.memory.totalJSHeapSize/1048576) } : null
      })`,
      returnByValue: true
    })

    // ---- 汇总 CPU profile 热点（self time） ----
    const nodes = new Map()
    ;(prof.profile.nodes || []).forEach(n => nodes.set(n.id, n))
    const selfTime = new Map()
    const total = prof.profile.endTime - prof.profile.startTime
    const deltas = prof.profile.timeDeltas || []
    const samples = prof.profile.samples || []
    for (let i = 0; i < samples.length; i++) {
      const id = samples[i]
      const dt = deltas[i] || 0
      selfTime.set(id, (selfTime.get(id) || 0) + dt)
    }
    const hotspots = []
    selfTime.forEach((us, id) => {
      const n = nodes.get(id)
      if (!n) return
      const cf = n.callFrame || {}
      hotspots.push({
        ms: Math.round(us / 1000),
        fn: cf.functionName || '(anonymous)',
        url: (cf.url || '').replace('http://localhost:3002/', '').slice(0, 110),
        line: cf.lineNumber
      })
    })
    hotspots.sort((a, b) => b.ms - a.ms)

    const frames = JSON.parse(probe.result.value).frames || []
    const ft = frames.slice(1)
    const avgFrame = ft.length ? ft.reduce((a, b) => a + b, 0) / ft.length : 0
    const over33 = ft.filter(f => f > 33.4).length
    const over100 = ft.filter(f => f > 100).length

    const dtWall = (m2.TaskDuration - m.TaskDuration)
    const cpuBusy = (m2.TaskDuration - m.TaskDuration) / (PROFILE_MS / 1000) * 100
    const scriptBusy = (m2.ScriptDuration - m.ScriptDuration) / (PROFILE_MS / 1000) * 100
    const layoutBusy = (m2.LayoutDuration - m.LayoutDuration) / (PROFILE_MS / 1000) * 100
    const styleBusy = (m2.RecalcStyleDuration - m.RecalcStyleDuration) / (PROFILE_MS / 1000) * 100

    console.log('\n===== 网络统计 (整段观察期) =====')
    console.log('请求数(完成):', finished, ' 传输字节:', (transferred / 1024 / 1024).toFixed(2), 'MB')
    Object.entries(byType).sort((a, b) => b[1].bytes - a[1].bytes).forEach(([t, v]) => {
      console.log('  ' + t.padEnd(12), String(v.n).padStart(5), (v.bytes / 1024).toFixed(1) + ' KB')
    })

    console.log('\n===== 页面结构 =====')
    console.log(probe.result.value)

    console.log('\n===== 帧率（观察窗口内 requestAnimationFrame 间隔 ms）=====')
    console.log('采样帧数:', ft.length, ' 平均间隔:', avgFrame.toFixed(1) + 'ms', ' 估算FPS:', avgFrame ? (1000 / avgFrame).toFixed(1) : 'n/a')
    console.log('超过 33ms 的帧:', over33, ' 超过 100ms 的帧:', over100)

    console.log('\n===== CPU 占用（' + PROFILE_MS + 'ms 采样窗口）=====')
    console.log('Main thread TaskDuration 占比: ' + cpuBusy.toFixed(1) + '%')
    console.log('  其中 Script: ' + scriptBusy.toFixed(1) + '%  RecalcStyle: ' + styleBusy.toFixed(1) + '%  Layout: ' + layoutBusy.toFixed(1) + '%')
    console.log('累计 TaskDuration(s): ' + dtWall.toFixed(2))

    console.log('\n===== JS 自耗时 TOP 25 =====')
    hotspots.slice(0, 25).forEach(h => {
      console.log(String(h.ms).padStart(7) + 'ms  ' + h.fn.padEnd(38).slice(0, 38) + '  ' + h.url + ':' + h.line)
    })

    console.log('\n===== Long Tasks (>50ms) =====')
    const lt = JSON.parse(probe.result.value).longtasks || []
    const ltTotal = lt.reduce((a, b) => a + b.d, 0)
    console.log('数量:', lt.length, ' 总时长:', ltTotal + 'ms')
    lt.sort((a, b) => b.d - a.d).slice(0, 15).forEach(x => console.log('  ' + String(x.d).padStart(6) + 'ms  @' + x.start + 'ms'))

    console.log('\n===== Console 错误/警告 =====')
    consoleErrors.slice(0, 30).forEach(e => console.log('  ' + e))
    if (!consoleErrors.length) console.log('  (无)')
  } catch (e) {
    console.error('PROBE ERROR:', e && e.stack || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { edge.kill() } catch (e) {}
    setTimeout(() => process.exit(0), 500)
  }
})()
