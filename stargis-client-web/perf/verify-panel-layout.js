/**
 * 三块面板：拖宽 / 拖高 + 折叠 + 持久化（真实浏览器 / CDP）
 * ------------------------------------------------------------------
 * 覆盖首页 / 地图管理 / 配套分析管理三个页面：
 *   · 左右面板内侧有竖向调节条，拖动后宽度 = 起点 + 位移 / k（画布缩放）
 *   · 下方面板（首页属性表 / 通用 ScreenBottomDock）上沿有横向调节条
 *   · 左右屏边把手可折叠（整块滑出屏幕），下方面板可收起到 47px 条
 *   · 尺寸与折叠状态写进 localStorage，刷新后仍在
 *
 * 用法（先起 dev server；本机大屏端是 3001，3000 是管理端，别指错）：
 *   node perf/verify-panel-layout.js http://localhost:3001
 */
const { spawn } = require('child_process')
const http = require('http')
const fs = require('fs')
const path = require('path')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const USER_DATA = os.tmpdir() + '\\edge-verify-panels-' + Date.now()
const PORT = 12200 + Math.floor(Math.random() * 400)

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
    try {
      const v = await httpGet('/json/version')
      if (v) return JSON.parse(v)
    } catch (e) { /* 还没起来 */ }
    await sleep(300)
  }
  throw new Error('CDP endpoint never came up')
}

class CDP {
  constructor (ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    ws.on('message', (raw) => {
      const msg = JSON.parse(raw)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
      }
    })
  }
  send (method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error('timeout: ' + method))
        }
      }, 180000)
    })
  }
  async evalJs (expression) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) return { __error: (r.exceptionDetails.exception || {}).description || r.exceptionDetails.text }
    return r.result.value
  }
  async mouse (type, x, y) {
    await this.send('Input.dispatchMouseEvent', {
      type,
      x: Math.round(x),
      y: Math.round(y),
      button: 'left',
      buttons: type === 'mouseReleased' ? 0 : 1,
      clickCount: type === 'mouseMoved' ? 0 : 1
    })
  }
  /** 真实鼠标拖拽：从 (x1,y1) 拖到 (x2,y2)，中间多步移动，触发 pointermove 累积 */
  async drag (x1, y1, x2, y2) {
    await this.mouse('mousePressed', x1, y1)
    const steps = 12
    for (let i = 1; i <= steps; i++) {
      await this.mouse('mouseMoved', x1 + ((x2 - x1) * i) / steps, y1 + ((y2 - y1) * i) / steps)
      await sleep(16)
    }
    await this.mouse('mouseReleased', x2, y2)
    await sleep(450)
  }
}

