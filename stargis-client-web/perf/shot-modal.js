/**
 * 新增/编辑弹窗改造的效果图
 * ==================================================================
 * 出四张（都在 1920×1080 视口下）：
 *   modal-{land,facility}.png       弹窗刚打开：2 列、高度减半、**背景没有压暗**、地图可见
 *   modal-{land,facility}-pick.png  弹窗拖到一边 + 选取模式：页面全都在、按钮在顶栏之下
 *
 * ★ 端口：3000 = 管理端，3001 = 大屏端（本脚本的对象）。
 * 用法：node perf/shot-modal.js [http://localhost:3001]
 */
const { spawn } = require('child_process')
const http = require('http')
const fs = require('fs')
const path = require('path')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const OUT = path.join(__dirname, 'shots')
const USER_DATA = os.tmpdir() + '\\edge-shot-modal-' + Date.now()
const PORT = 14200 + Math.floor(Math.random() * 400)

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
    // 出图前先记下视口尺寸：headless 偶发会少掉一条 34px 的窗口装饰，
    // 尺寸不一致的图放一起对比会误导人（实测出现过 1894×953 与 1894×987 混在一批里）。
    const vp = JSON.parse(await this.evalJs('JSON.stringify({ w: innerWidth, h: innerHeight })'))
    const r = await this.send('Page.captureScreenshot', { format: 'png' })
    fs.writeFileSync(file, Buffer.from(r.data, 'base64'))
    console.log(`  → ${path.relative(process.cwd(), file)}  (视口 ${vp.w}×${vp.h})`)
  }
  async dragMouse (x1, y1, x2, y2) {
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: Math.round(x1), y: Math.round(y1), button: 'left', clickCount: 1 })
    for (let i = 1; i <= 8; i++) {
      const t = i / 8
      await this.send('Input.dispatchMouseEvent', {
        type: 'mouseMoved',
        x: Math.round(x1 + (x2 - x1) * t),
        y: Math.round(y1 + (y2 - y1) * t),
        button: 'left',
        buttons: 1
      })
      await sleep(16)
    }
    await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: Math.round(x2), y: Math.round(y2), button: 'left', clickCount: 1 })
  }
  async pressKey (key, code, vk) {
    const base = { key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk }
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyDown' }, base))
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyUp' }, base))
  }
}

const HELPERS = `
window.__sm = {
  norm: function (t) { return String(t || '').replace(/\\s/g, '') },
  box: function (el) {
    if (!el) return null
    var r = el.getBoundingClientRect()
    return { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height }
  },
  head: function () { return document.querySelector('.screen-modal__head') },
  dialog: function () { return document.querySelector('.screen-modal__dialog') },
  clickText: function (text) {
    var self = this
    var b = Array.prototype.slice.call(document.querySelectorAll('button'))
      .filter(function (x) { return self.norm(x.textContent) === text && x.offsetParent !== null })
    if (!b.length) return false
    b[0].click()
    return true
  }
}
'ok'
`

;(async () => {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })
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
      throw new Error(`打开的不是大屏端（title="${title}"）。大屏端通常是 http://localhost:3001。`)
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
    await cdp.evalJs(HELPERS)
    console.log('### 已登录')
    await cdp.evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3000)

    const panels = [
      { tab: '经营性用地信息录入', addText: '新增宗地', pickSel: '.land-form__map-pick-btn', key: 'land', name: '经营性用地' },
      { tab: '配套地块数据录入', addText: '新增配套', pickSel: '.facility-form__map-pick-btn', key: 'facility', name: '配套项目' }
    ]

    for (const p of panels) {
      console.log(`================ ${p.name} ================`)
      await cdp.evalJs(`(() => {
        const norm = t => String(t||'').replace(/\\s/g,'')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(p.tab)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(2500)

      await cdp.evalJs(`window.__sm.clickText(${JSON.stringify(p.addText)})`)
      for (let i = 0; i < 30; i++) {
        await sleep(300)
        if (await cdp.evalJs(`!!window.__sm.dialog()`)) break
      }
      await sleep(1200)
      await cdp.shot(path.join(OUT, `modal-${p.key}.png`))

      // 拖到一边（抓标题栏中心 → 让弹窗落在 x≈700、y≈300）：
      // 这个位置既让开了左侧导航与右侧检索列，也不压顶部中间的两个选取按钮，
      // 出图时能同时看到「页面全都在 + 弹窗还在 + 地图可操作」。
      const h = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__sm.box(window.__sm.head()))`))
      await cdp.dragMouse(h.left + h.width / 2, h.top + h.height / 2, 1180, 323)
      await sleep(600)
      await cdp.evalJs(`(() => { const b = document.querySelector(${JSON.stringify(p.pickSel)}); if (b) b.click(); return !!b })()`)
      for (let i = 0; i < 20; i++) {
        await sleep(300)
        if (await cdp.evalJs(`!!document.querySelector('.pick-stage')`)) break
      }
      await sleep(1200)
      await cdp.shot(path.join(OUT, `modal-${p.key}-pick.png`))

      await cdp.pressKey('Escape', 'Escape', 27)
      await sleep(800)
      await cdp.evalJs(`(() => { const b = document.querySelector('.screen-modal__close'); if (b) b.click(); return !!b })()`)
      await sleep(900)
      console.log('')
    }
    console.log('✓ 效果图已写入 perf/shots/')
  } catch (e) {
    console.log('✗ 脚本异常：' + (e && e.message))
    process.exitCode = 1
  } finally {
    try { ws && ws.close() } catch (e) { /* 忽略 */ }
    try { edge.kill() } catch (e) { /* 忽略 */ }
    await sleep(500)
    process.exit(process.exitCode || 0)
  }
})()
