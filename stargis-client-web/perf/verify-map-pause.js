/**
 * 验证「整页模块覆盖地图时暂停 Cesium 渲染」这一改动
 * ===================================================================
 * 用真实的点击（点顶栏导航）走一遍 首页 → 档案管理 → 首页，检查三件事：
 *
 *   1) 进档案管理后 window.viewer.useDefaultRenderLoop 变成 false，
 *      并且**画面真的冻住了**：主动改一次镜头，canvas 像素不变；
 *   2) 主线程占用从 ~45% 掉到 ~5%；
 *   3) 点回首页后恢复 true，并且再次改镜头 canvas 像素会变（地图是活的）。
 *
 * 用 canvas.toDataURL() 做「画面是否变化」的判据：
 * S3dmViewer 建 Viewer 时开了 preserveDrawingBuffer: true，
 * 所以 toDataURL 能拿到当前帧，不需要截图落盘再比对。
 *
 * 用法：node perf/verify-map-pause.js http://localhost:3100
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3100'
const USERNAME = 'admin'
const PASSWORD = '123456'
const USER_DATA = os.tmpdir() + '\\edge-verify-pause-' + Date.now()
// 随机调试端口，避免和上一次残留的 msedge 抢同一个端口
const PORT = 9800 + Math.floor(Math.random() * 400)

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

async function metrics (cdp) {
  const snap = await cdp.send('Performance.getMetrics')
  const m = {}
  snap.metrics.forEach((x) => (m[x.name] = x.value))
  return m
}

/** 观察窗口内的主线程占用（% of one core） */
async function busy (cdp, ms) {
  const a = await metrics(cdp)
  await sleep(ms)
  const b = await metrics(cdp)
  return {
    task: +((((b.TaskDuration || 0) - (a.TaskDuration || 0)) / (ms / 1000)) * 100).toFixed(1),
    script: +((((b.ScriptDuration || 0) - (a.ScriptDuration || 0)) / (ms / 1000)) * 100).toFixed(1)
  }
}

/** 页面侧：读渲染开关、canvas 指纹；并支持「改一次镜头」 */
const PAGE_HELPERS = `
  window.__v = {
    state: function () {
      const v = window.viewer
      return v ? { loop: !!v.useDefaultRenderLoop, destroyed: typeof v.isDestroyed === 'function' ? v.isDestroyed() : false } : null
    },
    // canvas 指纹：取 4 个采样点的像素和，比整张 dataURL 便宜且足够判断「有没有变」
    fingerprint: function () {
      const v = window.viewer
      if (!v || !v.canvas) return null
      const src = v.canvas
      const c = document.createElement('canvas')
      c.width = 64; c.height = 64
      const ctx = c.getContext('2d')
      ctx.drawImage(src, 0, 0, 64, 64)
      const d = ctx.getImageData(0, 0, 64, 64).data
      let sum = 0, hash = 0
      for (let i = 0; i < d.length; i += 4) {
        sum += d[i] + d[i + 1] + d[i + 2]
        hash = (hash * 31 + d[i] + d[i + 1] * 3 + d[i + 2] * 7) >>> 0
      }
      return { sum: sum, hash: hash }
    },
    // 主动把镜头拉远一点，制造一次「画面必须重画」的机会
    nudge: function () {
      const v = window.viewer
      if (!v) return false
      v.camera.zoomOut(v.camera.positionCartographic.height * 0.15 + 100)
      return true
    }
  };
`

async function snapshot (cdp, label, observeMs) {
  const st = await cdp.evalJs('JSON.stringify(window.__v.state())')
  const before = await cdp.evalJs('JSON.stringify(window.__v.fingerprint())')
  await cdp.evalJs('window.__v.nudge()')
  const busyInfo = await busy(cdp, observeMs)
  const after = await cdp.evalJs('JSON.stringify(window.__v.fingerprint())')
  const s = JSON.parse(st)
  const b = JSON.parse(before)
  const a = JSON.parse(after)
  const moved = b && a ? (b.hash !== a.hash) : null
  console.log(`\n--- [${label}] ---`)
  console.log(`  viewer: ${s ? 'useDefaultRenderLoop=' + s.loop : '不存在'}   主线程忙 ${busyInfo.task}%  JS ${busyInfo.script}%`)
  console.log(`  改镜头后画面是否变化: ${moved === null ? 'n/a' : moved ? '变了（地图在渲染）' : '没变（画面已冻结）'}`)
  return { loop: s && s.loop, task: busyInfo.task, moved: moved }
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
    await cdp.send('Performance.enable')
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: PAGE_HELPERS })

    // ---- 登录 ----
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    let filled = false
    for (let i = 0; i < 120; i++) {
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
      if (r === 'OK') { filled = true; break }
    }
    if (!filled) throw new Error('登录页表单没出现（dev-server 还没编译完？）')
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click()
      return !!b
    })()`)

    // 等进首页 + Cesium 就绪
    let ready = false
    for (let i = 0; i < 60; i++) {
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
    if (!ready) throw new Error('没能进入大屏首页 / Cesium 未就绪')
    console.log('### 已进入首页，Cesium 就绪')
    await sleep(4000)

    const home1 = await snapshot(cdp, '首页（地图应当渲染）', 6000)

    // ---- 真实点击顶栏「档案管理」----
    const clicked = await cdp.evalJs(`(() => {
      const items = Array.from(document.querySelectorAll('.screen-header__nav-item'))
      const target = items.find(el => (el.textContent || '').indexOf('档案管理') > -1)
      if (!target) return 'NOT_FOUND:' + items.length
      target.click()
      return 'CLICKED'
    })()`)
    console.log('\n### 点击顶栏「档案管理」: ' + clicked)
    await sleep(6000)
    const archive = await snapshot(cdp, '档案管理（地图应被暂停）', 6000)

    // ---- 点回首页 ----
    const back = await cdp.evalJs(`(() => {
      const items = Array.from(document.querySelectorAll('.screen-header__nav-item'))
      const target = items.find(el => (el.textContent || '').indexOf('首页') > -1)
      if (!target) return 'NOT_FOUND'
      target.click()
      return 'CLICKED'
    })()`)
    console.log('\n### 点击顶栏「首页」: ' + back)
    await sleep(6000)
    const home2 = await snapshot(cdp, '回到首页（地图应恢复渲染）', 6000)

    // ---- 结论 ----
    console.log('\n### 判定')
    const checks = [
      ['首页地图在渲染（画面会变）', home1.moved === true],
      ['进档案管理后渲染循环被停掉', archive.loop === false],
      ['档案管理页画面已冻结（改镜头也不变）', archive.moved === false],
      ['档案管理页主线程占用 < 15%', archive.task < 15],
      ['回首页后渲染循环恢复', home2.loop === true],
      ['回首页后地图重新在渲染（画面会变）', home2.moved === true]
    ]
    let pass = 0
    checks.forEach(([name, ok]) => { if (ok) pass++; console.log(`  ${ok ? '✅' : '❌'} ${name}`) })
    console.log(`\n  通过 ${pass}/${checks.length}   （首页占用 ${home1.task}% → 档案 ${archive.task}% → 首页 ${home2.task}%）`)
    if (pass !== checks.length) process.exitCode = 2
  } catch (e) {
    console.error('VERIFY ERROR:', (e && e.stack) || e)
    process.exitCode = 1
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { spawn('taskkill', ['/PID', String(edge.pid), '/T', '/F'], { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(process.exitCode || 0), 800)
  }
})()
