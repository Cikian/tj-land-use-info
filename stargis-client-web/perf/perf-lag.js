/**
 * 卡顿诊断探针（大屏子页面）
 * ===================================================================
 * 目标：回答「为什么整个系统都卡」——分别量化下面三个候选原因，
 *       并做 A/B 对照（运行时把它们逐个关掉，再看同一组指标）。
 *
 *   A. Cesium 渲染循环一直在跑（档案/提级论证页面上仍然 60fps 重绘整屏）
 *   B. .screen-panel 等大面积 backdrop-filter: blur(12px)（每帧重新模糊背景）
 *   C. 样式表 / CSS 规则规模、DOM 规模（样式匹配成本）
 *
 * 用法（先确保 dev-server 在跑，默认 http://localhost:3000）：
 *   node perf/perf-lag.js http://localhost:3000 /screen/archive 12000
 *   node perf/perf-lag.js http://localhost:3000 /screen/review?tab=query 12000
 *
 * 只依赖项目里已有的 ws；headless Edge 用 --enable-gpu 走真实 GPU，
 * 否则 WebGL 会退化到 SwiftShader，绝对帧率没有参考价值（脚本会打印 renderer 以便判断）。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const ROUTE = process.argv[3] || '/screen/archive'
const OBSERVE_MS = Number(process.argv[4] || 12000)
const USERNAME = process.env.PERF_USER || 'admin'
const PASSWORD = process.env.PERF_PWD || '123456'
const USER_DATA = os.tmpdir() + '\\edge-perf-lag-' + Date.now()
// 每次跑用不同端口：连着跑两次时上一次的 msedge 还没退干净，
// 复用同一个调试端口会拿到已经关闭的 WebSocket（readyState 3）。
const PORT = 9400 + Math.floor(Math.random() * 400)

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
    this.ws = ws; this.id = 0; this.pending = new Map(); this.handlers = []
    ws.on('message', (raw) => {
      const msg = JSON.parse(raw)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id); this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      } else if (msg.method) this.handlers.forEach((h) => h(msg))
    })
  }
  on (fn) { this.handlers.push(fn) }
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
    if (r.exceptionDetails) {
      return { __error: (r.exceptionDetails.exception && r.exceptionDetails.exception.description) || r.exceptionDetails.text }
    }
    return r.result.value
  }
}

async function metrics (cdp) {
  const snap = await cdp.send('Performance.getMetrics')
  const m = {}
  snap.metrics.forEach((x) => (m[x.name] = x.value))
  return m
}

/** 各进程累计 CPU 时间（秒）：gpu / renderer / browser —— 用来量化合成与模糊的 GPU 成本 */
let browserCdp = null

async function procCpu (cdp) {
  const target = browserCdp || cdp
  try {
    const info = await target.send('SystemInfo.getProcessInfo')
    const out = {}
    ;(info.processInfo || []).forEach((p) => {
      // type: browser | renderer | gpu | utility | plugin | other | ppapi
      out[p.type] = (out[p.type] || 0) + (p.cpuTime || 0)
    })
    return out
  } catch (e) {
    return null
  }
}

/**
 * 浏览器级 CDP 连接。
 * SystemInfo 是 browser 域的，挂在 page 会话上调用会失败，
 * 所以单独连一次 /json/version 暴露的 webSocketDebuggerUrl。
 */
async function openBrowserCdp () {
  const v = JSON.parse(await httpGet('/json/version'))
  if (!v.webSocketDebuggerUrl) return null
  const bws = new WebSocket(v.webSocketDebuggerUrl, { perMessageDeflate: false })
  await new Promise((res, rej) => { bws.on('open', res); bws.on('error', rej) })
  const bcdp = new CDP(bws)
  try { await bcdp.send('SystemInfo.getProcessInfo') } catch (e) { return null }
  return { cdp: bcdp, ws: bws }
}

