/**
 * 版式调试探针：逐个页签 dump「内容区 + 其第一个子元素」的真实几何与计算样式。
 * 用途：确认设计画布的自适应尺寸（--stage-w / --stage-h）与真实缩放 k，
 *       从而判断是「脚本换算错了」还是「版式真的坏了」。
 * 用法：node perf/debug-layout.js [http://localhost:3001]
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const USER_DATA = os.tmpdir() + '\\edge-debug-layout-' + Date.now()
const PORT = 13300 + Math.floor(Math.random() * 300)
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

    await send('Page.navigate', { url: BASE + '/#/user/login' })
    for (let i = 0; i < 100; i++) {
      await sleep(1000)
      const r = await evalJs(`(() => {
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
    await evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)
    for (let i = 0; i < 60; i++) {
      await sleep(1000)
      const s = JSON.parse(await evalJs(`JSON.stringify({ hash: location.hash, viewer: !!window.viewer })`))
      if (s.hash.indexOf('login') === -1 && s.viewer) break
    }
    console.log('### 已登录')

    // 画布坐标系
    const geo = await evalJs(`(() => {
      const c = document.querySelector('.screen-stage__canvas')
      if (!c) return 'no-canvas'
      const cs = getComputedStyle(c)
      const r = c.getBoundingClientRect()
      return JSON.stringify({
        viewport: [window.innerWidth, window.innerHeight],
        canvasRect: [Math.round(r.width), Math.round(r.height)],
        stageW: cs.getPropertyValue('--stage-w').trim(),
        stageH: cs.getPropertyValue('--stage-h').trim(),
        transform: cs.transform
      })
    })()`)
    console.log('=== 画布坐标系 ===')
    console.log(geo)

    await evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3000)

    const tabs = ['经营性用地批量导入', '经营性用地信息录入', '配套地块数据录入', '配套信息批量导入', '配套附件管理', '数据更新与移除']
    for (const tab of tabs) {
      await evalJs(`(() => {
        const norm = t => String(t||'').replace(/\\s/g,'')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(tab)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(2200)
      const dump = await evalJs(`(() => {
        const c = document.querySelector('.screen-stage__canvas')
        const cs = getComputedStyle(c)
        const k = c.getBoundingClientRect().height / parseFloat(cs.getPropertyValue('--stage-h'))
        const cr = c.getBoundingClientRect()
        const DW = parseFloat(cs.getPropertyValue('--stage-w'))
        const DH = parseFloat(cs.getPropertyValue('--stage-h'))
        const box = function (el) {
          if (!el) return null
          const r = el.getBoundingClientRect()
          return {
            raw: [Math.round(r.left - cr.left), Math.round(r.top - cr.top), Math.round(r.width), Math.round(r.height)],
            design: [Math.round((r.left - cr.left) / k), Math.round((r.top - cr.top) / k), Math.round(r.width / k), Math.round(r.height / k)]
          }
        }
        const body = document.querySelector('.data-screen__body')
        const child = body ? body.firstElementChild : null
        const ccs = child ? getComputedStyle(child) : null
        return JSON.stringify({
          k: Math.round(k * 10000) / 10000,
          design: [Math.round(DW), Math.round(DH)],
          split: body ? body.classList.contains('is-split') : null,
          body: box(body),
          child: child ? {
            tag: child.tagName.toLowerCase(),
            cls: String(child.className).slice(0, 60),
            box: box(child),
            position: ccs.position,
            transform: ccs.transform,
            margin: ccs.margin,
            padding: ccs.padding,
            display: ccs.display,
            flex: ccs.flex,
            height: ccs.height,
            width: ccs.width,
            alignSelf: ccs.alignSelf
          } : null,
          childCount: body ? body.children.length : -1
        })
      })()`)
      console.log(`\n--- ${tab} ---`)
      console.log(dump)
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
