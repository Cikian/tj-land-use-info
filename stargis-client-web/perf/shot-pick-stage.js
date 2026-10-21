/**
 * 给「添加地块」全屏模式截图（人工复核用）
 * ------------------------------------------------------------------
 * verify-pick-stage.js 负责**断言**（元素在不在、能不能点到、事件落到谁身上），
 * 本脚本只负责**出图**：进选取模式后截一张、回到表单后再截一张，
 * 便于人眼确认「页面确实全部隐藏、只剩地图 + 顶部两个按钮」。
 *
 * 用法：node perf/shot-pick-stage.js [http://localhost:3000] [输出目录]
 * 产出：<输出目录>/pick-stage-in.png、pick-stage-after.png（默认 perf/shots/）
 */
const fs = require('fs')
const path = require('path')
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const OUT = path.resolve(process.argv[3] || path.join(__dirname, 'shots'))
const USERNAME = 'admin'
const PASSWORD = '123456'
const USER_DATA = os.tmpdir() + '\\edge-shot-pick-' + Date.now()
const PORT = 11300 + Math.floor(Math.random() * 400)

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
    for (let i = 0; i < 150; i++) {
      await sleep(1000)
      const r = await cdp.evalJs(`(() => {
        const inputs = Array.from(document.querySelectorAll('input'))
        const u = inputs.find(i => i.type !== 'password')
        const p = inputs.find(i => i.type === 'password')
        if (!u || !p) return 'NO'
        u.value = ${JSON.stringify(USERNAME)}; u.dispatchEvent(new Event('input', { bubbles: true }))
        p.value = ${JSON.stringify(PASSWORD)}; p.dispatchEvent(new Event('input', { bubbles: true }))
        return 'OK'
      })()`)
      if (r === 'OK') break
    }
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)

    let ready = false
    for (let i = 0; i < 90; i++) {
      await sleep(1000)
      const r = await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const ok = Array.from(document.querySelectorAll('.ant-modal button')).find(b => norm(b.textContent) === '确认')
        if (ok) ok.click()
        return JSON.stringify({ hash: location.hash, viewer: !!window.viewer })
      })()`)
      const s = JSON.parse(r)
      if (s.hash.indexOf('login') === -1 && s.viewer) { ready = true; break }
    }
    if (!ready) throw new Error('没能进入大屏首页')
    console.log('### 已登录，Cesium 就绪')
    await sleep(3000)

    // 数据管理 → 经营性用地信息录入 → 新增宗地
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3000)
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === '经营性用地信息录入')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3500)
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '新增宗地')
      if (b) b.click(); return !!b
    })()`)
    for (let i = 0; i < 30; i++) {
      await sleep(500)
      if (await cdp.evalJs(`!!document.querySelector('.land-form__map-pick-btn')`)) break
    }
    await sleep(1500)
    console.log('### 「新增经营性用地」表单已打开')
    await cdp.shot(path.join(OUT, 'pick-0-form.png'))

    // 进入选取模式
    await cdp.evalJs(`(() => {
      const b = document.querySelector('.land-form__map-pick-btn')
      if (b && !b.disabled) b.click()
      return !!(b && !b.disabled)
    })()`)
    for (let i = 0; i < 20; i++) {
      await sleep(500)
      if (await cdp.evalJs(`!!document.querySelector('.pick-stage')`)) break
    }
    await sleep(3000) // 等 Cesium 渲染几帧，截图里地图才不是纯色
    console.log('### 已进入「添加地块」全屏模式')
    await cdp.shot(path.join(OUT, 'pick-1-selecting.png'))

    // 取消，回到正常页面
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '取消绘制')
      if (b) b.click(); return !!b
    })()`)
    for (let i = 0; i < 20; i++) {
      await sleep(500)
      if (!(await cdp.evalJs(`!!document.querySelector('.pick-stage')`))) break
    }
    await sleep(2000)
    console.log('### 已点「取消绘制」，回到正常页面')
    await cdp.shot(path.join(OUT, 'pick-2-back.png'))
  } catch (e) {
    console.log('✗ 截图失败：' + (e && e.message))
    process.exitCode = 1
  } finally {
    try { ws && ws.close() } catch (e) { /* 忽略 */ }
    try { edge.kill() } catch (e) { /* 忽略 */ }
    await sleep(500)
    process.exit(process.exitCode || 0)
  }
})()