/** 观察窗口内：CPU 分布 + 帧率 + 强制回流耗时 */
async function observe (cdp, label, ms) {
  await cdp.evalJs('window.__lag.reset()')
  const before = await metrics(cdp)
  const cpuBefore = await procCpu(cdp)
  await sleep(ms)
  const after = await metrics(cdp)
  const cpuAfter = await procCpu(cdp)
  const dt = ms / 1000
  const pct = (k) => (((after[k] || 0) - (before[k] || 0)) / dt * 100)
  let gpuPct = null
  let rendererPct = null
  if (cpuBefore && cpuAfter) {
    gpuPct = (((cpuAfter.gpu || 0) - (cpuBefore.gpu || 0)) / dt * 100)
    rendererPct = (((cpuAfter.renderer || 0) - (cpuBefore.renderer || 0)) / dt * 100)
  }

  // 「操作滞后」的直接指标：事件循环延迟。
  // 用 60 跳 setTimeout(0) 链测每一跳实际等了多久——主线程被占住时，
  // 用户的点击/输入回调就要排队这么久（Chromium 对嵌套 setTimeout 有 4ms 地板，
  // 所以基线约 4ms，明显大于 4ms 就是被阻塞）。
  const loop = await cdp.evalJs(`new Promise(function (res) {
    const delays = []
    let last = performance.now(), n = 0
    const step = function () {
      const now = performance.now()
      delays.push(now - last)
      last = now
      if (++n >= 60) {
        delays.sort(function (a, b) { return a - b })
        res(JSON.stringify({
          p50: +delays[30].toFixed(2),
          p90: +delays[54].toFixed(2),
          max: +delays[59].toFixed(2)
        }))
      } else { setTimeout(step, 0) }
    }
    setTimeout(step, 0)
  })`, true)
  const loopDelay = typeof loop === 'string' ? JSON.parse(loop) : loop

  // 交互延迟的代理指标：读一次 offsetHeight 会强制样式重算 + 布局，
  // 取 20 次的中位数——主线程越忙，这个值越大（用户感知的「点了没反应」）。
  const reflowMs = await cdp.evalJs(`(() => {
    const samples = []
    for (let i = 0; i < 20; i++) {
      const t0 = performance.now()
      document.documentElement.offsetHeight
      void document.documentElement.offsetWidth
      samples.push(performance.now() - t0)
    }
    samples.sort((a, b) => a - b)
    return +samples[10].toFixed(2)
  })()`)
  await cdp.evalJs(`window.__lag.reflowMs = ${reflowMs}`)

  const data = await cdp.evalJs(`(() => {
    const f = window.__lag.frames
    const gaps = []
    for (let i = 1; i < f.length; i++) gaps.push(f[i] - f[i - 1])
    gaps.sort((a, b) => a - b)
    const pick = (p) => gaps.length ? +gaps[Math.min(gaps.length - 1, Math.floor(gaps.length * p))].toFixed(1) : -1
    return JSON.stringify({
      rafCount: f.length,
      fps: +(f.length / ${dt}).toFixed(1),
      gapP50: pick(0.5), gapP90: pick(0.9), gapP99: pick(0.99), gapMax: pick(0.999),
      longtasks: window.__lag.longtasks.slice().sort((a, b) => b - a).slice(0, 8),
      longtaskTotal: window.__lag.longtasks.reduce((a, b) => a + b, 0),
      reflowMs: window.__lag.reflowMs
    })
  })()`)

  const d = typeof data === 'string' ? JSON.parse(data) : data
  console.log(`\n--- [${label}] 观察 ${dt}s ---`)
  console.log(`  任务(主线程忙) ${pct('TaskDuration').toFixed(1)}%  Script ${pct('ScriptDuration').toFixed(1)}%  ` +
    `样式重算 ${pct('RecalcStyleDuration').toFixed(1)}%  布局 ${pct('LayoutDuration').toFixed(1)}%`)
  if (gpuPct !== null) {
    console.log(`  进程 CPU：GPU 进程 ${gpuPct.toFixed(1)}%  渲染进程合计 ${rendererPct.toFixed(1)}%（含上面主线程）`)
  }
  console.log(`  GPU/合成帧(约): ${d.fps} fps (窗口内 rAF ${d.rafCount} 次)  ` +
    `帧间隔 p50=${d.gapP50}ms p90=${d.gapP90}ms p99=${d.gapP99}ms`)
  console.log(`  Long Task ${d.longtasks.length ? d.longtasks.length + ' 个, 最长 ' + d.longtasks[0] + 'ms, 合计 ' + d.longtaskTotal + 'ms' : '无'}`)
  console.log(`  强制回流(读 offsetHeight) 中位: ${d.reflowMs}ms`)
  console.log(`  事件循环延迟(60 跳 setTimeout0) p50=${loopDelay.p50}ms p90=${loopDelay.p90}ms max=${loopDelay.max}ms`)
  console.log(`  增量: 样式重算 ${((after.RecalcStyleCount || 0) - (before.RecalcStyleCount || 0))} 次, ` +
    `布局 ${((after.LayoutCount || 0) - (before.LayoutCount || 0))} 次, ` +
    `JS堆 ${((after.JSHeapUsedSize - before.JSHeapUsedSize) / 1048576).toFixed(1)}MB`)
  return Object.assign({}, d, { loopDelay, gpuPct, taskPct: pct('TaskDuration'), scriptPct: pct('ScriptDuration') })
}

