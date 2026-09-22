/**
 * 临时性能探针 v3：逐请求体积排行 + 导航计时。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3002'
const USER_DATA = require('os').tmpdir() + '\\edge-perf-probe3-' + Date.now()
const PORT = 9335

function httpGet(path) {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: PORT, path }, res => {
      let d = ''; res.on('data', c => (d += c)); res.on('end', () => resolve(d))
    }).on('error', reject)
  })
}
async function waitForCDP(t) {
  const t0 = Date.now()
  while (Date.now() - t0 < t) { try { const v = await httpGet('/json/version'); if (v) return JSON.parse(v) } catch (e) {} await new Promise(r => setTimeout(r, 300)) }
  throw new Error('no cdp')
}
class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map(); this.handlers = []
    ws.on('message', raw => {
      const m = JSON.parse(raw)
      if (m.id && this.pending.has(m.id)) { const { resolve, reject } = this.pending.get(m.id); this.pending.delete(m.id); m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result) }
      else if (m.method) this.handlers.forEach(h => h(m))
    })
  }
  on(f) { this.handlers.push(f) }
  send(method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((res, rej) => { this.pending.set(id, { resolve: res, reject: rej }); setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); rej(new Error('timeout ' + method)) } }, 120000) })
  }
}
const sleep = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const edge = spawn(EDGE, ['--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--window-size=1600,900', 'about:blank'], { stdio: 'ignore' })
  let ws
  try {
    await waitForCDP(30000)
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find(t => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 512 * 1024 * 1024 })
    await new Promise((res, rej) => { ws.on('open', res); ws.on('error', rej) })
    const cdp = new CDP(ws)

    const recs = []
    const reqIndex = new Map()
    let bytes = 0
    let navigated = false
    cdp.on(msg => {
      const { method, params } = msg
      if (method === 'Network.requestWillBeSent') {
        reqIndex.set(params.requestId, { url: params.request.url, type: params.type, initiator: params.initiator && params.initiator.type })
      } else if (method === 'Network.loadingFinished') {
        const r = reqIndex.get(params.requestId) || { url: '?', type: '?' }
        recs.push({ url: r.url, type: r.type, bytes: params.encodedDataLength || 0 })
        bytes += params.encodedDataLength || 0
      }
    })
    await cdp.send('Network.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')

    // 先清空已有记录（避免复用同端口浏览器时残留旧页面统计）
    recs.length = 0
    bytes = 0
    await cdp.send('Network.clearBrowserCache').catch(() => {})
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    await new Promise(r => { cdp.on(m => { if (m.method === 'Page.loadEventFired') navigated = true }); setTimeout(r, 100) })
    await sleep(8000)
    console.log('loadEventFired: ' + navigated + '，已记录请求 ' + recs.length + ' 个 / ' + (bytes / 1048576).toFixed(1) + 'MB')

    // 大文件排行
    recs.sort((a, b) => b.bytes - a.bytes)
    console.log('### 单请求体积 TOP 25（总 ' + (bytes / 1048576).toFixed(1) + 'MB / ' + recs.length + ' 个请求）')
    recs.slice(0, 25).forEach(r => {
      console.log('  ' + (r.bytes / 1048576).toFixed(2).padStart(7) + ' MB  ' + r.type.padEnd(10) + '  ' + r.url.replace(BASE, '').slice(0, 120))
    })

    const tiny = recs.filter(r => r.bytes < 200 * 1024)
    console.log('\n### 小文件统计（<200KB，主要是 prefetch 的分包）')
    console.log('  个数 ' + tiny.length + '  合计 ' + (tiny.reduce((a, b) => a + b.bytes, 0) / 1048576).toFixed(1) + ' MB')
    const prefetch = recs.filter(r => /\/\d+\.js$|\/fail\.js|\/user\.js|\/oauth2-app\.login\.js/.test(r.url))
    console.log('  webpack 动态分包(prefetch 目标) 个数 ' + prefetch.length + '  合计 ' + (prefetch.reduce((a, b) => a + b.bytes, 0) / 1048576).toFixed(1) + ' MB')

    const nav = await cdp.send('Runtime.evaluate', {
      expression: `(function(){
        var n = performance.getEntriesByType('navigation')[0] || {};
        var paint = performance.getEntriesByType('paint').map(function(p){return p.name + '=' + Math.round(p.startTime) + 'ms'});
        var res = performance.getEntriesByType('resource');
        var slow = res.map(function(r){return {n: r.name.replace(location.origin,'').slice(0,90), d: Math.round(r.duration), s: Math.round(r.startTime), b: Math.round((r.transferSize||0)/1024)}});
        slow.sort(function(a,b){return b.d-a.d});
        return JSON.stringify({
          domContentLoaded: Math.round(n.domContentLoadedEventEnd||0),
          loadEvent: Math.round(n.loadEventEnd||0),
          firstPaint: paint,
          resourceCount: res.length,
          slowest: slow.slice(0, 15),
          latestResourceEnd: Math.round(res.reduce(function(m,r){return Math.max(m, r.responseEnd)}, 0))
        });
      })()`, returnByValue: true
    })
    console.log('\n### 导航计时')
    const nd = JSON.parse(nav.result.value)
    console.log('  DOMContentLoaded ' + nd.domContentLoaded + 'ms | loadEvent ' + nd.loadEvent + 'ms | ' + nd.firstPaint.join(', '))
    console.log('  资源总数 ' + nd.resourceCount + ' | 最后一个资源在 ' + nd.latestResourceEnd + 'ms 才结束')
    console.log('  最慢资源:')
    nd.slowest.forEach(s => console.log('    ' + String(s.d).padStart(6) + 'ms  起于' + String(s.s).padStart(6) + 'ms  ' + String(s.b).padStart(6) + 'KB  ' + s.n))
  } catch (e) {
    console.error('ERR', e && e.stack || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    // 必须连子进程一起杀，否则 headless Edge 会残留并占用调试端口，导致下次探针连到旧页面
    try { require('child_process').execSync('taskkill /PID ' + edge.pid + ' /T /F', { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 800)
  }
})()
