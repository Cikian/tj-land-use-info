/**
 * 数据管理「右侧检索列 + 可拖高列表」版式验证（真实浏览器 / CDP）
 * ==================================================================
 * 断言的是**需求本身**，不是「代码没报错」：
 *   ① 列表默认高度压到屏幕下方，每屏只露 3~4 条数据；
 *   ② 检索容器竖排在屏幕右侧、与左侧二级导航**左右对称**；
 *   ③ 列表高度可以**拖动**改变（且拖动方向、幅度都对，含上下限夹紧与键盘调节）；
 *   ④ 经营性用地与配套项目**两个面板一致**。
 *
 * ★ 所有几何量都换算成**设计画布坐标**（1920×1080）再比较：
 *   画布是 transform: scale(k) 的，直接读 getBoundingClientRect 得到的是真实像素，
 *   在 1894×987 的窗口里 k≈0.914，拿真实像素去和 1080/40/200 这些设计值比会全部对不上。
 *
 * ★ 拖动用 CDP 的 Input.dispatchMouseEvent（真实输入域），不用元素的 dispatchEvent：
 *   后者绕过命中测试，也绕不过「画布缩放换算」这个真正的考点。
 *
 * 用法：node perf/verify-data-layout.js [http://localhost:3001]
 *
 * ★ 端口：本机两个前端同时在跑 —— **3000 是管理端 admin-client，3001 才是大屏端**。
 *   指错端口时页面能打开、但登录后完全是另一个应用，断言会成片失败且原因看不出来，
 *   所以下面有一道「标题必须是大屏端」的显式护栏。
 */
const { spawn } = require('child_process')
const http = require('http')
const WebSocket = require('ws')
const os = require('os')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = process.argv[2] || 'http://localhost:3001'
const USER_DATA = os.tmpdir() + '\\edge-verify-data-layout-' + Date.now()
const PORT = 12300 + Math.floor(Math.random() * 400)

/* 设计画布的宽高是**自适应**的（见 HELPERS 注释），运行时从画布上读，不在这里写死 */

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
  /** 真实鼠标拖动：从 (x1,y1) 按下，经中间点移动到 (x2,y2)，再抬起 */
  async dragMouse (x1, y1, x2, y2) {
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: Math.round(x1), y: Math.round(y1), button: 'left', clickCount: 1 })
    const steps = 5
    for (let i = 1; i <= steps; i++) {
      const t = i / steps
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
  async pressKey (key, code, vk) {
    const base = { key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk }
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyDown' }, base))
    await this.send('Input.dispatchKeyEvent', Object.assign({ type: 'keyUp' }, base))
  }
}

/* ------------------------------------------------------------------
 * 页面内测量工具：全部输出**设计画布坐标**
 *
 * ★ 坐标系必须从画布自己身上读，不能假定 1920×1080：
 *   ScreenStage 是「纵向贴合高度、横向自适应」的 ——
 *     scale    = min(vh / 1080, vw / minDesignWidth, maxScale)
 *     designW  = vw / scale   ← **不是 1920**，随窗口宽高比变化
 *     designH  = vh / scale   ← 高度贴合时恒为 1080
 *   实测 1894×987 视口：scale≈0.9139、设计画布 2072.46×1080。
 *   组件把这两个数写在画布的 --stage-w / --stage-h 上，直接读即可。
 *   （踩过的坑：用 rect.width/1920 当 scale 会得到 1，于是所有横向断言
 *     都差一个 0.914 的系数，看起来像「版式全错」，其实是脚本算错。）
 * ------------------------------------------------------------------ */