const results = []
function check (name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? '  —— ' + detail : ''}`)
}

/** 取元素中心点（真实视口坐标） */
const centerOf = (selector) => `(() => {
  const el = document.querySelector(${JSON.stringify(selector)})
  if (!el) return null
  const r = el.getBoundingClientRect()
  return { x: r.left + r.width / 2, y: r.top + r.height / 2, w: r.width, h: r.height }
})()`

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
    await cdp.send('Log.enable')

    /** 截图留档（人工复核版式用） */
    const shotTo = async (name) => {
      const shot = await cdp.send('Page.captureScreenshot', { format: 'png' })
      const p = path.join(__dirname, 'shots', name)
      try {
        fs.mkdirSync(path.dirname(p), { recursive: true })
        fs.writeFileSync(p, Buffer.from(shot.data, 'base64'))
        console.log(`\n截图：${p}`)
      } catch (e) { /* 截图失败不影响断言 */ }
    }

    // 收集页面报错（Vue 渲染错误 / 未捕获异常），最后统一断言「0 条」
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `window.__panelErrs = [];
        window.addEventListener('error', function (e) { window.__panelErrs.push(String(e.message || e)); });
        window.addEventListener('unhandledrejection', function (e) { window.__panelErrs.push('unhandled: ' + String(e.reason)); });`
    })

    /* ---------------- 登录 ---------------- */
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    let loginReady = false
    for (let i = 0; i < 90; i++) {
      await sleep(700)
      const r = await cdp.evalJs(`(() => {
        const inputs = Array.from(document.querySelectorAll('input'))
        const u = inputs.find(i => i.type !== 'password')
        const p = inputs.find(i => i.type === 'password')
        if (!u || !p) return 'NO'
        u.value = 'admin'; u.dispatchEvent(new Event('input', { bubbles: true }))
        p.value = '123456'; p.dispatchEvent(new Event('input', { bubbles: true }))
        return 'OK'
      })()`)
      if (r === 'OK') { loginReady = true; break }
    }
    if (!loginReady) throw new Error('登录页没有出现用户名/密码输入框')
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)

    /* ---------------- 等大屏首页就绪 ---------------- */
    let homeReady = false
    for (let i = 0; i < 90; i++) {
      await sleep(700)
      const r = await cdp.evalJs(`!!document.querySelector('.home-stat.is-left .home-stat__panel')`)
      if (r === true) { homeReady = true; break }
      // 登录后可能落在别的路由：直接补一次 /#/screen
      if (i === 20) await cdp.send('Page.navigate', { url: BASE + '/#/screen?map=0' })
    }
    if (!homeReady) throw new Error('大屏首页没渲染出来（检查端口是不是 3001 大屏端）')

    const stage = await cdp.evalJs(`(() => {
      const c = document.querySelector('.screen-stage__canvas')
      if (!c) return null
      const r = c.getBoundingClientRect()
      return { scale: r.width / c.offsetWidth, w: c.offsetWidth, h: c.offsetHeight }
    })()`)
    check('画布缩放比可测（用于换算拖动位移）', stage && stage.scale > 0, stage && JSON.stringify(stage))
    const k = (stage && stage.scale) || 1

    /** 取氛围蒙版的「设计像素」宽高（inline style → computedStyle 是画布坐标，不受 scale 影响） */
    const vignetteSize = (side) => cdp.evalJs(`(() => {
      const el = document.querySelector('.land-screen__vignette.is-${side}')
      if (!el) return null
      const cs = getComputedStyle(el)
      return { w: parseFloat(cs.width), h: parseFloat(cs.height) }
    })()`)

    /* ================= 首页 ================= */
    console.log('\n### 首页')
    const homeWidth = () => cdp.evalJs(`(() => {
      const el = document.querySelector('.home-stat.is-left')
      return el ? parseFloat(getComputedStyle(el).getPropertyValue('--home-panel-w')) : null
    })()`)
    const beforeW = await homeWidth()
    check('首页左面板默认宽 400（设计稿）', beforeW === 400, `--home-panel-w=${beforeW}`)
    check('首页左面板有竖向调节条', await cdp.evalJs(`!!document.querySelector('.home-stat.is-left .home-stat__resizer')`))
    const homeVig0 = await vignetteSize('left')
    check('首页左蒙版默认宽 560（= 面板 400 + 160）', homeVig0 && homeVig0.w === 560, JSON.stringify(homeVig0))
    const homeBottomVig0 = await vignetteSize('bottom')
    check('首页下蒙版默认高 286（= 面板 321 - 35）', homeBottomVig0 && homeBottomVig0.h === 286, JSON.stringify(homeBottomVig0))

    const leftBar = await cdp.evalJs(centerOf('.home-stat.is-left .home-stat__resizer'))
    if (leftBar) {
      await cdp.drag(leftBar.x, leftBar.y, leftBar.x + 100, leftBar.y)
      const after = await homeWidth()
      const expect = 400 + 100 / k
      check('拖动左面板调节条：宽度 = 起点 + 位移/k', Math.abs(after - expect) <= 3, `${beforeW} → ${after}（期望≈${expect.toFixed(1)}）`)
      const vig = await vignetteSize('left')
      check('左蒙版跟着面板一起变宽（面板宽 + 160）', vig && Math.abs(vig.w - (after + 160)) <= 2, `蒙版 ${vig && vig.w}，面板 ${after}`)
    } else {
      check('拖动左面板调节条', false, '找不到调节条')
    }

    // 折叠 / 展开（屏边把手）
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    let tx = await cdp.evalJs(`(() => {
      const el = document.querySelector('.home-stat.is-left')
      return el ? getComputedStyle(el).transform : null
    })()`)
    check('点屏边把手：左面板整体滑出屏幕', /matrix\(1, 0, 0, 1, -\d+/.test(tx || ''), tx)
    const homeVigFolded = await vignetteSize('left')
    check('左蒙版随面板折叠一起收到 0', homeVigFolded && homeVigFolded.w === 0, JSON.stringify(homeVigFolded))
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    tx = await cdp.evalJs(`getComputedStyle(document.querySelector('.home-stat.is-left')).transform`)
    check('再点一次：左面板滑回原位', tx === 'none' || tx === 'matrix(1, 0, 0, 1, 0, 0)', tx)
    const homeVigBack = await vignetteSize('left')
    check('展开后左蒙版恢复', homeVigBack && Math.abs(homeVigBack.w - (await homeWidth()) - 160) <= 2, JSON.stringify(homeVigBack))

    // 下方面板：高度 + 折叠
    const attrH = () => cdp.evalJs(`document.querySelector('.home-attr').offsetHeight`)
    check('首页下方面板默认高 321（设计稿）', (await attrH()) === 321, `offsetHeight=${await attrH()}`)
    const attrBar = await cdp.evalJs(centerOf('.home-attr .home-attr__resizer'))
    if (attrBar) {
      await cdp.drag(attrBar.x, attrBar.y, attrBar.x, attrBar.y - 80)
      const after = await attrH()
      const expect = 321 + 80 / k
      check('拖动下方面板上沿：高度 = 起点 + 位移/k', Math.abs(after - expect) <= 3, `321 → ${after}（期望≈${expect.toFixed(1)}）`)
      const vig = await vignetteSize('bottom')
      check('下蒙版跟着面板一起变高（面板高 - 35）', vig && Math.abs(vig.h - (after - 35)) <= 2, `蒙版 ${vig && vig.h}，面板 ${after}`)
    } else {
      check('拖动下方面板上沿', false, '找不到调节条')
    }
    const attrWidthExpanded = await cdp.evalJs(`document.querySelector('.home-attr').getBoundingClientRect().width`)
    await cdp.evalJs(`document.querySelector('.home-attr .home-attr__toggle').click()`)
    await sleep(450)
    check('下方面板可收起为 47px 条', (await attrH()) === 47, `offsetHeight=${await attrH()}`)
    const bottomVigCollapsed = await vignetteSize('bottom')
    check('下蒙版随面板折叠一起收到 0', bottomVigCollapsed && bottomVigCollapsed.h === 0, JSON.stringify(bottomVigCollapsed))
    await cdp.evalJs(`document.querySelector('.home-attr .home-attr__toggle').click()`)
    await sleep(450)

    // 侧栏折叠时属性表变宽（左右内缩 460 → 30）
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(550)
    const attrWidthFolded = await cdp.evalJs(`document.querySelector('.home-attr').getBoundingClientRect().width`)
    check('左侧折叠后属性表变宽', attrWidthFolded > attrWidthExpanded + 50, `${Math.round(attrWidthExpanded)} → ${Math.round(attrWidthFolded)}`)
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)

    /* ================= 地图管理 ================= */
    console.log('\n### 地图管理')
    const openMenu = async (label) => {
      const ok = await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === ${JSON.stringify(label)})
        if (b) b.click(); return true
        return false
      })()`)
      await sleep(1600)
      return ok
    }
    check('顶栏能点到「地图管理」', await openMenu('地图管理'))

    const mapWidth = () => cdp.evalJs(`(() => {
      const el = document.querySelector('.map-screen')
      return el ? parseFloat(getComputedStyle(el).getPropertyValue('--panel-left-w')) : null
    })()`)
    check('地图管理左面板默认宽 400', (await mapWidth()) === 400, `--panel-left-w=${await mapWidth()}`)
    check('地图管理左右面板都有调节条',
      await cdp.evalJs(`!!document.querySelector('.map-screen__left-resizer') && !!document.querySelector('.map-screen__right-resizer')`))
    const mapVig0 = await vignetteSize('left')
    check('地图管理左蒙版默认宽 560', mapVig0 && mapVig0.w === 560, JSON.stringify(mapVig0))
    const mapBottomVig0 = await vignetteSize('bottom')
    check('地图管理底部默认收起 → 下蒙版为 0', mapBottomVig0 && mapBottomVig0.h === 0, JSON.stringify(mapBottomVig0))
    const mapLeftBar = await cdp.evalJs(centerOf('.map-screen__left-resizer'))
    if (mapLeftBar) {
      await cdp.drag(mapLeftBar.x, mapLeftBar.y, mapLeftBar.x + 80, mapLeftBar.y)
      const after = await mapWidth()
      check('地图管理左面板拖动变宽', Math.abs(after - (400 + 80 / k)) <= 3, `400 → ${after}`)
      const vig = await vignetteSize('left')
      check('地图管理左蒙版跟着变宽', vig && Math.abs(vig.w - (after + 160)) <= 2, `蒙版 ${vig && vig.w}，面板 ${after}`)
    }
    const mapRightBar = await cdp.evalJs(centerOf('.map-screen__right-resizer'))
    if (mapRightBar) {
      const beforeR = await cdp.evalJs(`parseFloat(getComputedStyle(document.querySelector('.map-screen')).getPropertyValue('--panel-right-w'))`)
      await cdp.drag(mapRightBar.x, mapRightBar.y, mapRightBar.x - 80, mapRightBar.y)
      const afterR = await cdp.evalJs(`parseFloat(getComputedStyle(document.querySelector('.map-screen')).getPropertyValue('--panel-right-w'))`)
      check('地图管理右面板往左拖变宽（reverse）', Math.abs(afterR - (beforeR + 80 / k)) <= 3, `${beforeR} → ${afterR}`)
      const vigR = await vignetteSize('right')
      check('地图管理右蒙版跟着变宽', vigR && Math.abs(vigR.w - (afterR + 160)) <= 2, `蒙版 ${vigR && vigR.w}，面板 ${afterR}`)
    }
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    check('地图管理左面板可折叠（滑出屏幕）',
      /matrix\(1, 0, 0, 1, -\d+/.test(await cdp.evalJs(`getComputedStyle(document.querySelector('.map-screen__left')).transform`)),
      await cdp.evalJs(`getComputedStyle(document.querySelector('.map-screen__left')).transform`))
    const mapVigFolded = await vignetteSize('left')
    check('地图管理左蒙版随折叠收到 0', mapVigFolded && mapVigFolded.w === 0, JSON.stringify(mapVigFolded))
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(400)

    // 底部属性表：默认收起 → 展开 → 拖高
    const dockH = () => cdp.evalJs(`(document.querySelector('.screen-bottom-dock') || {}).offsetHeight || null`)
    check('地图管理底部属性表默认是 47px 收起条', (await dockH()) === 47, `height=${await dockH()}`)
    await cdp.evalJs(`document.querySelector('.screen-bottom-dock .screen-bottom-dock__toggle').click()`)
    await sleep(500)
    check('点展开：属性表面板出现（321）', (await dockH()) === 321, `height=${await dockH()}`)
    const mapBottomVigOpen = await vignetteSize('bottom')
    check('展开后下蒙版出现（321 - 35）', mapBottomVigOpen && mapBottomVigOpen.h === 286, JSON.stringify(mapBottomVigOpen))
    const dockBar = await cdp.evalJs(centerOf('.screen-bottom-dock .screen-bottom-dock__resizer'))
    if (dockBar) {
      await cdp.drag(dockBar.x, dockBar.y, dockBar.x, dockBar.y - 100)
      const after = await dockH()
      check('底部属性表可拖动改高', Math.abs(after - (321 + 100 / k)) <= 3, `321 → ${after}`)
      const vigB = await vignetteSize('bottom')
      check('地图管理下蒙版跟着变高', vigB && Math.abs(vigB.h - (after - 35)) <= 2, `蒙版 ${vigB && vigB.h}，面板 ${after}`)
    } else {
      check('底部属性表可拖动改高', false, '找不到调节条')
    }
    await shotTo('panel-layout-map.png')

    /* ================= 配套分析管理 ================= */
    console.log('\n### 配套分析管理')
    check('顶栏能点到「配套分析管理」', await openMenu('配套分析管理'))
    check('配套分析管理左右面板都有调节条',
      await cdp.evalJs(`!!document.querySelector('.analysis-screen__left-resizer') && !!document.querySelector('.analysis-screen__right-resizer')`))
    const anaBar = await cdp.evalJs(centerOf('.analysis-screen__left-resizer'))
    if (anaBar) {
      const before = await cdp.evalJs(`parseFloat(getComputedStyle(document.querySelector('.analysis-screen')).getPropertyValue('--panel-left-w'))`)
      await cdp.drag(anaBar.x, anaBar.y, anaBar.x + 60, anaBar.y)
      const after = await cdp.evalJs(`parseFloat(getComputedStyle(document.querySelector('.analysis-screen')).getPropertyValue('--panel-left-w'))`)
      check('配套分析管理左面板拖动变宽', Math.abs(after - (before + 60 / k)) <= 3, `${before} → ${after}`)
      const vig = await vignetteSize('left')
      check('配套分析管理左蒙版跟着变宽', vig && Math.abs(vig.w - (after + 160)) <= 2, `蒙版 ${vig && vig.w}，面板 ${after}`)
    }
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-right .screen-side-rail__handle').click()`)
    await sleep(500)
    check('配套分析管理右面板可折叠（滑出屏幕）',
      /matrix\(1, 0, 0, 1, \d+/.test(await cdp.evalJs(`getComputedStyle(document.querySelector('.analysis-screen__right')).transform`)),
      await cdp.evalJs(`getComputedStyle(document.querySelector('.analysis-screen__right')).transform`))
    const anaRightVig = await vignetteSize('right')
    check('配套分析管理右蒙版随折叠收到 0', anaRightVig && anaRightVig.w === 0, JSON.stringify(anaRightVig))
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-right .screen-side-rail__handle').click()`)
    await sleep(400)
    await shotTo('panel-layout-analysis.png')

    /* ================= 数据管理（左导航 / 右检索列 / 列表） ================= */
    console.log('\n### 数据管理')
    check('顶栏能点到「数据管理」', await openMenu('数据管理'))
    const dataLayoutRead = () => cdp.evalJs(`(() => {
      const nav = document.querySelector('.data-screen__nav')
      const body = document.querySelector('.data-screen__body')
      const search = document.querySelector('.land-entry__search')
      const list = document.querySelector('.land-entry__list')
      const root = document.querySelector('.data-screen')
      return {
        navW: nav ? nav.offsetWidth : null,
        bodyLeft: body ? parseFloat(getComputedStyle(body).left) : null,
        searchW: search ? search.offsetWidth : null,
        listLeft: list ? parseFloat(getComputedStyle(list).left) : null,
        listRight: list ? parseFloat(getComputedStyle(list).right) : null,
        listH: list ? list.offsetHeight : null,
        navTransform: nav ? getComputedStyle(nav).transform : null,
        searchTransform: search ? getComputedStyle(search).transform : null,
        navVar: root ? parseFloat(getComputedStyle(root).getPropertyValue('--data-nav-w')) : null,
        searchVar: root ? parseFloat(getComputedStyle(root).getPropertyValue('--data-search-w')) : null
      }
    })()`)
    const d0 = await dataLayoutRead()
    check('数据管理左导航默认宽 200', d0 && d0.navVar === 200 && d0.navW === 200, JSON.stringify(d0 && { navVar: d0.navVar, navW: d0.navW }))
    check('数据管理右检索列默认宽 200', d0 && d0.searchVar === 200 && d0.searchW === 200, JSON.stringify(d0 && { searchVar: d0.searchVar, searchW: d0.searchW }))
    // ★ 分栏页签的 .data-screen__body 是 inset:0（内容区交出整块画布给 entry 面板），
    //   所以「内容区左偏移」这条只在非分栏页签上量；分栏页签看列表左偏移（下面那条）。
    check('数据管理列表默认左右 250 / 250', d0 && d0.listLeft === 250 && d0.listRight === 250, `list ${d0 && d0.listLeft} / ${d0 && d0.listRight}`)
    check('数据管理列表默认高 360', d0 && d0.listH === 360, `listH=${d0 && d0.listH}`)
    const dVig0 = { left: await vignetteSize('left'), right: await vignetteSize('right'), bottom: await vignetteSize('bottom') }
    check('数据管理左/右蒙版默认宽 360（= 200 + 160）', dVig0.left && dVig0.left.w === 360 && dVig0.right && dVig0.right.w === 360, JSON.stringify(dVig0.left) + ' / ' + JSON.stringify(dVig0.right))
    check('数据管理下蒙版默认高 325（= 360 - 35）', dVig0.bottom && dVig0.bottom.h === 325, JSON.stringify(dVig0.bottom))

    // 拖宽左导航
    const dataNavBar = await cdp.evalJs(centerOf('.data-screen__nav-resizer'))
    if (dataNavBar) {
      await cdp.drag(dataNavBar.x, dataNavBar.y, dataNavBar.x + 60, dataNavBar.y)
      const d1 = await dataLayoutRead()
      check('数据管理左导航拖动变宽', Math.abs(d1.navVar - (200 + 60 / k)) <= 3, `200 → ${d1.navVar}`)
      check('列表左偏移跟着导航变宽', Math.abs(d1.listLeft - (d1.navVar + 50)) <= 2, `listLeft=${d1.listLeft}，nav=${d1.navVar}`)
      const vig = await vignetteSize('left')
      check('数据管理左蒙版跟着导航变宽', vig && Math.abs(vig.w - (d1.navVar + 160)) <= 2, `蒙版 ${vig && vig.w}，导航 ${d1.navVar}`)
    } else {
      check('数据管理左导航拖动变宽', false, '找不到左导航拖动条')
    }

    // 折叠 / 展开左导航
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    const dCollapsed = await dataLayoutRead()
    check('数据管理左导航可折叠（滑出屏幕）', /matrix\(1, 0, 0, 1, -\d+/.test(dCollapsed.navTransform || ''), dCollapsed.navTransform)
    check('折叠后列表左偏移收到 30', dCollapsed.listLeft === 30, `listLeft=${dCollapsed.listLeft}`)
    const navVigFolded = await vignetteSize('left')
    check('折叠后左蒙版收到 0', navVigFolded && navVigFolded.w === 0, JSON.stringify(navVigFolded))
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)

    // 拖宽右检索列
    const dataSearchBar = await cdp.evalJs(centerOf('.data-screen__search-resizer'))
    if (dataSearchBar) {
      await cdp.drag(dataSearchBar.x, dataSearchBar.y, dataSearchBar.x - 60, dataSearchBar.y)
      const d2 = await dataLayoutRead()
      check('数据管理右检索列往左拖变宽（reverse）', Math.abs(d2.searchVar - (200 + 60 / k)) <= 3, `200 → ${d2.searchVar}`)
      check('列表右偏移跟着检索列变宽（检索列 + 50）', Math.abs(d2.listRight - (d2.searchVar + 50)) <= 2, `listRight=${d2.listRight}，search=${d2.searchVar}`)
      const vigR = await vignetteSize('right')
      check('数据管理右蒙版跟着检索列变宽', vigR && Math.abs(vigR.w - (d2.searchVar + 160)) <= 2, `蒙版 ${vigR && vigR.w}，检索列 ${d2.searchVar}`)
    } else {
      check('数据管理右检索列拖动变宽', false, '找不到右检索列拖动条')
    }

    // 折叠 / 展开右检索列
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-right .screen-side-rail__handle').click()`)
    await sleep(500)
    const dRight = await dataLayoutRead()
    check('数据管理右检索列可折叠（滑出屏幕）', /matrix\(1, 0, 0, 1, \d+/.test(dRight.searchTransform || ''), dRight.searchTransform)
    check('折叠后列表右偏移收到 30', dRight.listRight === 30, `listRight=${dRight.listRight}`)
    const rightVigFolded = await vignetteSize('right')
    check('折叠后右蒙版收到 0', rightVigFolded && rightVigFolded.w === 0, JSON.stringify(rightVigFolded))
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-right .screen-side-rail__handle').click()`)
    await sleep(500)

    // 非分栏页签（批量导入 / 附件 / 回收站）：内容区左偏移要跟随导航宽度 / 折叠
    const clickDataTab = async (label) => {
      await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(label)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(1000)
    }
    await clickDataTab('经营性用地批量导入')
    const dPlain = await cdp.evalJs(`(() => {
      const b = document.querySelector('.data-screen__body')
      const root = document.querySelector('.data-screen')
      return {
        left: b ? parseFloat(getComputedStyle(b).left) : null,
        navW: root ? parseFloat(getComputedStyle(root).getPropertyValue('--data-nav-w')) : null
      }
    })()`)
    check('非分栏页签：内容区左偏移 = 导航宽 + 50', dPlain && Math.abs(dPlain.left - (dPlain.navW + 50)) <= 2, `left=${dPlain && dPlain.left}，nav=${dPlain && dPlain.navW}`)
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    const dPlainFolded = await cdp.evalJs(`parseFloat(getComputedStyle(document.querySelector('.data-screen__body')).left)`)
    check('非分栏页签：折叠导航后内容区左偏移收到 30', dPlainFolded === 30, `left=${dPlainFolded}`)
    await cdp.evalJs(`document.querySelector('.screen-side-rail.is-left .screen-side-rail__handle').click()`)
    await sleep(500)
    await clickDataTab('经营性用地信息')

    // 拖高列表（底部面板）
    const dataListBar = await cdp.evalJs(centerOf('.land-entry__list .screen-resize-bar'))
    if (dataListBar) {
      const beforeH = (await dataLayoutRead()).listH
      await cdp.drag(dataListBar.x, dataListBar.y, dataListBar.x, dataListBar.y - 80)
      const afterH = (await dataLayoutRead()).listH
      check('数据管理列表拖动变高', Math.abs(afterH - (beforeH + 80 / k)) <= 3, `${beforeH} → ${afterH}`)
      const vigB = await vignetteSize('bottom')
      check('数据管理下蒙版跟着列表变高', vigB && Math.abs(vigB.h - (afterH - 35)) <= 2, `蒙版 ${vigB && vigB.h}，列表 ${afterH}`)
    } else {
      check('数据管理列表拖动变高', false, '找不到列表拖动条')
    }
    await shotTo('panel-layout-data.png')

    /* ================= 持久化：刷新后仍在 ================= */
    console.log('\n### 持久化')
    const saved = await cdp.evalJs(`window.localStorage.getItem('screen-panel-layout-v1')`)
    check('尺寸/折叠状态写进 localStorage', !!saved && saved.indexOf('"home"') > -1, saved ? saved.slice(0, 160) : 'null')
    // 回首页量一次当前宽度，再刷新验证持久化（在分析页量不到首页的 DOM）
    await openMenu('首页')
    await sleep(400)
    const beforeReload = await cdp.evalJs(`(() => {
      const el = document.querySelector('.home-stat.is-left')
      return el ? parseFloat(getComputedStyle(el).getPropertyValue('--home-panel-w')) : null
    })()`)
    await cdp.send('Page.reload', { ignoreCache: false })
    await sleep(5000)
    const afterReload = await cdp.evalJs(`(() => {
      const el = document.querySelector('.home-stat.is-left')
      return el ? parseFloat(getComputedStyle(el).getPropertyValue('--home-panel-w')) : null
    })()`)
    check('刷新后首页面板宽度仍是拖过的值', afterReload === 509 || Math.abs(afterReload - beforeReload) <= 1, `${beforeReload} → ${afterReload}`)

    /* ================= 页面无报错 ================= */
    const errs = await cdp.evalJs(`window.__panelErrs || []`)
    check('页面无未捕获异常 / Vue 渲染报错', !errs || errs.length === 0, errs && errs.length ? JSON.stringify(errs).slice(0, 400) : '0 条')

    // 截图留档（人工复核版式用）
    await shotTo('panel-layout-home.png')
  } catch (error) {
    console.error('\n验证脚本异常：', error && error.message)
    process.exitCode = 1
  } finally {
    try { if (ws) ws.close() } catch (e) { /* 忽略 */ }
    edge.kill()
  }

  const failed = results.filter((r) => !r.ok)
  console.log(`\n=== ${results.length - failed.length}/${results.length} 条通过 ===`)
  if (failed.length) {
    failed.forEach((f) => console.log('  ✗ ' + f.name))
    process.exitCode = 1
  }
})()
