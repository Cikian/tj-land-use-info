/**
 * 调试探针：登录后把页面状态 dump 出来（判断是「应用编译/运行出错」还是「脚本问题」）
 * 用法：node perf/debug-probe.js [http://localhost:3001]
 *
 * ★ 端口：3000 = 管理端 admin-client，3001 = 大屏端 stargis-client-web（本探针的对象）。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const USER_DATA = os.tmpdir() + '\\edge-debug-' + Date.now()
const PORT = 12800 + Math.floor(Math.random() * 300)
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function httpGet (p) {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: PORT, path: p }, (res) => {
      let d = ''
      res.on('data', (c) => (d += c))
      res.on('end', () => resolve(d))
    }).on('error', reject)
  })
}

;(async () => {
  const edge = spawn(EDGE, [
    '--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--window-size=1920,1080', 'about:blank'
  ], { stdio: 'ignore' })
  let ws
  try {
    for (let i = 0; i < 100; i++) {
      try { if (await httpGet('/json/version')) break } catch (e) { /* 等 */ }
      await sleep(300)
    }
    const list = JSON.parse(await httpGet('/json/list'))
    const page = list.find((t) => t.type === 'page')
    ws = new WebSocket(page.webSocketDebuggerUrl, { perMessageDeflate: false, maxPayload: 256 * 1024 * 1024 })
    await new Promise((resolve, reject) => { ws.on('open', resolve); ws.on('error', reject) })
    let id = 0
    const pending = new Map()
    ws.on('message', (raw) => {
      const m = JSON.parse(raw)
      if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); p(m) }
    })
    const send = (method, params = {}) => new Promise((resolve) => {
      const myId = ++id
      pending.set(myId, resolve)
      ws.send(JSON.stringify({ id: myId, method, params }))
    })
    const evalJs = async (expression) => {
      const r = await send('Runtime.evaluate', { expression, returnByValue: true })
      if (r.result && r.result.exceptionDetails) return { __error: r.result.exceptionDetails.text }
      return r.result && r.result.result ? r.result.result.value : r
    }
    await send('Runtime.enable')
    await send('Page.enable')

    // 收集页面报错
    await evalJs(`window.__errs = []; window.addEventListener('error', function(e){ window.__errs.push(String(e.message)) }); 'ok'`)

    await send('Page.navigate', { url: BASE + '/#/user/login' })
    await sleep(12000)

    const dump = await evalJs(`JSON.stringify({
      hash: location.hash,
      title: document.title,
      errs: window.__errs,
      overlay: !!document.querySelector('webpack-dev-server-client-overlay, #webpack-dev-server-client-overlay'),
      overlayText: (function(){
        var o = document.querySelector('webpack-dev-server-client-overlay, #webpack-dev-server-client-overlay')
        if (!o) return null
        try { return (o.shadowRoot ? o.shadowRoot.textContent : o.textContent).slice(0, 1200) } catch (e) { return 'shadow-read-failed' }
      })(),
      inputs: Array.from(document.querySelectorAll('input')).map(i => i.type + ':' + (i.placeholder||'')),
      bodyLen: document.body ? document.body.innerText.length : 0,
      bodyHead: document.body ? document.body.innerText.slice(0, 300) : null,
      appHtmlLen: (function(){ var a = document.getElementById('app'); return a ? a.innerHTML.length : -1 })()
    })`, true)
    console.log('=== 登录页状态 ===')
    console.log(typeof dump === 'string' ? dump : JSON.stringify(dump, null, 2))

    // 试着登录
    await evalJs(`(() => {
      const inputs = Array.from(document.querySelectorAll('input'))
      const u = inputs.find(i => i.type !== 'password')
      const p = inputs.find(i => i.type === 'password')
      if (!u || !p) return 'NO-INPUTS'
      u.value = 'admin'; u.dispatchEvent(new Event('input', { bubbles: true }))
      p.value = '123456'; p.dispatchEvent(new Event('input', { bubbles: true }))
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click()
      return 'CLICKED:' + !!b
    })()`, true)

    for (let i = 0; i < 40; i++) {
      await sleep(1500)
      const s = await evalJs(`JSON.stringify({
        hash: location.hash,
        viewer: !!window.viewer,
        errs: window.__errs,
        overlay: !!document.querySelector('webpack-dev-server-client-overlay, #webpack-dev-server-client-overlay'),
        appHtmlLen: (function(){ var a = document.getElementById('app'); return a ? a.innerHTML.length : -1 })(),
        bodyHead: document.body ? document.body.innerText.slice(0, 200) : null
      })`, true)
      console.log(`[${i}] ` + (typeof s === 'string' ? s : JSON.stringify(s)))
      const parsed = typeof s === 'string' ? JSON.parse(s) : s
      if (parsed.hash && parsed.hash.indexOf('login') === -1) break
    }
  } catch (e) {
    console.log('探针异常：' + (e && e.message))
  } finally {
    try { ws && ws.close() } catch (e) { /* 忽略 */ }
    try { edge.kill() } catch (e) { /* 忽略 */ }
    await sleep(400)
    process.exit(0)
  }
})()