;(async () => {
  const edge = spawn(EDGE, [
    '--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + USER_DATA,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    '--window-size=1920,1080', '--enable-gpu', '--use-angle=d3d11',
    '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader',
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

    const bc = await openBrowserCdp()
    if (bc) { browserCdp = bc.cdp; console.log('### 已连接 browser 级 CDP（可读 GPU 进程 CPU）') } else { console.log('### ⚠ browser 级 CDP 不可用，跳过 GPU 进程指标') }

    const errs = []
    const netFail = []
    cdp.on((msg) => {
      if (msg.method === 'Runtime.exceptionThrown') {
        errs.push(((msg.params.exceptionDetails.exception || {}).description || msg.params.exceptionDetails.text || '').slice(0, 160))
      } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        errs.push('console.error: ' + msg.params.args.map((a) => String(a.value || a.description || '')).join(' ').slice(0, 160))
      } else if (msg.method === 'Network.responseReceived') {
        const r = msg.params.response
        if (r.status >= 400 && /login|Login|sceneCreateApi/.test(r.url)) {
          netFail.push(r.status + ' ' + r.url.replace(BASE, '').slice(0, 120))
        }
      } else if (msg.method === 'Network.loadingFailed') {
        netFail.push('FAILED ' + msg.params.errorText)
      }
    })

    await cdp.send('Runtime.enable')
    await cdp.send('Page.enable')
    await cdp.send('Performance.enable')
    await cdp.send('Network.enable')

    await cdp.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `
        window.__lag = {
          frames: [], longtasks: [], reflowMs: -1,
          reset: function () { this.frames = []; this.longtasks = []; }
        };
        (function tick (ts) { window.__lag.frames.push(ts); requestAnimationFrame(tick) })(performance.now());
        try {
          new PerformanceObserver(function (l) {
            l.getEntries().forEach(function (e) { window.__lag.longtasks.push(Math.round(e.duration)) })
          }).observe({ entryTypes: ['longtask'] })
        } catch (e) {}
      `
    })

    // ---------------- 登录 ----------------
    console.log('### 登录 ' + BASE + '/#/user/login  (账号 ' + USERNAME + ')')
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })

    // 等登录表单真的出现再填（dev-server 首编译可能要几十秒，固定 sleep 会误判）
    let fill = 'NO_INPUTS'
    for (let i = 0; i < 90; i++) {
      await sleep(1000)
      fill = await cdp.evalJs(`(() => {
        const inputs = Array.from(document.querySelectorAll('input'))
        const u = inputs.find(i => i.type !== 'password')
        const p = inputs.find(i => i.type === 'password')
        if (!u || !p) return 'NO_INPUTS:' + inputs.length
        const setVal = (el, v) => {
          el.value = v
          el.dispatchEvent(new Event('input', { bubbles: true }))
          el.dispatchEvent(new Event('change', { bubbles: true }))
        }
        setVal(u, ${JSON.stringify(USERNAME)})
        setVal(p, ${JSON.stringify(PASSWORD)})
        return 'FILLED'
      })()`)
      if (fill === 'FILLED') break
    }
    console.log('  填表: ' + JSON.stringify(fill) + (fill === 'FILLED' ? '' : '（等待超过 90s）'))
    await sleep(500)

    await cdp.evalJs(`(() => {
      // antd 会给「两个汉字」的按钮中间插一个空格（登 录），所以比较前先去空白
      const norm = (t) => String(t || '').replace(/\\s/g, '')
      const btns = Array.from(document.querySelectorAll('button'))
      const b = btns.find(x => norm(x.textContent) === '登录')
      if (b) b.click()
      return !!b
    })()`)

    // 等待进入大屏（租户弹窗出现就点确认）
    let loggedIn = false
    for (let i = 0; i < 40; i++) {
      await sleep(1000)
      const st = await cdp.evalJs(`(() => {
        const norm = (t) => String(t || '').replace(/\\s/g, '')
        const okBtn = Array.from(document.querySelectorAll('.ant-modal button'))
          .find(b => norm(b.textContent) === '确认')
        if (okBtn) okBtn.click()
        return JSON.stringify({ hash: location.hash, hasOk: !!okBtn, hasCesium: !!window.viewer })
      })()`)
      if (st && st.indexOf('__error') === -1) {
        const s = JSON.parse(st)
        if (s.hash && s.hash.indexOf('login') === -1) { loggedIn = true; console.log('  进入: ' + s.hash + '  cesium=' + s.hasCesium); break }
      }
    }
    if (!loggedIn) {
      const dump = await cdp.evalJs(`JSON.stringify({ hash: location.hash, text: (document.body.innerText||'').slice(0,300) })`)
      console.log('  ⚠ 未确认登录成功: ' + dump)
      if (netFail.length) console.log('  网络异常: ' + JSON.stringify(netFail.slice(0, 8), null, 0))
      if (errs.length) console.log('  页面报错: ' + JSON.stringify(errs.slice(0, 5)))
      throw new Error('登录失败 —— 后面的数字会是登录页的，没有参考价值，已中止')
    }

    // ---------------- 打开目标路由 ----------------
    // ⚠ 必须走一次真实的整页加载：views/screen/index.vue 只在 data() 里读一次路由，
    //   而 /、/screen/archive、/screen/review 共用同一个组件实例，
    //   只改 hash 是同文档导航，组件不会重建，activeMenu 会一直停在 home。
    console.log('\n### 打开 ' + ROUTE + '（整页加载）')
    await cdp.send('Page.navigate', { url: 'about:blank' })
    await sleep(600)
    await cdp.send('Page.navigate', { url: BASE + '/#' + ROUTE })
    await sleep(12000)

    const info = await cdp.evalJs(`(() => {
      let cssRules = 0
      for (let i = 0; i < document.styleSheets.length; i++) {
        try { cssRules += document.styleSheets[i].cssRules.length } catch (e) {}
      }
      const c = document.createElement('canvas')
      const gl = c.getContext('webgl') || c.getContext('experimental-webgl')
      let renderer = 'n/a'
      if (gl) {
        const ext = gl.getExtension('WEBGL_debug_renderer_info')
        renderer = ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER)
      }
      const blurred = Array.from(document.querySelectorAll('*')).filter(el => {
        const s = getComputedStyle(el)
        return (s.backdropFilter && s.backdropFilter !== 'none') || (s.webkitBackdropFilter && s.webkitBackdropFilter !== 'none')
      })
      const nav = document.querySelector('.archive-screen__nav, .escalation-screen__nav')
      const navStyle = nav ? getComputedStyle(nav) : null
      return JSON.stringify({
        hash: location.hash,
        domNodes: document.getElementsByTagName('*').length,
        panels: document.querySelectorAll('.screen-panel').length,
        navBackdrop: navStyle ? (navStyle.backdropFilter || navStyle.webkitBackdropFilter || '(empty)') : '(no nav)',
        styleSheets: document.styleSheets.length,
        cssRules: cssRules,
        canvases: document.getElementsByTagName('canvas').length,
        hasViewer: !!window.viewer,
        renderLoop: window.viewer ? !!window.viewer.useDefaultRenderLoop : null,
        webglRenderer: renderer,
        backdropEls: blurred.length,
        backdropSample: blurred.slice(0, 6).map(el => el.className && String(el.className).slice(0, 40))
      })
    })()`)
    const inf = typeof info === 'string' ? JSON.parse(info) : info
    console.log('\n### 页面结构')
    console.log('  hash=' + inf.hash + '  DOM ' + inf.domNodes + ' 节点 | screen-panel ' + inf.panels + ' 个 | 样式表 ' + inf.styleSheets + ' 个 / CSS 规则 ' + inf.cssRules + ' 条')
    console.log('  侧栏 backdrop-filter 计算值: ' + inf.navBackdrop)
    console.log('  canvas ' + inf.canvases + ' | window.viewer=' + inf.hasViewer + ' | useDefaultRenderLoop=' + inf.renderLoop)
    console.log('  WebGL renderer: ' + inf.webglRenderer)
    console.log('  backdrop-filter 元素 ' + inf.backdropEls + ' 个: ' + JSON.stringify(inf.backdropSample))

    // ---------------- 基准观察 ----------------
    const base = await observe(cdp, '基准：现状（Cesium 渲染循环开 + backdrop-filter 开）', OBSERVE_MS)

    // ---------------- A/B-1：停掉 Cesium 渲染循环 ----------------
    const stopped = await cdp.evalJs(`(() => {
      if (!window.viewer) return 'NO_VIEWER'
      window.viewer.useDefaultRenderLoop = false
      const cv = window.viewer.canvas
      if (cv) cv.style.visibility = 'hidden'
      return 'STOPPED'
    })()`)
    console.log('\n### A/B-1 停止 Cesium 渲染循环: ' + stopped)
    await sleep(1500)
    const noCesium = await observe(cdp, 'A/B-1：Cesium 渲染循环已停', OBSERVE_MS)

    // ---------------- A/B-2：关掉 backdrop-filter ----------------
    const styleTag = await cdp.evalJs(`(() => {
      const s = document.createElement('style')
      s.id = '__no_blur'
      s.textContent = '*, *::before, *::after { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; }'
      document.head.appendChild(s)
      return 'INJECTED'
    })()`)
    console.log('\n### A/B-2 注入 backdrop-filter:none: ' + styleTag)
    await sleep(1500)
    const noBlur = await observe(cdp, 'A/B-2：Cesium 已停 + 无 backdrop-filter', OBSERVE_MS)

    // ---------------- A/B-3：恢复 Cesium，只关 backdrop-filter ----------------
    await cdp.evalJs(`(() => {
      if (window.viewer) { window.viewer.useDefaultRenderLoop = true; if (window.viewer.canvas) window.viewer.canvas.style.visibility = 'visible' }
      const s = document.getElementById('__no_blur'); if (s) s.remove()
      return 'RESTORED'
    })()`)
    await sleep(1500)
    const cesiumNoBlur = await observe(cdp, 'A/B-3：Cesium 开 + 无 backdrop-filter', OBSERVE_MS)

    console.log('\n### 汇总（同一路由，逐项关闭后的主线程占用 / 帧率）')
    const rows = [
      ['现状', base],
      ['停 Cesium', noCesium],
      ['停 Cesium + 无 blur', noBlur],
      ['只关 blur', cesiumNoBlur]
    ]
    rows.forEach(([k, v]) => console.log(
      `  ${k.padEnd(22)} 主线程忙=${String(v.taskPct.toFixed(1)).padStart(5)}%  JS=${String(v.scriptPct.toFixed(1)).padStart(5)}%  ` +
      `GPU进程=${v.gpuPct === null ? ' n/a ' : String(v.gpuPct.toFixed(1)).padStart(5) + '%'}  ` +
      `事件循环延迟 p50=${String(v.loopDelay.p50).padStart(6)}ms p90=${String(v.loopDelay.p90).padStart(7)}ms max=${String(v.loopDelay.max).padStart(7)}ms`))

    if (errs.length) {
      console.log('\n### 运行时报错(前 10 条)')
      errs.slice(0, 10).forEach((e) => console.log('  ' + e))
    }
  } catch (e) {
    console.error('PROBE ERROR:', (e && e.stack) || e)
  } finally {
    try { if (ws) ws.close() } catch (e) {}
    try { spawn('taskkill', ['/PID', String(edge.pid), '/T', '/F'], { stdio: 'ignore' }) } catch (e) {}
    setTimeout(() => process.exit(0), 800)
  }
})()
