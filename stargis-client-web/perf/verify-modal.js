/**
 * 新增/编辑弹窗改造验证（真实浏览器 / CDP）
 * ==================================================================
 * 断言的是**需求本身**：
 *   ① 表单由 3 列改 2 列；弹窗高度是原来的一半；
 *   ② 弹窗可以随意拖动（且不会被拖出屏幕找不回来）；弹窗以外的背景**不再变暗**，
 *      并且背景里的地图**真的能操作**（不是「看着没变暗但事件被吃掉」）；
 *   ③ 点「添加地块」不再隐藏任何页面，弹窗仍在、地图仍可操作，
 *      两个按钮落在顶栏之下（不压导航）；Esc / 完成绘制都能回到表单；
 *   ④ 经营性用地与配套项目两个表单一致。
 *
 * ★ 端口：3000 = 管理端 admin-client，3001 = 大屏端 stargis-client-web（本脚本的对象）。
 * ★ 弹窗挂在 document.body（不在缩放画布内），所以它的几何量是**真实像素**，
 *   与画布无关 —— 这一点和 verify-data-layout.js 正好相反，别把换算照搬过来。
 *
 * 用法：node perf/verify-modal.js [http://localhost:3001]
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const USER_DATA = os.tmpdir() + '\\edge-verify-modal-' + Date.now()
const PORT = 13600 + Math.floor(Math.random() * 400)

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const r1 = (n) => Math.round(n * 10) / 10
const near = (a, b, tol) => Math.abs(a - b) <= tol

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
  /** 截一小块图（用于「背景有没有被压暗」的像素级比对） */
  async clipShot (x, y, width, height) {
    const r = await this.send('Page.captureScreenshot', {
      format: 'png',
      clip: { x, y, width, height, scale: 1 }
    })
    return r.data
  }
  async dragMouse (x1, y1, x2, y2) {
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: Math.round(x1), y: Math.round(y1), button: 'left', clickCount: 1 })
    for (let i = 1; i <= 6; i++) {
      const t = i / 6
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

/* ------------------------------------------------------------------
 * 页面内测量工具（弹窗在 body 上 → 全部是真实像素）
 * ------------------------------------------------------------------ */
const HELPERS = `
window.__vm = {
  norm: function (t) { return String(t || '').replace(/\\s/g, '') },
  box: function (el) {
    if (!el) return null
    var r = el.getBoundingClientRect()
    return { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height }
  },
  dialog: function () { return document.querySelector('.screen-modal__dialog') },
  mask: function () { return document.querySelector('.screen-modal__mask') },
  head: function () { return document.querySelector('.screen-modal__head') },
  /** 点某个按钮（按可见文字精确匹配） */
  clickText: function (text) {
    var self = this
    var b = Array.prototype.slice.call(document.querySelectorAll('button'))
      .filter(function (x) { return self.norm(x.textContent) === text && x.offsetParent !== null })
    if (!b.length) return false
    b[0].click()
    return true
  },
  /** 命中测试：某个点最上面是谁（判断「事件有没有被透明层吃掉」） */
  hit: function (x, y) {
    var el = document.elementFromPoint(x, y)
    if (!el) return 'null'
    return el.tagName.toLowerCase() + '.' + String(el.className || '').slice(0, 40)
  },
  /** 弹窗内第一个表单网格的列数 */
  gridTracks: function (gridSel) {
    var g = document.querySelector(gridSel)
    if (!g) return -1
    var c = getComputedStyle(g).gridTemplateColumns
    return c ? c.split(' ').filter(Boolean).length : 0
  },
  /** 页面有没有被隐藏（选取模式下的回归点） */
  hiddenCount: function () {
    return document.querySelectorAll('.is-pick-hidden').length
  },
  /** body 上有没有选取模式的标记类 */
  pickingFlag: function () { return document.body.classList.contains('is-picking-map') }
}
'ok'
`

const results = []
function check (name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail !== undefined ? '  —— ' + detail : ''}`)
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

    /* ---------------- 登录 ---------------- */
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
    const vp = JSON.parse(await cdp.evalJs('JSON.stringify({ w: innerWidth, h: innerHeight })'))
    /*
     * 「原来的一半」的基准：改造前 body-max-height = calc(100vh - 200px)，
     * 弹窗头 46 + 底栏 54 = 100，所以改造前的弹窗总高 = 100vh - 100。
     * 改造后应约为它的一半。
     */
    const baselineHeight = vp.h - 100
    const expectedHeight = baselineHeight / 2
    console.log(`### 已登录；视口 ${vp.w}×${vp.h}`)
    console.log(`### 改造前弹窗高度基准 = 100vh - 100 = ${baselineHeight}px，减半后应 ≈ ${expectedHeight}px\n`)

    await cdp.evalJs(`(() => {
      const norm = t => String(t||'').replace(/\\s/g,'')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3000)

    const panels = [
      {
        tab: '经营性用地信息录入',
        scope: '.land-entry',
        addText: '新增宗地',
        gridSel: '.land-form__grid',
        formSel: '.land-form',
        pickBtnSel: '.land-form__map-pick-btn',
        name: '经营性用地'
      },
      {
        tab: '配套地块数据录入',
        scope: '.facility-entry',
        addText: '新增配套',
        gridSel: '.facility-form__grid',
        formSel: '.facility-form',
        pickBtnSel: '.facility-form__map-pick-btn',
        name: '配套项目'
      }
    ]

    /* 背景压暗比对用的静态区域：左侧二级导航（纯界面，不受地图动画影响） */
    const NAV_CLIP = { x: 40, y: 160, w: 150, h: 380 }

    for (const p of panels) {
      console.log(`================ ${p.name} ================`)
      await cdp.evalJs(`(() => {
        const norm = t => String(t||'').replace(/\\s/g,'')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(p.tab)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(2500)

      // 打开弹窗之前先截一块背景，稍后逐字节比对
      const before = await cdp.clipShot(NAV_CLIP.x, NAV_CLIP.y, NAV_CLIP.w, NAV_CLIP.h)

      const opened = await cdp.evalJs(`window.__vm.clickText(${JSON.stringify(p.addText)})`)
      check(`${p.name}：能打开新增弹窗（点「${p.addText}」）`, opened)
      for (let i = 0; i < 30; i++) {
        await sleep(300)
        if (await cdp.evalJs(`!!window.__vm.dialog()`)) break
      }
      await sleep(900)

      const st = JSON.parse(await cdp.evalJs(`(() => {
        var d = window.__vm.dialog(), m = window.__vm.mask()
        var ms = m ? getComputedStyle(m) : null
        var body = d ? d.querySelector('.screen-modal__body') : null
        return JSON.stringify({
          dialog: window.__vm.box(d),
          head: window.__vm.box(window.__vm.head()),
          gridTracks: window.__vm.gridTracks(${JSON.stringify(p.gridSel)}),
          maskBg: ms ? ms.backgroundColor : null,
          maskPE: ms ? ms.pointerEvents : null,
          maskExists: !!m,
          bodyScrollable: body ? (body.scrollHeight > body.clientHeight + 2) : null,
          formExists: !!document.querySelector(${JSON.stringify(p.formSel)}),
          pickBtn: !!document.querySelector(${JSON.stringify(p.pickBtnSel)}),
          rootPE: (function () {
            var r = document.querySelector('.screen-modal')
            return r ? getComputedStyle(r).pointerEvents : null
          })(),
          dialogPE: d ? getComputedStyle(d).pointerEvents : null
        })
      })()`))

      check(`${p.name}：弹窗已打开且表单渲染`, !!st.dialog && st.formExists && st.pickBtn)

      /* ---------- 需求①：2 列 + 高度减半 ---------- */
      check(
        `${p.name}：表单是 **2 列**（原来 3 列）`,
        st.gridTracks === 2,
        `列数 ${st.gridTracks}`
      )
      check(
        `${p.name}：弹窗高度约为原来的**一半**`,
        near(st.dialog.height, expectedHeight, 20),
        `实测 ${r1(st.dialog.height)}px；基准 ${baselineHeight}px → 一半 ${r1(expectedHeight)}px`
      )
      check(
        `${p.name}：正文确实在滚动（说明高度是被上限压住的，不是内容不够）`,
        st.bodyScrollable === true,
        `body 可滚动 = ${st.bodyScrollable}`
      )
      check(
        `${p.name}：弹窗宽度已收窄到 960`,
        near(st.dialog.width, 960, 2),
        `宽 ${r1(st.dialog.width)}px`
      )

      /* ---------- 需求②：背景不变暗 ---------- */
      const maskAlpha = (() => {
        const m = /rgba?\(([^)]+)\)/.exec(String(st.maskBg || ''))
        if (!m) return null
        const parts = m[1].split(',').map((s) => parseFloat(s.trim()))
        return parts.length === 4 ? parts[3] : 1
      })()
      check(
        `${p.name}：遮罩底色**全透明**（背景不再变暗）`,
        maskAlpha === 0,
        `mask background = ${st.maskBg}`
      )
      check(
        `${p.name}：遮罩不拦截鼠标（否则「能看见地图」但点不到）`,
        st.maskPE === 'none',
        `mask pointer-events = ${st.maskPE}`
      )
      check(
        `${p.name}：弹窗外层不拦截鼠标、对话框自己可点`,
        st.rootPE === 'none' && st.dialogPE === 'auto',
        `外层 ${st.rootPE} / 对话框 ${st.dialogPE}`
      )

      const after = await cdp.clipShot(NAV_CLIP.x, NAV_CLIP.y, NAV_CLIP.w, NAV_CLIP.h)
      check(
        `${p.name}：弹窗打开前后，背景那块画面的**像素完全一致**（真的没压暗）`,
        before === after,
        before === after ? `${before.length} 字节逐字节相同` : '两次截图不同 —— 背景被改动了'
      )

      const hitOutside = await cdp.evalJs(`window.__vm.hit(300, 200)`)
      check(
        `${p.name}：弹窗外的地图**能直接命中**（不是被透明层吃掉）`,
        /canvas/i.test(String(hitOutside)) || /cesium|s3dm/i.test(String(hitOutside)),
        `(300,200) 命中 ${hitOutside}`
      )

      /* ---------- 需求②：可拖动 ---------- */
      const d0 = st.dialog
      const headCx = st.head.left + st.head.width / 2
      const headCy = st.head.top + st.head.height / 2
      await cdp.dragMouse(headCx, headCy, headCx + 160, headCy - 140)
      await sleep(500)
      const d1 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.dialog()))`))
      /*
       * ★ 环境坑：headless Edge 偶发会在运行途中把视口高度缩掉 34px
       *   （一条窗口装饰，出图脚本里也撞到过：同一批图里混进一张 1894×953）。
       *   弹窗是 flex 居中的，视口一变「居中基准」就跟着变，位移断言会**假失败**。
       *   所以这里把居中基准的变化补偿掉，而不是放宽容差 ——
       *   容差一放宽，真正的「拖动跟不上鼠标」也会被放过去。
       */
      const vpAfter = JSON.parse(await cdp.evalJs('JSON.stringify({ w: innerWidth, h: innerHeight })'))
      if (vpAfter.h !== vp.h) {
        console.log(`  ！视口高度在本次运行中变化了：${vp.h} → ${vpAfter.h}（已按居中基准补偿）`)
      }
      const shiftX = (vpAfter.w - d1.width) / 2 - (vp.w - d0.width) / 2
      const shiftY = (vpAfter.h - d1.height) / 2 - (vp.h - d0.height) / 2
      check(
        `${p.name}：拖标题栏可以移动弹窗（位移与鼠标一致）`,
        near(d1.left - d0.left, 160 + shiftX, 4) && near(d1.top - d0.top, -140 + shiftY, 4),
        `Δx ${r1(d1.left - d0.left)}（期望 ${r1(160 + shiftX)}）/ Δy ${r1(d1.top - d0.top)}（期望 ${r1(-140 + shiftY)}）`
      )
      check(
        `${p.name}：拖动不改变弹窗尺寸（宽度不变；高度只随视口 50vh 联动）`,
        near(d1.width, d0.width, 1) && near(d1.height - d0.height, (vpAfter.h - vp.h) / 2, 1.5),
        `宽 ${r1(d1.width)}；高 ${r1(d0.height)} → ${r1(d1.height)}（视口高 ${vp.h} → ${vpAfter.h}，50vh 联动 ${r1((vpAfter.h - vp.h) / 2)}）`
      )

      // 键盘也能挪（无障碍：不只有鼠标能拖）
      await cdp.evalJs(`(() => { const h = window.__vm.head(); if (h) h.focus(); return true })()`)
      await sleep(200)
      const kb0 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.dialog()))`))
      for (let i = 0; i < 3; i++) {
        await cdp.pressKey('ArrowRight', 'ArrowRight', 39)
        await sleep(120)
      }
      await sleep(300)
      const kb1 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.dialog()))`))
      check(
        `${p.name}：标题栏可聚焦，方向键同样能移动弹窗`,
        near(kb1.left - kb0.left, 48, 3),
        `Δx ${r1(kb1.left - kb0.left)}（3 × 16px）`
      )

      // 往右下角狂拖，应被夹住（不能把弹窗拖没了）
      const h1 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.head()))`))
      await cdp.dragMouse(h1.left + h1.width / 2, h1.top + h1.height / 2, vp.w + 800, vp.h + 800)
      await sleep(500)
      const d2 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.dialog()))`))
      check(
        `${p.name}：往屏幕外拖会被夹住（标题栏始终留在视口内，找得回来）`,
        d2.left < vp.w - 40 && d2.right > 40 && d2.top >= -1 && d2.top <= vp.h - 20,
        `left ${r1(d2.left)} / right ${r1(d2.right)} / top ${r1(d2.top)}（视口 ${vp.w}×${vp.h}）`
      )

      // 拖回左上方一个「不挡地图、也不压两个选取按钮」的位置，便于后续断言与截图
      // ★ 抓取点必须夹进视口：被上一步拖到极限后，标题栏只有 80px 还露在屏幕里，
      //   用「标题栏中心」去抓会落在视口外，鼠标事件根本打不到元素（拖动静默失败）。
      const h2 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.head()))`))
      const grabX = Math.max(20, Math.min(h2.left + h2.width / 2, vp.w - 20))
      const grabY = Math.max(20, Math.min(h2.top + h2.height / 2, vp.h - 20))
      await cdp.dragMouse(grabX, grabY, 600, 323)
      await sleep(500)
      const d3 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__vm.box(window.__vm.dialog()))`))
      check(
        `${p.name}：能把弹窗拖到一边、让出地图`,
        d3.left < vp.w / 2 && d3.top > 120,
        `拖后 left ${r1(d3.left)} / top ${r1(d3.top)}`
      )

      /* ---------- 收窄到 960 之后没有内容被裁掉 ---------- */
      const clip = JSON.parse(await cdp.evalJs(`(() => {
        var body = document.querySelector('.screen-modal__body')
        var items = Array.prototype.slice.call(document.querySelectorAll('.screen-modal__dialog [class$="__readonly-item"]'))
        var bad = items.filter(function (it) {
          var ir = it.getBoundingClientRect()
          var tag = it.querySelector('.screen-tag')
          if (!tag) return false
          var tr = tag.getBoundingClientRect()
          return tr.right > ir.right + 1 || tr.left < ir.left - 1
        })
        return JSON.stringify({
          overflowX: body ? body.scrollWidth - body.clientWidth : -1,
          readonlyCount: items.length,
          clipped: bad.length
        })
      })()`))
      check(
        `${p.name}：弹窗正文没有横向溢出（2 列没被挤爆）`,
        clip.overflowX <= 1,
        `横向溢出 ${clip.overflowX}px`
      )
      check(
        `${p.name}：只读标志位的小格子没有被裁掉`,
        clip.clipped === 0,
        clip.readonlyCount ? `${clip.readonlyCount} 个格子，越界 ${clip.clipped} 个` : '本表单没有这类格子（跳过）'
      )

      /* ---------- 需求③：添加地块不再隐藏页面 ---------- */
      const pickClicked = await cdp.evalJs(`(() => {
        const b = document.querySelector(${JSON.stringify(p.pickBtnSel)})
        if (!b) return false
        b.click()
        return true
      })()`)
      check(`${p.name}：能点「添加地块」`, pickClicked)
      for (let i = 0; i < 20; i++) {
        await sleep(300)
        if (await cdp.evalJs(`!!document.querySelector('.pick-stage')`)) break
      }
      await sleep(700)

      const pk = JSON.parse(await cdp.evalJs(`(() => {
        var stage = document.querySelector('.pick-stage')
        var acts = document.querySelector('.pick-stage__actions')
        var header = document.querySelector('.screen-header')
        var d = window.__vm.dialog()
        var root = document.querySelector('.screen-modal-root')
        var body = document.querySelector('.data-screen__body')
        return JSON.stringify({
          stage: !!stage,
          stagePE: stage ? getComputedStyle(stage).pointerEvents : null,
          actions: window.__vm.box(acts),
          headerBottom: header ? header.getBoundingClientRect().bottom : null,
          hidden: window.__vm.hiddenCount(),
          pickingFlag: window.__vm.pickingFlag(),
          dialogVisible: !!d && d.getBoundingClientRect().height > 0,
          dialogBox: window.__vm.box(d),
          modalVisibility: root ? getComputedStyle(root).visibility : null,
          bodyOpacity: body ? getComputedStyle(body).opacity : null,
          bodyVisible: body ? body.getBoundingClientRect().height > 0 : null,
          cancelBtn: !!Array.prototype.slice.call(document.querySelectorAll('.pick-stage button'))
            .find(function (b) { return window.__vm.norm(b.textContent) === '取消绘制' }),
          doneBtn: !!Array.prototype.slice.call(document.querySelectorAll('.pick-stage button'))
            .find(function (b) { return window.__vm.norm(b.textContent) === '完成绘制' }),
          hitMap: window.__vm.hit(300, 200)
        })
      })()`))

      check(`${p.name}：进入了选取模式（覆盖层出现）`, pk.stage && pk.cancelBtn && pk.doneBtn)
      check(
        `${p.name}：**没有隐藏任何页面**（不存在 .is-pick-hidden）`,
        pk.hidden === 0,
        `隐藏元素 ${pk.hidden} 个`
      )
      check(
        `${p.name}：body 上不再打选取模式的标记类（页面层与浮层都不再被藏）`,
        pk.pickingFlag === false,
        `body.is-picking-map = ${pk.pickingFlag}`
      )
      check(
        `${p.name}：页面内容仍然可见（内容区 opacity=1 且有高度）`,
        pk.bodyOpacity === '1' && pk.bodyVisible === true,
        `opacity ${pk.bodyOpacity} / 有高度 ${pk.bodyVisible}`
      )
      check(
        `${p.name}：**发起选取的弹窗仍然可见**（不再被藏起来）`,
        pk.dialogVisible && pk.modalVisibility === 'visible',
        `对话框有高度 ${pk.dialogVisible} / visibility ${pk.modalVisibility}`
      )
      check(
        `${p.name}：覆盖层不吃鼠标，选取时地图仍可操作`,
        pk.stagePE === 'none' && (/canvas/i.test(String(pk.hitMap)) || /cesium|s3dm/i.test(String(pk.hitMap))),
        `覆盖层 ${pk.stagePE}；(300,200) 命中 ${pk.hitMap}`
      )
      check(
        `${p.name}：两个按钮落在**顶栏之下**，不压导航`,
        pk.actions && pk.headerBottom !== null && pk.actions.top >= pk.headerBottom,
        `按钮顶 ${r1(pk.actions && pk.actions.top)} ≥ 顶栏底 ${r1(pk.headerBottom)}`
      )

      // Esc = 取消绘制，且**不能**把填了一半的表单一起关掉
      await cdp.pressKey('Escape', 'Escape', 27)
      await sleep(900)
      const afterEsc = JSON.parse(await cdp.evalJs(`JSON.stringify({
        stage: !!document.querySelector('.pick-stage'),
        dialog: !!window.__vm.dialog(),
        pickingFlag: window.__vm.pickingFlag()
      })`))
      check(
        `${p.name}：Esc 只退出选取，弹窗（表单）仍在`,
        !afterEsc.stage && afterEsc.dialog,
        `覆盖层 ${afterEsc.stage} / 弹窗 ${afterEsc.dialog}`
      )

      // 再进一次，用「完成绘制」退出
      await cdp.evalJs(`(() => { const b = document.querySelector(${JSON.stringify(p.pickBtnSel)}); if (b) b.click(); return !!b })()`)
      await sleep(1500)
      const doneClicked = await cdp.evalJs(`(() => {
        const b = Array.prototype.slice.call(document.querySelectorAll('.pick-stage button'))
          .find(function (x) { return window.__vm.norm(x.textContent) === '完成绘制' })
        if (!b) return false
        b.click()
        return true
      })()`)
      await sleep(900)
      const afterDone = JSON.parse(await cdp.evalJs(`JSON.stringify({
        stage: !!document.querySelector('.pick-stage'),
        dialog: !!window.__vm.dialog()
      })`))
      check(
        `${p.name}：「完成绘制」回到表单（弹窗不关）`,
        doneClicked && !afterDone.stage && afterDone.dialog,
        `覆盖层 ${afterDone.stage} / 弹窗 ${afterDone.dialog}`
      )

      // 关闭弹窗，准备下一个面板
      await cdp.evalJs(`(() => {
        const b = document.querySelector('.screen-modal__close')
        if (b) b.click()
        return !!b
      })()`)
      await sleep(900)
      check(
        `${p.name}：关闭后弹窗卸载干净`,
        !(await cdp.evalJs(`!!window.__vm.dialog()`))
      )
      console.log('')
    }

    /* ---------------- 回归：其它弹窗仍然是「模态」 ---------------- */
    /*
     * 本轮把 .screen-modal 的 pointer-events 改成了 none（好让 :dimmed="false"
     * 只改一个类就能放行背景）。这条改动是**全局**的，所以必须回归：
     * 默认弹窗（这里用「移除原因」确认框，它没传 dimmed）仍要压暗背景、挡住背景点击。
     */
    console.log('================ 回归：其它弹窗仍是模态 ================')
    const removeLink = await cdp.evalJs(`(() => {
      const b = Array.prototype.slice.call(document.querySelectorAll('.land-entry button, .facility-entry button'))
        .find(function (x) { return window.__vm.norm(x.textContent) === '移除' })
      if (!b) return false
      b.click(); return true
    })()`)
    check('回归：能点开某条记录的「移除」（先出二次确认）', removeLink)
    await sleep(700)
    const confirmed = await cdp.evalJs(`(() => {
      const b = Array.prototype.slice.call(document.querySelectorAll('button'))
        .filter(function (x) { return window.__vm.norm(x.textContent) === '确定' && x.offsetParent !== null })
      if (!b.length) return false
      b[0].click(); return true
    })()`)
    check('回归：二次确认里的「确定」可点（浮层没被改动影响）', confirmed)
    for (let i = 0; i < 20; i++) {
      await sleep(300)
      if (await cdp.evalJs(`!!window.__vm.dialog()`)) break
    }
    await sleep(700)
    const reg = JSON.parse(await cdp.evalJs(`(() => {
      var d = window.__vm.dialog(), m = window.__vm.mask(), h = window.__vm.head()
      return JSON.stringify({
        exists: !!d,
        width: d ? d.getBoundingClientRect().width : -1,
        maskBg: m ? getComputedStyle(m).backgroundColor : null,
        maskPE: m ? getComputedStyle(m).pointerEvents : null,
        headCursor: h ? getComputedStyle(h).cursor : null,
        headTabIndex: h ? h.getAttribute('tabindex') : null,
        hit: window.__vm.hit(300, 200)
      })
    })()`))
    check(
      '回归：默认弹窗仍然**压暗**背景（rgba(1,14,17,0.72)）',
      String(reg.maskBg).indexOf('0.72') !== -1,
      `mask background = ${reg.maskBg}`
    )
    check(
      '回归：默认弹窗的遮罩仍然**挡住**背景点击',
      reg.maskPE === 'auto' && /screen-modal__mask/.test(String(reg.hit)),
      `遮罩 ${reg.maskPE}；(300,200) 命中 ${reg.hit}`
    )
    check(
      '回归：默认弹窗不可拖动（draggable 默认关）',
      reg.headCursor !== 'move' && !reg.headTabIndex,
      `标题栏 cursor ${reg.headCursor} / tabindex ${reg.headTabIndex}`
    )
    await cdp.evalJs(`(() => {
      const b = Array.prototype.slice.call(document.querySelectorAll('.screen-modal button'))
        .find(function (x) { return window.__vm.norm(x.textContent) === '取消' })
      if (b) b.click(); return !!b
    })()`)
    await sleep(700)
    check('回归：确认框取消后弹窗卸载干净', !(await cdp.evalJs(`!!window.__vm.dialog()`)))
    console.log('')

    const failed = results.filter((r) => !r.ok)
    console.log('================================================')
    console.log(`断言：${results.length - failed.length} / ${results.length} 通过`)
    if (failed.length) {
      console.log('失败项：\n  - ' + failed.map((f) => f.name).join('\n  - '))
      process.exitCode = 1
    } else {
      console.log('✓ 全部通过：2 列 + 高度减半 + 可拖动 + 背景不变暗且地图可操作 + 选取不再隐藏页面（两个面板一致）')
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
