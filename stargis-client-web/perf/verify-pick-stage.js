/**
 * 验证「选取地块」全屏模式（2026-10 需求）
 * ===================================================================
 * 这个脚本存在的唯一理由：本次改动的两个致命缺陷（覆盖层永远不出现、
 * 覆盖层把地图鼠标事件吃光）**静态检查一个都查不出来** ——
 * 模板编译、less 编译、eslint、verify-sfc 全过，但功能完全不工作。
 * 所以必须在真实浏览器里点一遍。
 *
 * 用真实点击走一遍：数据管理 → 经营性用地信息录入 → 新增宗地 → 选取地块
 *                  → 取消选择 → 再进 → 选择完成 →（再对配套表单跑一遍）
 *
 * 每个环节断言：
 *   1) 覆盖层 .pick-stage 真的出现在 DOM 里；
 *   2) 两个按钮（取消选择 / 选择完成）在**屏幕顶部正中**、且真的能点到；
 *   3) 「所有页面全部隐藏」：画布元素 opacity=0、弹窗/下拉 visibility=hidden；
 *   4) 地图仍可见、**鼠标事件能落到地图上**（关键：拦截 pointer-events 检查）；
 *   5) 点取消 / 完成后覆盖层消失、画布恢复 opacity=1、弹窗重新可见。
 *
 * 用法：node perf/verify-pick-stage.js [http://localhost:3000]
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3000'
const USERNAME = 'admin'
const PASSWORD = '123456'
const USER_DATA = os.tmpdir() + '\\edge-verify-pick-' + Date.now()
const PORT = 10900 + Math.floor(Math.random() * 400)

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

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
  /**
   * 用真实输入域在 (x, y) 处点击（mousePressed + mouseReleased）。
   *
   * ★ 为什么不能只用元素的 `b.click()`：那是**直接在元素上合成 click**，
   *   绕过浏览器的命中测试 —— 即使按钮被别的元素盖住（例如某个 z-index 更高的
   *   浮层压在按钮上），b.click() 依然会成功，于是「按钮真的能点到」这条断言
   *   就变成了假绿。Input.dispatchMouseEvent 走真实命中测试，
   *   盖住 / pointer-events: none 这类问题都会如实暴露。
   */
  async clickAt (x, y) {
    const base = { x: Math.round(x), y: Math.round(y), button: 'left', clickCount: 1 }
    await this.send('Input.dispatchMouseEvent', Object.assign({ type: 'mousePressed' }, base))
    await this.send('Input.dispatchMouseEvent', Object.assign({ type: 'mouseReleased' }, base))
  }
}

/* ------------------------------------------------------------------ */
/* 页面侧探针                                                          */
/* ------------------------------------------------------------------ */

/**
 * 在页面里定义一个「检查选取模式」的函数，返回一份结构化快照。
 * 所有判据都在真实 DOM / computedStyle 上取，不看组件内部状态。
 */
