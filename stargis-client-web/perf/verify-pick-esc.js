/**
 * 专项验证：选取模式下按 Esc 只应「取消绘制」，不能连表单一起关掉
 * ------------------------------------------------------------------
 * 背景（审查提出、且已修）：焦点在进入选取模式时仍留在被隐藏的表单弹窗里，
 * 而 ScreenModal 的 Escape 处理挂在它自己的 dialog 上，**先于** window 的
 * 冒泡监听触发 —— 如果 ScreenPickStage 用冒泡监听，按 Esc 会
 * 「取消绘制 + 把整张填了一半的表单一起关掉」。
 * 修法是 ScreenPickStage 改用**捕获阶段**监听并 preventDefault + stopPropagation。
 *
 * 用法：node perf/verify-pick-esc.js [http://localhost:3000]
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const USER_DATA = os.tmpdir() + '\\edge-verify-esc-' + Date.now()
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
  /** 发真实按键（走输入域，和用户按键盘同一条路径） */
  async pressKey (key, code, windowsVirtualKeyCode) {
    const base = { key, code, windowsVirtualKeyCode, nativeVirtualKeyCode: windowsVirtualKeyCode }
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyDown' }, base))
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyUp' }, base))
  }
}

const results = []
function check (name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? '  —— ' + detail : ''}`)
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
        u.value = 'admin'; u.dispatchEvent(new Event('input', { bubbles: true }))
        p.value = '123456'; p.dispatchEvent(new Event('input', { bubbles: true }))
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
    console.log('### 已登录')
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
    await sleep(1200)

    // 在表单里填一个值，用来证明 Esc 之后表单内容没丢
    await cdp.evalJs(`(() => {
      const el = document.getElementById('lf-dkmc')
      if (!el) return false
      el.value = 'ESC-测试地块'
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('blur', { bubbles: true }))
      return true
    })()`)
    await sleep(500)

    // 进选取模式
    await cdp.evalJs(`(() => { const b = document.querySelector('.land-form__map-pick-btn'); if (b && !b.disabled) b.click(); return true })()`)
    for (let i = 0; i < 20; i++) {
      await sleep(500)
      if (await cdp.evalJs(`!!document.querySelector('.pick-stage')`)) break
    }
    await sleep(1500)
    check('覆盖层已出现', await cdp.evalJs(`!!document.querySelector('.pick-stage')`))
    check('进入前表单里已填好一个值', await cdp.evalJs(`(document.getElementById('lf-dkmc')||{}).value === 'ESC-测试地块'`))

    // ★ 按 Esc
    await cdp.pressKey('Escape', 'Escape', 27)
    await sleep(2000)

    const stageGone = !(await cdp.evalJs(`!!document.querySelector('.pick-stage')`))
    check('按 Esc 退出了选取模式（覆盖层消失）', stageGone)

    const formStillOpen = await cdp.evalJs(`(() => {
      const m = document.querySelector('.screen-modal-root > .screen-modal')
      if (!m) return false
      const cs = getComputedStyle(m)
      return cs.visibility !== 'hidden' && cs.display !== 'none'
    })()`)
    check('★ 按 Esc **没有**把表单一起关掉（表单仍然可见）', formStillOpen)

    const val = await cdp.evalJs(`((document.getElementById('lf-dkmc')||{}).value)`)
    check('★ 表单里已填的内容还在（没被误关导致丢失）', val === 'ESC-测试地块', String(val))

    const bodyFlag = await cdp.evalJs(`document.body.classList.contains('is-picking-map')`)
    check('body 标记已清除', bodyFlag === false)

    const failed = results.filter((r) => !r.ok)
    console.log('\n================================================')
    console.log(`断言：${results.length - failed.length} / ${results.length} 通过`)
    if (failed.length) {
      console.log('失败项：' + failed.map((f) => f.name).join('；'))
      process.exitCode = 1
    }
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
