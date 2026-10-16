/**
 * 数据管理新版式截图（人工复核用）
 * ------------------------------------------------------------------
 * verify-data-layout.js 负责**断言**（几何、可见行数、拖动换算）；
 * 本脚本只负责**出图**，便于人眼确认：
 *   ① 列表压到屏幕下方、只露 3~4 行；
 *   ② 检索列竖排在右侧、与左侧导航左右对称；
 *   ③ 往上拖之后列表变高、地图仍然露着。
 *
 * 用法：node perf/shot-data-layout.js [http://localhost:3001] [输出目录]
 * 产出：<输出目录>/data-{land,facility}-default.png、data-{land,facility}-tall.png
 *       （默认 perf/shots/，该目录已 gitignore）
 *
 * ★ 端口：3000 = 管理端 admin-client，3001 = 大屏端 stargis-client-web。
 */
const fs = require('fs')
const path = require('path')
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const OUT = path.resolve(process.argv[3] || path.join(__dirname, 'shots'))
const USER_DATA = os.tmpdir() + '\\edge-shot-data-' + Date.now()
const PORT = 11800 + Math.floor(Math.random() * 400)

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

async function waitForCDP (timeoutMs) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    try { const v = await httpGet('/json/version'); if (v) return JSON.parse(v) } catch (e) { /* 还没起来 */ }
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
  async evalJs (expression) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true })
    if (r.exceptionDetails) return { __error: (r.exceptionDetails.exception || {}).description || r.exceptionDetails.text }
    return r.result.value
  }
  async shot (file) {
    const r = await this.send('Page.captureScreenshot', { format: 'png' })
    fs.writeFileSync(file, Buffer.from(r.data, 'base64'))
    console.log('  已保存 ' + file)
  }
  /** 真实鼠标拖动（与 verify-data-layout.js 同一手法：走输入域，能验证缩放换算） */
  async dragMouse (x1, y1, x2, y2) {
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: Math.round(x1), y: Math.round(y1), button: 'left', clickCount: 1 })
    for (let i = 1; i <= 5; i++) {
      const t = i / 5
      await this.send('Input.dispatchMouseEvent', {
        type: 'mouseMoved',
        x: Math.round(x1 + (x2 - x1) * t),
        y: Math.round(y1 + (y2 - y1) * t),
        button: 'left',
        buttons: 1
      })
      await sleep(20)
    }
    await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: Math.round(x2), y: Math.round(y2), button: 'left', clickCount: 1 })
  }
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
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
    await new Promise((resolve, reject) => { ws.on('open', resolve); ws.on('error', reject) })
    const cdp = new CDP(ws)
    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')

    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    await sleep(4000)
    const title = await cdp.evalJs('document.title')
    if (!/星际GIS/.test(String(title))) {
      throw new Error(`打开的不是大屏端（title="${title}"）。大屏端通常是 http://localhost:3001，3000 是管理端。`)
    }
    for (let i = 0; i < 150; i++) {
      await sleep(1000)
      const r = await cdp.evalJs(`(() => {
        const inputs = Array.from(document.querySelectorAll('input'))
        const u = inputs.find(i => i.type !== 'password')
        const p = inputs.find(i => i.type === 'password')
        if (!u || !p) return 'NO'
        u.value = 'admin'; u.dispatchEvent(new Event('input', { bubbles: true }))
        p.value = '123456'; p.dispatchEvent(new Event('input', { bubbles: true }))
        return 'OK'
      })()`)
      if (r === 'OK') break
    }
    await cdp.evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)
    for (let i = 0; i < 90; i++) {
      await sleep(1000)
      const s = JSON.parse(await cdp.evalJs(`(() => {
        const norm = t => String(t||'').replace(/\\s/g,'')
        const ok = Array.from(document.querySelectorAll('.ant-modal button')).find(b => norm(b.textContent) === '确认')
        if (ok) ok.click()
        return JSON.stringify({ hash: location.hash, viewer: !!window.viewer })
      })()`))
      if (s.hash.indexOf('login') === -1 && s.viewer) break
    }
    console.log('### 已登录')

    await cdp.evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3500)

    const panels = [
      { tab: '经营性用地信息录入', scope: '.land-entry', name: 'land' },
      { tab: '配套地块数据录入', scope: '.facility-entry', name: 'facility' }
    ]

    for (const p of panels) {
      await cdp.evalJs(`(() => {
        const norm = t => String(t||'').replace(/\\s/g,'')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(p.tab)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(3000)
      console.log(`--- ${p.tab} ---`)
      await cdp.shot(path.join(OUT, `data-${p.name}-default.png`))

      // 往上拖，让列表变高（同时也复核拖动可用）
      const c = JSON.parse(await cdp.evalJs(`(() => {
        const bar = document.querySelector('${p.scope} .screen-resize-bar')
        if (!bar) return 'null'
        const r = bar.getBoundingClientRect()
        return JSON.stringify({ x: r.left + r.width / 2, y: r.top + r.height / 2 })
      })()`))
      if (c) {
        await cdp.dragMouse(c.x, c.y, c.x, c.y - 330)
        await sleep(900)
        const h = await cdp.evalJs(`document.querySelector('${p.scope}__list').offsetHeight`)
        console.log(`  拖动后列表高 = ${h}px`)
        await cdp.shot(path.join(OUT, `data-${p.name}-tall.png`))
      }
    }
  } catch (e) {
    console.log('✗ 截图脚本异常：' + (e && e.message))
    process.exitCode = 1
  } finally {
    try { ws && ws.close() } catch (e) { /* 忽略 */ }
    try { edge.kill() } catch (e) { /* 忽略 */ }
    await sleep(400)
    process.exit(process.exitCode || 0)
  }
})()