const PAGE_HELPERS = `
window.__pick = {
  visible: function (el) {
    if (!el) return false
    const cs = getComputedStyle(el)
    if (cs.display === 'none' || cs.visibility === 'hidden') return false
    if (Number(cs.opacity) === 0) return false
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.height > 0
  },

  // 覆盖层是否在 DOM 里 + 两个按钮的位置/可点性
  stage: function () {
    const stage = document.querySelector('.pick-stage')
    if (!stage) return { present: false }
    const cs = getComputedStyle(stage)
    const buttons = Array.from(stage.querySelectorAll('button')).map(function (b) {
      const r = b.getBoundingClientRect()
      const bcs = getComputedStyle(b)
      return {
        text: String(b.textContent || '').replace(/\\s/g, ''),
        x: Math.round(r.left), y: Math.round(r.top),
        w: Math.round(r.width), h: Math.round(r.height),
        cx: Math.round(r.left + r.width / 2),
        pointerEvents: bcs.pointerEvents,
        visible: bcs.display !== 'none' && bcs.visibility !== 'hidden'
      }
    })
    return {
      present: true,
      zIndex: cs.zIndex,
      pointerEvents: cs.pointerEvents,
      background: cs.backgroundColor,
      // 屏幕顶部正中？两个按钮整体水平居中 + 靠上
      buttons: buttons,
      viewport: { w: window.innerWidth, h: window.innerHeight }
    }
  },

  // 地图是否仍然可见（不该被隐藏 / 遮挡）
  map: function () {
    const home = document.getElementById('home')
    const v = window.viewer
    if (!home) return { home: false }
    const cs = getComputedStyle(home)
    const r = home.getBoundingClientRect()
    return {
      home: true,
      visible: cs.display !== 'none' && cs.visibility !== 'hidden' && Number(cs.opacity) > 0,
      size: { w: Math.round(r.width), h: Math.round(r.height) },
      viewer: v ? { loop: !!v.useDefaultRenderLoop, canvasW: v.canvas ? v.canvas.clientWidth : 0 } : null
    }
  },

  /**
   * 命中测试：屏幕正中那个点上，document.elementFromPoint 返回的是谁？
   * 这是判断「覆盖层有没有把地图挡死」的**直接**证据 ——
   * 如果返回 .pick-stage 自己，说明鼠标事件到不了地图。
   */
  hitTest: function () {
    const x = Math.round(window.innerWidth / 2)
    const y = Math.round(window.innerHeight / 2)
    const el = document.elementFromPoint(x, y)
    if (!el) return { x: x, y: y, tag: null }
    const path = []
    let n = el
    while (n && n !== document.body && path.length < 6) {
      path.push(n.tagName.toLowerCase() + (n.className && typeof n.className === 'string' ? '.' + n.className.trim().split(/\\s+/).join('.') : ''))
      n = n.parentElement
    }
    return { x: x, y: y, tag: el.tagName.toLowerCase(), cls: String(el.className || ''), path: path }
  },

  /**
   * 画布元素的可见性统计。
   * ★ 选择器必须覆盖**需求里说的「所有页面」**，漏一个就会出现假绿：
   *   早先只查了「蒙版 + 整页模块 + 顶栏」，而首页那三块常驻面板
   *   （左统计 / 右图层树 / 底部属性表）与两个首页浮层**根本不在统计里** ——
   *   漏挂 canvasFade 时这条断言照样全绿。现在把它们全部纳入。
   *   各类根元素名取自各组件自己的根 class（都是单根组件，class 会落到根节点）。
   */
  canvas: function () {
    const nodes = Array.from(document.querySelectorAll(
      '.land-screen__vignette, .land-screen__module, .screen-header, ' +
      '.home-left, .home-tree, .home-attr, .home-plot'
    ))
    const hidden = nodes.filter(function (n) { return Number(getComputedStyle(n).opacity) === 0 })
    return { total: nodes.length, hidden: hidden.length }
  },

  /** body 上的选取标记是否设置（全局隐藏弹窗/下拉的开关） */
  bodyFlag: function () { return document.body.classList.contains('is-picking-map') },

  /** 表单弹窗 / 下拉浮层的可见性（应当被藏起来） */
  overlays: function () {
    const modals = Array.from(document.querySelectorAll('.screen-modal-root > .screen-modal'))
    const pops = Array.from(document.querySelectorAll('.screen-popover'))
    const vis = function (el) { const cs = getComputedStyle(el); return cs.visibility !== 'hidden' && cs.display !== 'none' }
    return {
      modals: modals.length,
      modalsVisible: modals.filter(vis).length,
      popovers: pops.length,
      popoversVisible: pops.filter(vis).length
    }
  },

  /** 点一个按钮（按文字找），返回是否点到 */
  clickByText: function (text) {
    const norm = function (t) { return String(t || '').replace(/\\s/g, '') }
    const b = Array.from(document.querySelectorAll('button')).find(function (x) { return norm(x.textContent) === text })
    if (!b) return false
    b.click()
    return true
  },

  /** 找表单里的「选取地块」按钮并点击 */
  clickPickEntry: function () {
    const b = document.querySelector('.land-form__map-pick-btn, .facility-form__map-pick-btn')
    if (!b) return 'NOT_FOUND'
    if (b.disabled) return 'DISABLED'
    b.click()
    return 'OK'
  },

  /** 当前打开的表单弹窗标题（判断我们在哪张表单里） */
  modalTitle: function () {
    const el = document.querySelector('.screen-modal-root > .screen-modal .screen-modal__title')
    return el ? String(el.textContent).trim() : null
  },

  /** 真实鼠标事件测试：往屏幕正中发一次 pointerdown，看它落在谁身上 */
  probePointer: function () {
    const x = Math.round(window.innerWidth / 2)
    const y = Math.round(window.innerHeight / 2)
    let hit = null
    const handler = function (e) { hit = e.target }
    window.addEventListener('pointerdown', handler, true)
    const ev = new PointerEvent('pointerdown', { clientX: x, clientY: y, bubbles: true, cancelable: true })
    const target = document.elementFromPoint(x, y) || document.body
    target.dispatchEvent(ev)
    window.removeEventListener('pointerdown', handler, true)
    if (!hit) return { delivered: false }
    return {
      delivered: true,
      tag: hit.tagName.toLowerCase(),
      cls: String(hit.className || '').slice(0, 60),
      isCanvas: hit.tagName.toLowerCase() === 'canvas'
    }
  }
};
`