const HELPERS = `
window.__dvl = {
  norm: function (t) { return String(t || '').replace(/\\s/g, '') },
  canvas: function () { return document.querySelector('.screen-stage__canvas') },
  stageVar: function (name) {
    var c = this.canvas()
    if (!c) return NaN
    return parseFloat(getComputedStyle(c).getPropertyValue(name))
  },
  /* 真实缩放 k：画布**渲染高度** ÷ 设计高度（不能用宽度，宽度是自适应的） */
  k: function () {
    var c = this.canvas()
    if (!c) return 1
    var h = this.stageVar('--stage-h')
    return h ? c.getBoundingClientRect().height / h : 1
  },
  dw: function () { return this.stageVar('--stage-w') },
  dh: function () { return this.stageVar('--stage-h') },
  /* 元素在设计画布坐标系里的盒子 */
  box: function (el) {
    if (!el) return null
    var c = this.canvas()
    if (!c) return null
    var s = this.k()
    var cr = c.getBoundingClientRect()
    var r = el.getBoundingClientRect()
    return {
      left: (r.left - cr.left) / s,
      top: (r.top - cr.top) / s,
      right: (r.right - cr.left) / s,
      bottom: (r.bottom - cr.top) / s,
      width: r.width / s,
      height: r.height / s
    }
  },
  boxOf: function (sel) { return this.box(document.querySelector(sel)) },
  /* 列表高度直接读 offsetHeight：元素在缩放画布内，布局值就是画布像素，无需换算 */
  listHeight: function (sel) {
    var el = document.querySelector(sel)
    return el ? el.offsetHeight : -1
  },
  /* 可见行数：完全落在表格滚动视口内的数据行 */
  rows: function () {
    var scroll = document.querySelector('.screen-data-table__scroll')
    if (!scroll) return { total: 0, visible: 0, rowHeight: 0 }
    var sr = scroll.getBoundingClientRect()
    var rows = Array.prototype.slice.call(document.querySelectorAll('.screen-data-table__table tbody tr'))
      .filter(function (tr) { return !tr.classList.contains('is-empty') })
    var visible = rows.filter(function (tr) {
      var r = tr.getBoundingClientRect()
      return r.top >= sr.top - 1 && r.bottom <= sr.bottom + 1
    }).length
    var rowHeight = rows.length ? rows[0].getBoundingClientRect().height / this.k() : 0
    return { total: rows.length, visible: visible, rowHeight: rowHeight }
  },
  /* 拖动条中心（真实像素，供 CDP 输入用） */
  barCenter: function (scopeSel) {
    var bar = document.querySelector(scopeSel + ' .screen-resize-bar')
    if (!bar) return null
    var r = bar.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
  },
  /* 搜索表单是否单列竖排 */
  searchLayout: function (formSel) {
    var form = document.querySelector(formSel)
    if (!form) return null
    var grid = form.querySelector('[class$="__grid"]')
    var cols = grid ? getComputedStyle(grid).gridTemplateColumns : ''
    var field = form.querySelector('.screen-field')
    var label = field ? field.querySelector('.screen-field__label') : null
    var control = field ? field.querySelector('.screen-field__control') : null
    var lb = label ? label.getBoundingClientRect() : null
    var cb = control ? control.getBoundingClientRect() : null
    return {
      tracks: cols ? cols.split(' ').filter(Boolean).length : 0,
      columns: cols,
      stacked: !!(lb && cb && lb.bottom <= cb.top + 2),
      labelBottom: lb ? lb.bottom : null,
      controlTop: cb ? cb.top : null
    }
  },
  /* 画布正中偏上一点的命中测试（那里应该是地图，不是被列表/检索列吃掉） */
  mapHit: function (y) {
    var c = this.canvas()
    var cr = c.getBoundingClientRect()
    var s = this.k()
    var x = cr.left + (this.dw() / 2) * s
    var yy = cr.top + y * s
    var top = document.elementFromPoint(x, yy)
    return top ? (top.tagName.toLowerCase() + '.' + String(top.className || '').slice(0, 30)) : null
  }
}
'ok'
`

