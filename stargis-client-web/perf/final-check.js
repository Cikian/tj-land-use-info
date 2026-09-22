/**
 * 最终验证探针：只看本次导航的请求/传输/时序，不复用历史 performance 缓冲。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3002'
const ROUTE = process.argv[3] || '/user/login'
const OBSERVE = Number(process.argv[4] || 12000)
const PORT = 9340
const USER_DATA = require('os').tmpdir() + '\\edge-final-' + Date.now()

function httpGet(p) {
  return new Promise((res, rej) => {
    http.get({ host: '127.0.0.1', port: PORT, path: p }, r => { let d = ''; r.on('data', c => (d += c)); r.on('end', () => res(d)) }).on('error', rej)
  })
}
async function waitCDP(t) { const t0 = Date.now(); while (Date.now() - t0 < t) { try { const v = await httpGet('/json/version'); if (v) return JSON.parse(v) } catch (e) {} await new Promise(r => setTimeout(r, 300)) } throw new Error('no cdp') }
class CDP {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.handlers = []
    ws.on('message', raw => {
      const m = JSON.parse(raw)
      if (m.id && this.pending.has(m.id)) {
        const { resolve, reject } = this.pending.get(m.id)
        this.pending.delete(m.id)
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
      } else if (m.method) {
        this.handlers.forEach(h => h(m))
      }
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
  const edge = spawn(EDGE, ['--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA, '--no-first-run', '--window-size=1600,900', 'about:blank'], { stdio: 'ignore' })
  let ws
  try {
    await waitCDP(30000)
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find(t => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 512 * 1024 * 1024 })
    await new Promise((res, rej) => { ws.on('open', res); ws.on('error', rej) })
    const cdp = new CDP(ws)

    let t0 = 0
    const records = new Map()
    const done = []
    cdp.on(m => {
      if (m.method === 'Network.requestWillBeSent') {
        records.set(m.params.requestId, { url: m.params.request.url, type: m.params.type, t: Date.now() - t0 })
      } else if (m.method === 'Network.loadingFinished') {
        const r = records.get(m.params.requestId)
        if (r) done.push({ ...r, bytes: m.params.encodedDataLength || 0, finish: Date.now() - t0 })
      } else if (m.method === 'Network.loadingFailed') {
        const r = records.get(m.params.requestId)
        if (r) done.push({ ...r, bytes: 0, failed: m.params.errorText, finish: Date.now() - t0 })
      }
    })

    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Network.enable')
    await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })

    t0 = Date.now()
    await cdp.send('Page.navigate', { url: BASE + '/#?probe=' + Date.now() + ROUTE })
    await sleep(OBSERVE)

    const total = done.reduce((a, b) => a + b.bytes, 0)
    console.log('### 本次导航（缓存已禁用）: ' + done.length + ' 个请求 / ' + (total / 1048576).toFixed(1) + ' MB\n')

    const fonts = done.filter(r => /\.(otf|ttf|woff2?)(\?|$)/i.test(r.url))
    console.log('### 字体请求: ' + (fonts.length ? fonts.length + ' 个' : '无'))
    fonts.forEach(f => console.log('   ' + (f.bytes / 1048576).toFixed(2) + 'MB  @' + f.t + 'ms  ' + f.url.split('/').pop()))

    console.log('\n### 单请求体积 TOP 12')
    done.sort((a, b) => b.bytes - a.bytes).slice(0, 12).forEach(r => {
      console.log('  ' + (r.bytes / 1048576).toFixed(2).padStart(7) + ' MB  @' + String(r.t).padStart(6) + 'ms  ' + r.type.padEnd(10) + '  ' + r.url.replace(BASE, '').slice(0, 70))
    })

    const prefetch = done.filter(r => /\/\d+\.js$/.test(r.url))
    console.log('\n### 数字命名的动态分包: ' + prefetch.length + ' 个 / ' + (prefetch.reduce((a, b) => a + b.bytes, 0) / 1048576).toFixed(1) + ' MB')

    const nav = await cdp.send('Runtime.evaluate', {
      expression: `(function(){
        var n = performance.getEntriesByType('navigation')[0] || {};
        var paint = performance.getEntriesByType('paint').map(function(p){return p.name + '=' + Math.round(p.startTime)});
        return JSON.stringify({ dcl: Math.round(n.domContentLoadedEventEnd||0), load: Math.round(n.loadEventEnd||0), paint: paint,
          dom: document.getElementsByTagName('*').length, sheets: document.styleSheets.length,
          rules: (function(){var t=0;for(var i=0;i<document.styleSheets.length;i++){try{t+=document.styleSheets[i].cssRules.length}catch(e){}}return t})(),
          syRules: (function(){var t=0;for(var i=0;i<document.styleSheets.length;i++){var rs;try{rs=document.styleSheets[i].cssRules}catch(e){continue}
            for(var j=0;j<rs.length;j++){ if(rs[j].type===1 && /font-family:sy/i.test(rs[j].cssText)) t++ }}return t})(),
          memory: performance.memory ? Math.round(performance.memory.usedJSHeapSize/1048576) : null });
      })()`, returnByValue: true
    })
    const v = JSON.parse(nav.result.value)
    console.log('\n### 页面')
    console.log('  DOMContentLoaded ' + v.dcl + 'ms | load ' + v.load + 'ms | ' + v.paint.join(', '))
    console.log('  DOM ' + v.dom + ' 节点 | 样式表 ' + v.sheets + ' 个 / CSS 规则 ' + v.rules + ' 条')
    console.log('  仍在使用 sy 字族的样式规则: ' + v.syRules + ' 条')
    console.log('  JS 堆: ' + v.memory + ' MB')
  } catch (e) { console.error('ERR', e && e.stack || e) } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { require('child_process').execSync('taskkill /PID ' + edge.pid + ' /T /F', { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 700)
  }
})()