/* ------------------------------------------------------------------ */
/* 断言                                                                */
/* ------------------------------------------------------------------ */

const results = []
function check (name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail ? '  —— ' + detail : ''}`)
}

async function state (cdp) {
  const raw = await cdp.evalJs(`JSON.stringify({
    stage: window.__pick.stage(),
    map: window.__pick.map(),
    hit: window.__pick.hitTest(),
    probe: window.__pick.probePointer(),
    canvas: window.__pick.canvas(),
    bodyFlag: window.__pick.bodyFlag(),
    overlays: window.__pick.overlays(),
    title: window.__pick.modalTitle()
  })`)
  return JSON.parse(raw)
}

/** 等某个条件成立（轮询页面状态） */
async function waitFor (cdp, expr, timeoutMs, label) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    const r = await cdp.evalJs(expr)
    if (r === true) return true
    await sleep(400)
  }
  console.log(`  ⚠ 等待超时：${label}`)
  return false
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
    await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: PAGE_HELPERS })

    /* ---------------- 登录 ---------------- */
    await cdp.send('Page.navigate', { url: BASE + '/#/user/login' })
    let filled = false
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
      if (r === 'OK') { filled = true; break }
    }
    if (!filled) throw new Error('登录页表单没出现（dev-server 还没编译完？）')
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click()
      return !!b
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
    if (!ready) throw new Error('没能进入大屏首页 / Cesium 未就绪')
    console.log('### 已登录，Cesium 就绪')
    await sleep(3000)

    /* ---------------- 进入数据管理 ---------------- */
    console.log('\n### 进入「数据管理 → 经营性用地信息录入」')
    await cdp.evalJs(`window.__pick.clickByText('数据管理')`)
    await sleep(3000)
    // 左二级导航里点「经营性用地信息录入」
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === '经营性用地信息录入')
      if (b) b.click()
      return !!b
    })()`)
    await sleep(3500)

    /* ---------------- 打开「新增宗地」表单 ---------------- */
    console.log('\n### 打开「新增经营性用地」表单')
    await cdp.evalJs(`window.__pick.clickByText('新增宗地')`)
    await waitFor(cdp, `!!document.querySelector('.land-form__map-pick-btn')`, 15000, '新增宗地表单出现')
    await sleep(1200)

    const before = await state(cdp)
    console.log(`  表单标题：${before.title}`)
    check('「选取地块」按钮出现在新增宗地表单里', !!before.title && before.title.indexOf('新增') === 0, before.title || '')

    /* ---------------- 点「选取地块」 ---------------- */
    console.log('\n### 点击「选取地块」→ 进入全屏地图模式')
    const clicked = await cdp.evalJs(`window.__pick.clickPickEntry()`)
    check('「选取地块」按钮可点击（未被禁用）', clicked === 'OK', String(clicked))
    const appeared = await waitFor(cdp, `!!document.querySelector('.pick-stage')`, 10000, '覆盖层出现')
    check('覆盖层 .pick-stage 真的渲染出来了（阻塞①的核心判据）', appeared)
    await sleep(1500)

    const inPick = await state(cdp)
    console.log('  状态：', JSON.stringify({ stage: inPick.stage.present, canvas: inPick.canvas, bodyFlag: inPick.bodyFlag, overlays: inPick.overlays, hit: inPick.hit }))

    // --- 需求①：所有页面全部隐藏 ---
    check(
      '所有页面元素都隐藏了（蒙版/整页模块/顶栏/首页面板，opacity=0）',
      // 下界 6：数据管理页场景至少应有 4 张蒙版 + 顶栏 + 1 个整页模块。
      // 加这条下界是为了防「选择器写窄 → total 变小 → 断言反而更容易过」的退化。
      inPick.canvas.total >= 6 && inPick.canvas.hidden === inPick.canvas.total,
      `${inPick.canvas.hidden}/${inPick.canvas.total} 隐藏`
    )
    check(
      '表单弹窗被藏起来（body 标记 + visibility: hidden）',
      inPick.bodyFlag === true && inPick.overlays.modals > 0 && inPick.overlays.modalsVisible === 0,
      `modal ${inPick.overlays.modalsVisible}/${inPick.overlays.modals} 可见，bodyFlag=${inPick.bodyFlag}`
    )
    check(
      '下拉浮层也被藏起来（z-index 1260 会盖住覆盖层）',
      inPick.overlays.popoversVisible === 0,
      `popover ${inPick.overlays.popoversVisible}/${inPick.overlays.popovers} 可见`
    )

    // --- 需求②：上方中间两个明显按钮 ---
    const btns = inPick.stage.buttons || []
    const cancel = btns.find((b) => b.text === '取消选择')
    const done = btns.find((b) => b.text === '选择完成')
    check('存在「取消选择」和「选择完成」两个按钮', !!cancel && !!done, btns.map((b) => b.text).join(' / '))
    if (cancel && done) {
      const vw = inPick.stage.viewport.w
      const groupCenter = (Math.min(cancel.x, done.x) + Math.max(cancel.x + cancel.w, done.x + done.w)) / 2
      check('两个按钮水平居中于屏幕顶部', Math.abs(groupCenter - vw / 2) < 12 && cancel.y < 120,
        `组中心 x=${Math.round(groupCenter)}（视口中心 ${vw / 2}），top=${cancel.y}`)
      check('两个按钮尺寸明显（高≥48px，宽≥140px）', cancel.h >= 48 && cancel.w >= 140 && done.h >= 48 && done.w >= 140,
        `${cancel.w}×${cancel.h} / ${done.w}×${done.h}`)
      check('两个按钮 pointer-events: auto（真的能点到）',
        cancel.pointerEvents === 'auto' && done.pointerEvents === 'auto',
        `${cancel.pointerEvents} / ${done.pointerEvents}`)
    }

    // --- 需求：只显示下方地图，且地图可交互 ---
    check('地图仍然可见（没被一起隐藏）', inPick.map.visible === true && inPick.map.size.w > 100, JSON.stringify(inPick.map.size))
    check('选取模式下 Cesium 恢复渲染（mapPaused=false）', !!(inPick.map.viewer && inPick.map.viewer.loop), JSON.stringify(inPick.map.viewer))
    check(
      '屏幕正中命中测试落到地图 canvas，而不是覆盖层（阻塞②的核心判据）',
      inPick.hit.path && inPick.hit.path.some((p) => p.indexOf('cesium') > -1 || p === 'canvas') === true,
      `命中 ${inPick.hit.tag}.${inPick.hit.cls} ← ${JSON.stringify(inPick.hit.path)}`
    )
    check(
      '真实 pointerdown 事件落到 canvas（地图能拖能缩放）',
      inPick.probe.delivered === true && inPick.probe.isCanvas === true,
      JSON.stringify(inPick.probe)
    )
    check('覆盖层根节点 pointer-events 为 none（不吃地图事件）', inPick.stage.pointerEvents === 'none', inPick.stage.pointerEvents)

    // --- 点「取消选择」回到正常页面 ---
    // ★ 用真实鼠标点击（而不是 b.click()）：这是「按钮真的能点到」的唯一硬证据。
    console.log('\n### 真实鼠标点击「取消选择」')
    // 先确认按钮中心没有被别的元素盖住（遮挡类回归 b.click() 查不出来）
    const cancelHit = await cdp.evalJs(`(() => {
      const btn = Array.from(document.querySelectorAll('.pick-stage button')).find(b => String(b.textContent||'').replace(/\\s/g,'') === '取消选择')
      if (!btn) return { found: false }
      const r = btn.getBoundingClientRect()
      const cx = Math.round(r.left + r.width/2), cy = Math.round(r.top + r.height/2)
      const top = document.elementFromPoint(cx, cy)
      return { found: true, cx: cx, cy: cy, selfHit: !!(top && (top === btn || btn.contains(top))), topTag: top ? top.tagName.toLowerCase() + '.' + String(top.className||'').slice(0,40) : null }
    })()`)
    check('「取消选择」按钮中心未被遮挡（命中自己）', cancelHit.found && cancelHit.selfHit === true, JSON.stringify(cancelHit))
    if (cancelHit.found) await cdp.clickAt(cancelHit.cx, cancelHit.cy)
    const gone1 = await waitFor(cdp, `!document.querySelector('.pick-stage')`, 8000, '覆盖层消失（取消）')
    check('真实鼠标点「取消选择」后覆盖层消失', gone1)
    await sleep(1200)
    const afterCancel = await state(cdp)
    check('取消后画布恢复可见（整页模块 opacity 回到 1）', afterCancel.canvas.hidden === 0, `${afterCancel.canvas.hidden}/${afterCancel.canvas.total} 仍隐藏`)
    check('取消后表单弹窗重新可见（回到正常页面）', afterCancel.overlays.modalsVisible === afterCancel.overlays.modals && afterCancel.overlays.modals > 0,
      `modal ${afterCancel.overlays.modalsVisible}/${afterCancel.overlays.modals}`)
    check('取消后 body 标记已清除', afterCancel.bodyFlag === false)
    check('取消后表单还在（没被误关，填的内容不会丢）', !!afterCancel.title, String(afterCancel.title))

    /* ---------------- 再进一次，点「选择完成」 ---------------- */
    console.log('\n### 再点「选取地块」→ 点「选择完成」')
    // ⚠ 这两步的返回值**必须**断言。
    //   曾漏判过：waitFor 的返回值被丢弃、clickByText 的返回值也没人看，
    //   于是覆盖层第二次根本没出现时，下面的
    //   waitFor('!document.querySelector(".pick-stage")') 会**立刻为真**，
    //   「点『选择完成』后覆盖层消失」等 5 条断言在功能全废的情况下照样全绿。
    await cdp.evalJs(`window.__pick.clickPickEntry()`)
    const reappeared = await waitFor(cdp, `!!document.querySelector('.pick-stage')`, 10000, '覆盖层再次出现')
    check('第二次点「选取地块」覆盖层再次出现', reappeared)
    await sleep(1000)
    // 同样用**真实鼠标点击**「选择完成」
    const doneHit = await cdp.evalJs(`(() => {
      const btn = Array.from(document.querySelectorAll('.pick-stage button')).find(b => String(b.textContent||'').replace(/\\s/g,'') === '选择完成')
      if (!btn) return { found: false }
      const r = btn.getBoundingClientRect()
      const cx = Math.round(r.left + r.width/2), cy = Math.round(r.top + r.height/2)
      const top = document.elementFromPoint(cx, cy)
      return { found: true, cx: cx, cy: cy, selfHit: !!(top && (top === btn || btn.contains(top))) }
    })()`)
    check('「选择完成」按钮存在且未被遮挡', doneHit.found && doneHit.selfHit === true, JSON.stringify(doneHit))
    if (doneHit.found) await cdp.clickAt(doneHit.cx, doneHit.cy)
    const doneClicked = !!doneHit.found
    const gone2 = await waitFor(cdp, `!document.querySelector('.pick-stage')`, 8000, '覆盖层消失（完成）')
    check('真实鼠标点「选择完成」后覆盖层消失', doneClicked && gone2)
    await sleep(1200)
    const afterDone = await state(cdp)
    check('完成后画布恢复可见', afterDone.canvas.hidden === 0, `${afterDone.canvas.hidden}/${afterDone.canvas.total} 仍隐藏`)
    check('完成后表单弹窗重新可见', afterDone.overlays.modalsVisible === afterDone.overlays.modals && afterDone.overlays.modals > 0,
      `modal ${afterDone.overlays.modalsVisible}/${afterDone.overlays.modals}`)
    check('完成后 body 标记已清除', afterDone.bodyFlag === false)
    check('完成后表单还在', !!afterDone.title, String(afterDone.title))

    // 关掉宗地表单
    await cdp.evalJs(`window.__pick.clickByText('取消')`)
    await sleep(1200)

    /* ---------------- 配套项目表单同样验证 ---------------- */
    console.log('\n### 对「配套项目」表单再跑一遍')
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === '配套地块数据录入')
      if (b) b.click()
      return !!b
    })()`)
    await sleep(3500)
    await cdp.evalJs(`window.__pick.clickByText('新增配套')`)
    await waitFor(cdp, `!!document.querySelector('.facility-form__map-pick-btn')`, 15000, '新增配套表单出现')
    await sleep(1200)
    const fBefore = await state(cdp)
    console.log(`  表单标题：${fBefore.title}`)

    const fClicked = await cdp.evalJs(`window.__pick.clickPickEntry()`)
    check('配套表单「选取地块」可点击', fClicked === 'OK', String(fClicked))
    const fAppeared = await waitFor(cdp, `!!document.querySelector('.pick-stage')`, 10000, '覆盖层出现（配套）')
    check('配套表单也能唤起全屏地图模式', fAppeared)
    await sleep(1500)
    const fIn = await state(cdp)
    check('配套：所有页面隐藏', fIn.canvas.total > 0 && fIn.canvas.hidden === fIn.canvas.total, `${fIn.canvas.hidden}/${fIn.canvas.total}`)
    check('配套：弹窗被藏起来', fIn.bodyFlag === true && fIn.overlays.modalsVisible === 0, `modal ${fIn.overlays.modalsVisible}/${fIn.overlays.modals}`)
    check('配套：地图可交互（pointerdown 到 canvas）', fIn.probe.delivered === true && fIn.probe.isCanvas === true, JSON.stringify(fIn.probe))

    await cdp.evalJs(`window.__pick.clickByText('取消选择')`)
    const fGone = await waitFor(cdp, `!document.querySelector('.pick-stage')`, 8000, '覆盖层消失（配套）')
    check('配套：点「取消选择」回到正常页面', fGone)
    await sleep(1200)
    const fAfter = await state(cdp)
    check('配套：取消后画布与弹窗都恢复', fAfter.canvas.hidden === 0 && fAfter.overlays.modalsVisible === fAfter.overlays.modals && fAfter.overlays.modals > 0,
      `canvas 隐藏 ${fAfter.canvas.hidden}，modal ${fAfter.overlays.modalsVisible}/${fAfter.overlays.modals}`)

    /* ---------------- 汇总 ---------------- */
    const failed = results.filter((r) => !r.ok)
    console.log('\n================================================')
    console.log(`断言：${results.length - failed.length} / ${results.length} 通过`)
    if (failed.length) {
      console.log('\n失败项：')
      failed.forEach((f) => console.log(`  ✗ ${f.name}  —— ${f.detail || ''}`))
      process.exitCode = 1
    } else {
      console.log('✓ 全部通过：选取地块 → 隐藏全部页面 → 只显示地图（可交互）→ 取消/完成回到原页面')
    }
  } catch (e) {
    console.log('\n✗ 脚本异常：' + (e && e.message))
    process.exitCode = 1
  } finally {
    try { ws && ws.close() } catch (e) {}
    try { edge.kill() } catch (e) {}
    await sleep(500)
    process.exit(process.exitCode || 0)
  }
})()