const results = []
function check (name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`  ${ok ? '✓' : '✗'} ${name}${detail !== undefined ? '  —— ' + detail : ''}`)
}
const near = (a, b, tol) => Math.abs(a - b) <= tol
const r1 = (n) => Math.round(n * 10) / 10

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
      throw new Error(
        `打开的不是大屏端（title="${title}"）。` +
        '大屏端 stargis-client-web 通常是 http://localhost:3001，3000 是管理端 admin-client。'
      )
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
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '登录')
      if (b) b.click(); return !!b
    })()`)
    let ready = false
    for (let i = 0; i < 90; i++) {
      await sleep(1000)
      const s = JSON.parse(await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const ok = Array.from(document.querySelectorAll('.ant-modal button')).find(b => norm(b.textContent) === '确认')
        if (ok) ok.click()
        return JSON.stringify({ hash: location.hash, viewer: !!window.viewer })
      })()`))
      if (s.hash.indexOf('login') === -1 && s.viewer) { ready = true; break }
    }
    if (!ready) throw new Error('没能进入大屏首页')
    await cdp.evalJs(HELPERS)
    console.log('### 已登录，Cesium 就绪')

    /* ---------------- 进入数据管理 ---------------- */
    await cdp.evalJs(`(() => {
      const norm = t => String(t || '').replace(/\\s/g, '')
      const b = Array.from(document.querySelectorAll('button')).find(x => norm(x.textContent) === '数据管理')
      if (b) b.click(); return !!b
    })()`)
    await sleep(3000)

    const geo = JSON.parse(await cdp.evalJs(`JSON.stringify({
      k: window.__dvl.k(),
      dw: window.__dvl.dw(),
      dh: window.__dvl.dh(),
      vw: window.innerWidth,
      vh: window.innerHeight
    })`))
    const scale = geo.k
    const DESIGN_W = geo.dw
    const DESIGN_H = geo.dh
    console.log(`### 视口 ${geo.vw}×${geo.vh} → 画布缩放 k = ${r1(scale)}，设计画布 ${r1(DESIGN_W)}×${r1(DESIGN_H)}`)
    console.log('### （横向坐标上限是自适应的设计宽，不是 1920；几何量均已换算成设计坐标）\n')

    /** 对某一个面板跑完整套断言 */
    const verifyPanel = async (tabLabel, scope, formSel, title) => {
      console.log(`================ ${title} ================`)
      await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(tabLabel)})
        if (b) b.click(); return !!b
      })()`)
      for (let i = 0; i < 30; i++) {
        await sleep(500)
        if (await cdp.evalJs(`!!document.querySelector('${scope}')`)) break
      }
      await sleep(2500)

      const st = await cdp.evalJs(`JSON.stringify({
        root: !!document.querySelector('${scope}'),
        list: window.__dvl.boxOf('${scope}__list'),
        search: window.__dvl.boxOf('${scope}__search'),
        nav: window.__dvl.boxOf('.data-screen__nav'),
        body: window.__dvl.boxOf('.data-screen__body'),
        bodyPE: (function(){ var b = document.querySelector('.data-screen__body'); return b ? getComputedStyle(b).pointerEvents : null })(),
        rootPE: (function(){ var b = document.querySelector('${scope}'); return b ? getComputedStyle(b).pointerEvents : null })(),
        listHeight: window.__dvl.listHeight('${scope}__list'),
        rows: window.__dvl.rows(),
        layout: window.__dvl.searchLayout('${formSel}'),
        bar: !!document.querySelector('${scope} .screen-resize-bar'),
        searchScrollable: (function(){
          var p = document.querySelector('${scope}__search .screen-panel__body')
          return p ? getComputedStyle(p).overflowY : null
        })(),
        mapHitAbove: window.__dvl.mapHit(300),
        modalCount: document.querySelectorAll('.screen-modal-root').length
      })`)
      const s = JSON.parse(st)

      check(`${title}：面板已渲染`, s.root)
      if (!s.root || !s.list || !s.search) {
        console.log('  （面板结构不完整，跳过后续几何断言）')
        return
      }

      /* ---------- 需求①：列表压到屏幕下方，只露 3~4 行 ---------- */
      check(
        `${title}：列表贴在屏幕下方（下沿 = 1080-40 = 1040）`,
        near(s.list.bottom, DESIGN_H - 40, 2),
        `下沿 ${r1(s.list.bottom)}`
      )
      check(
        `${title}：列表默认高度只露 3~4 行`,
        s.rows.visible >= 3 && s.rows.visible <= 4,
        `可见 ${s.rows.visible} 行（列表高 ${s.listHeight}px，行高 ${r1(s.rows.rowHeight)}px，共 ${s.rows.total} 行）`
      )
      check(
        `${title}：列表左右与左侧栏对称留白（250 / 1920-250）`,
        near(s.list.left, 250, 2) && near(s.list.right, DESIGN_W - 250, 2),
        `left ${r1(s.list.left)} / right ${r1(s.list.right)}`
      )

      /* ---------- 需求②：检索列竖排在右侧、与左侧栏对称 ---------- */
      check(
        `${title}：检索列在屏幕右侧（right = 30，宽 200）`,
        near(DESIGN_W - s.search.right, 30, 2) && near(s.search.width, 200, 2),
        `右边距 ${r1(DESIGN_W - s.search.right)}，宽 ${r1(s.search.width)}`
      )
      check(
        `${title}：检索列与左侧导航**左右对称**`,
        near(DESIGN_W - s.search.right, s.nav.left, 1.5) &&
          near(s.search.width, s.nav.width, 1.5) &&
          near(s.search.top, s.nav.top, 1.5) &&
          near(DESIGN_H - s.search.bottom, DESIGN_H - s.nav.bottom, 1.5),
        `检索列 right${r1(DESIGN_W - s.search.right)}/top${r1(s.search.top)}/宽${r1(s.search.width)}  ` +
        `导航 left${r1(s.nav.left)}/top${r1(s.nav.top)}/宽${r1(s.nav.width)}`
      )
      check(
        `${title}：检索列与列表不重叠（留 20px 间距）`,
        s.list.right < s.search.left,
        `列表右 ${r1(s.list.right)} < 检索列左 ${r1(s.search.left)}`
      )
      check(
        `${title}：检索条件是**单列竖排**（不是 4 列网格）`,
        s.layout && s.layout.tracks === 1,
        s.layout ? `列数 ${s.layout.tracks}（${s.layout.columns}）` : '未取到'
      )
      check(
        `${title}：窄列里字段标签在控件**上方**（stacked）`,
        s.layout && s.layout.stacked,
        s.layout ? `标签底 ${r1(s.layout.labelBottom)} ≤ 控件顶 ${r1(s.layout.controlTop)}` : '未取到'
      )
      check(
        `${title}：检索面板可滚动（条件展开后不会被裁掉）`,
        s.searchScrollable === 'auto' || s.searchScrollable === 'scroll',
        `overflow-y: ${s.searchScrollable}`
      )

      /* ---------- 展开「更多条件」后，窄列里不能出现横向裁切 ---------- */
      await cdp.evalJs(`(() => {
        const btn = document.querySelector('${formSel} [class$="__toggle"]')
        if (btn && btn.getAttribute('aria-expanded') === 'false') btn.click()
        return !!btn
      })()`)
      await sleep(900)
      const wide = JSON.parse(await cdp.evalJs(`(() => {
        const body = document.querySelector('${scope}__search .screen-panel__body')
        const form = document.querySelector('${formSel}')
        if (!body || !form) return 'null'
        const bb = body.getBoundingClientRect()
        // 每个控件是否都完整落在检索列的可用宽度里（右侧不越界）
        const ctrls = Array.prototype.slice.call(form.querySelectorAll(
          '.screen-input__inner, .screen-select__inner, .screen-date-input, .screen-button'
        ))
        const overflowing = ctrls.filter(function (el) {
          const r = el.getBoundingClientRect()
          return r.width > 0 && (r.right > bb.right + 1 || r.left < bb.left - 1)
        }).length
        // 区间日期的两个输入框各有多宽（窄列里最容易被压坏的就是它）
        // ★ 换算成**设计像素**再判断：原生日期输入不会换行，
        //   宽度不够时 "2026-10-16" 会被截断，而截断在真实像素下看不出来
        const k = window.__dvl.k()
        const dates = Array.prototype.slice.call(form.querySelectorAll('.screen-date-input__segment input'))
          .map(function (i) { return Math.round(i.getBoundingClientRect().width / k) })
        return JSON.stringify({
          hOverflow: body.scrollWidth - body.clientWidth,
          ctrls: ctrls.length,
          overflowing: overflowing,
          dates: dates,
          expanded: (function () {
            const t = form.querySelector('[class$="__toggle"]')
            return t ? t.getAttribute('aria-expanded') : null
          })()
        })
      })()`))
      check(
        `${title}：展开「更多条件」后检索列**没有横向裁切**`,
        wide && wide.hOverflow <= 1 && wide.overflowing === 0,
        `横向溢出 ${wide && wide.hOverflow}px，越界控件 ${wide && wide.overflowing}/${wide && wide.ctrls} 个`
      )
      /*
       * 只有**确实有日期区间**的表单才查这一条：
       * 配套检索条件里没有日期字段，硬要求两个日期框会变成假失败。
       */
      if (wide && wide.dates.length) {
        check(
          `${title}：区间日期的输入框在 166px 列里能放下完整日期（各 ≥ 120 设计px）`,
          wide.dates.length >= 2 && wide.dates.every((w) => w >= 120),
          `各宽 ${wide.dates.join(' / ')} 设计px`
        )
      } else {
        check(`${title}：检索条件里没有日期区间字段（跳过日期宽度断言）`, true, '无 .screen-date-input')
      }

      /* ---------- 地图仍然可交互（列表变矮的意义所在） ---------- */
      check(
        `${title}：列表上方留给地图，且命中测试落到地图（不是透明面板）`,
        /canvas/i.test(String(s.mapHitAbove)) || /cesium/i.test(String(s.mapHitAbove)),
        `命中 ${s.mapHitAbove}`
      )
      check(
        `${title}：内容区 / 面板根节点都不吃事件（pointer-events: none）`,
        s.bodyPE === 'none' && s.rootPE === 'none',
        `body ${s.bodyPE} / panel ${s.rootPE}`
      )

      /* ---------- 需求③：拖动改高 ---------- */
      check(`${title}：上沿有拖动条`, s.bar)
      const before = s.listHeight
      const c = await cdp.evalJs(`JSON.stringify(window.__dvl.barCenter('${scope}'))`)
      const center = JSON.parse(c)
      if (center) {
        const dragUp = 100 // 真实像素
        await cdp.dragMouse(center.x, center.y, center.x, center.y - dragUp)
        await sleep(700)
        const after = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        const expect = before + dragUp / scale
        check(
          `${title}：往上拖 ${dragUp}px → 列表变高，且**按画布缩放换算**`,
          after > before && near(after, expect, 4),
          `高 ${before} → ${after}（期望 ${r1(expect)}；若未换算缩放只会到 ${before + dragUp}）`
        )

        const rowsAfter = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__dvl.rows())`))
        check(
          `${title}：列表变高后可见行数随之增加`,
          rowsAfter.visible > s.rows.visible,
          `${s.rows.visible} → ${rowsAfter.visible} 行`
        )

        // 下限夹紧：往下拖很远
        const c2 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__dvl.barCenter('${scope}'))`))
        await cdp.dragMouse(c2.x, c2.y, c2.x, c2.y + 1200)
        await sleep(700)
        const atMin = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        check(
          `${title}：往下拖到极限会被**下限**夹住（200）`,
          near(atMin, 200, 2),
          `高 ${atMin}`
        )

        // 上限夹紧：往上拖很远
        const c3 = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__dvl.barCenter('${scope}'))`))
        await cdp.dragMouse(c3.x, c3.y, c3.x, c3.y - 1500)
        await sleep(700)
        const atMax = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        const maxBox = JSON.parse(await cdp.evalJs(`JSON.stringify(window.__dvl.boxOf('${scope}__list'))`))
        check(
          `${title}：往上拖到极限会被**上限**夹住（936，正好顶到顶栏下沿 104）`,
          near(atMax, 936, 2) && near(maxBox.top, 104, 2),
          `高 ${atMax}，上沿 ${r1(maxBox.top)}`
        )

        // 键盘调节：先聚焦拖动条
        await cdp.evalJs(`(() => {
          const bar = document.querySelector('${scope} .screen-resize-bar')
          if (bar) bar.focus()
          return document.activeElement === bar
        })()`)
        const beforeKey = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        await cdp.pressKey('ArrowDown', 'ArrowDown', 40)
        await sleep(400)
        const afterKey = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        check(
          `${title}：键盘 ↓ 也能调（无障碍：不只有鼠标能改）`,
          near(afterKey, beforeKey - 16, 2),
          `${beforeKey} → ${afterKey}`
        )

        // 还原成默认高度，便于人眼/截图复核
        await cdp.evalJs(`(() => {
          const bar = document.querySelector('${scope} .screen-resize-bar')
          if (bar) { bar.focus(); return true }
          return false
        })()`)
        await cdp.pressKey('Home', 'Home', 36)
        await sleep(300)
        await cdp.evalJs(`(() => {
          const bar = document.querySelector('${scope} .screen-resize-bar')
          if (bar) bar.focus()
          return true
        })()`)
        for (let i = 0; i < 50; i++) {
          const h = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
          if (h >= 360) break
          await cdp.pressKey('ArrowUp', 'ArrowUp', 38)
        }
        const restored = await cdp.evalJs(`window.__dvl.listHeight('${scope}__list')`)
        check(`${title}：键盘可把高度调回默认量级`, restored >= 360, `高 ${restored}`)
      }
    }

    await verifyPanel('经营性用地信息录入', '.land-entry', '.land-search', '经营性用地')
    await verifyPanel('配套地块数据录入', '.facility-entry', '.facility-search', '配套项目')

    /* ---------------- 其它四个面板没被改坏 ---------------- */
    console.log('\n================ 回归：其它四个页签仍是旧版式 ================')
    for (const tab of ['经营性用地批量导入', '配套信息批量导入', '配套附件管理', '数据更新与移除']) {
      await cdp.evalJs(`(() => {
        const norm = t => String(t || '').replace(/\\s/g, '')
        const b = Array.from(document.querySelectorAll('.data-screen__nav-item')).find(x => norm(x.textContent) === ${JSON.stringify(tab)})
        if (b) b.click(); return !!b
      })()`)
      await sleep(1800)
      const info = JSON.parse(await cdp.evalJs(`JSON.stringify({
        split: (function(){ var b = document.querySelector('.data-screen__body'); return b ? b.classList.contains('is-split') : null })(),
        pe: (function(){ var b = document.querySelector('.data-screen__body'); return b ? getComputedStyle(b).pointerEvents : null })(),
        childBox: (function(){
          var b = document.querySelector('.data-screen__body')
          if (!b || !b.firstElementChild) return null
          return window.__dvl.box(b.firstElementChild)
        })()
      })`))
      check(
        `${tab}：保持旧版式（内容区 250→1890、顶部 104、可交互）`,
        info.split === false && info.pe === 'auto' && info.childBox &&
          near(info.childBox.left, 250, 2) && near(info.childBox.right, DESIGN_W - 30, 2) &&
          near(info.childBox.top, 104, 2) && near(info.childBox.bottom, DESIGN_H - 40, 2),
        `is-split=${info.split} pe=${info.pe} box=${
          info.childBox ? `${r1(info.childBox.left)}~${r1(info.childBox.right)} / ${r1(info.childBox.top)}~${r1(info.childBox.bottom)}` : 'null'}`
      )
    }

    const failed = results.filter((r) => !r.ok)
    console.log('\n================================================')
    console.log(`断言：${results.length - failed.length} / ${results.length} 通过`)
    if (failed.length) {
      console.log('失败项：\n  - ' + failed.map((f) => f.name).join('\n  - '))
      process.exitCode = 1
    } else {
      console.log('✓ 全部通过：列表压到屏幕下方只露 3~4 行、检索列竖排在右侧与左侧栏对称、高度可拖动（含换算/夹紧/键盘）')
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
