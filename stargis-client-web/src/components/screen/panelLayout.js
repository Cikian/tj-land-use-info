/**
 * panelLayout 大屏「三块面板」的尺寸与折叠状态（按页面持久化）
 * ==================================================================
 * 需求（2026-11）：大屏所有「左 / 右 / 下」三块面板都要能拖动改宽/改高，
 * 并且都能像首页左右面板那样折叠隐藏。首页 / 地图管理 / 配套分析管理
 * 三块面板各自是独立组件；数据管理页的左二级导航 / 右检索列 / 底部列表
 * 是页面内部的三块，也接进同一份状态，避免再写一套存储与上下限。
 *
 * 为什么状态放在组件外（模块级）：
 *   1. 页面组件是 v-if 切换的（views/screen/index.vue 里整页模块一换就销毁），
 *      状态放组件 data 里会在切页时丢失；
 *   2. 三个页面各自的面板组件（HomeStatPanel / MapToolPanel / …）需要读写
 *      同一份尺寸，放 props 逐层传会把页面模板写得很啰嗦；
 *   3. 需求要求「永久记住」（localStorage），本来就要有一处收口。
 *
 * 单位：**设计画布像素**（ScreenStage 的 transform: scale(k) 之前的值）。
 *   拖动时屏幕位移是真实像素，由 ScreenResizeBar 除以 k 换算，这里不重复处理。
 *
 * 存储格式（key 带版本号，改结构时换 key 即可让旧值自然失效）：
 *   {
 *     "home":     { "left": { "size": 400, "collapsed": false }, "right": …, "bottom": … },
 *     "map":      { … },
 *     "analysis": { … }
 *   }
 * 读取时逐项夹紧到 [min, max]，防止手改 localStorage 或旧版本数据把面板撑爆/缩没。
 */
import Vue from 'vue'

/** 面板与屏幕边缘 / 面板与面板之间的留白（设计稿口径：30） */
export const PANEL_GAP = 30

/** 折叠后屏边只剩这一条留白（与 ScreenSideRail 的 30px 轨道一致） */
export const PANEL_COLLAPSED_INSET = PANEL_GAP

const STORAGE_KEY = 'screen-panel-layout-v1'

/**
 * 每个页面三块面板的默认尺寸与上下限。
 *
 * 上下限怎么定的（都按设计画布像素）：
 *   · 左 / 右面板 340：再窄，标题装饰、表格列、检索控件就会被裁；
 *     560（左）/ 720（右）：右面板常要装插件表单与结果表，给宽一点的余量；
 *   · 下方面板 160：至少露出表头 + 两行；700：1080 画布下不会顶掉顶栏标题。
 *
 * 默认值就是设计稿尺寸（左/右 400、属性表 321），保证第一次进入时
 * 与高保真逐像素一致；用户拖过之后才偏离设计稿。
 */
const PAGE_DEFAULTS = {
  home: {
    left: { size: 400, min: 340, max: 560, collapsed: false },
    right: { size: 400, min: 340, max: 560, collapsed: false },
    // 首页属性表设计稿就是 321 高；折叠后是 47 高的收起条
    bottom: { size: 321, min: 160, max: 700, collapsed: false }
  },
  map: {
    left: { size: 400, min: 340, max: 560, collapsed: false },
    right: { size: 400, min: 340, max: 720, collapsed: false },
    // 地图管理底部本来只有一条 47px 的「属性表」收起条，默认保持收起
    bottom: { size: 321, min: 160, max: 700, collapsed: true }
  },
  analysis: {
    left: { size: 400, min: 340, max: 560, collapsed: false },
    right: { size: 400, min: 340, max: 720, collapsed: false },
    bottom: { size: 321, min: 160, max: 700, collapsed: true }
  },
  /**
   * 数据管理页（2026-11 追加）：左二级导航 / 右检索列 / 底部列表。
   * 三块都在页面内部（不像首页那样各是一个独立组件），所以尺寸由页面
   * 通过 CSS 变量下发给 entry 面板，这里的值同时驱动 views/screen/index.vue
   * 的氛围蒙版（蒙版要跟着面板一起伸缩 / 折叠）。
   *   · 左/右 200 = 设计稿的导航与检索列宽度；160 是再窄标签就放不下的下限；
   *   · bottom 360 = 经营性用地/配套列表的默认高度（与 entry 面板原常量一致，
   *     上下限 200 / 936 也照搬那里的实测值；数据管理页的下方面板仍只拖高、不折叠）。
   */
  data: {
    left: { size: 200, min: 160, max: 340, collapsed: false },
    right: { size: 200, min: 160, max: 340, collapsed: false },
    bottom: { size: 360, min: 200, max: 936, collapsed: false }
  }
}

const SIDES = ['left', 'right', 'bottom']

/** 模块级单例：同一页面在任意组件里拿到的都是同一份响应式状态 */
const store = Vue.observable({ pages: {} })

/** localStorage 写入去抖句柄（拖动时 input 事件每帧都发，不能每帧都写盘） */
let saveTimer = null

function clamp (value, min, max, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num)) return fallback
  return Math.min(max, Math.max(min, Math.round(num)))
}

/** 读整份存储；坏数据（手改 / 旧格式）一律当作没有，回落到默认值 */
function readStorage () {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return {}
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch (e) {
    return {}
  }
}

/**
 * 写整份存储（去抖 180ms）。
 * ★ 只序列化 size / collapsed，不要把方法或 Vue 的 __ob__ 写进去；
 *   存储异常（隐私模式 / 配额满）静默忽略 —— 尺寸记不住不影响使用。
 */
function scheduleSave () {
  if (typeof window === 'undefined') return
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = null
    try {
      if (!window.localStorage) return
      const payload = {}
      Object.keys(store.pages).forEach((page) => {
        const layout = store.pages[page]
        payload[page] = {}
        SIDES.forEach((side) => {
          payload[page][side] = {
            size: layout[side].size,
            collapsed: layout[side].collapsed
          }
        })
      })
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
    } catch (e) {
      // 存不进去就只保留本次会话内的状态
    }
  }, 180)
}

/** 单块面板：默认值 → 存储值覆盖 → 逐项校验 */
function normalizeSide (saved, defaults) {
  const source = saved && typeof saved === 'object' ? saved : {}
  return {
    size: clamp(source.size, defaults.min, defaults.max, defaults.size),
    min: defaults.min,
    max: defaults.max,
    collapsed: typeof source.collapsed === 'boolean' ? source.collapsed : defaults.collapsed
  }
}

/**
 * 取某个页面的面板布局状态（首次调用时创建并读存储）。
 *
 * 返回的响应式对象结构：
 *   layout.left.size / layout.left.collapsed / layout.left.min / layout.left.max
 *   layout.setSize(side, value)      拖动 / 键盘调节时调用（自动夹紧 + 落盘）
 *   layout.setCollapsed(side, value) 折叠状态（自动落盘）
 *   layout.toggle(side)              折叠 / 展开
 *   layout.reset()                   恢复设计稿默认（调试 / 以后可挂到界面）
 *
 * @param {'home'|'map'|'analysis'} page 页面 key（也是存储里的一级键）
 */
export function usePanelLayout (page) {
  if (store.pages[page]) return store.pages[page]

  const defaults = PAGE_DEFAULTS[page] || PAGE_DEFAULTS.home
  const saved = readStorage()[page] || {}

  const state = {}
  SIDES.forEach((side) => {
    state[side] = normalizeSide(saved[side], defaults[side])
  })

  /** 改尺寸（**设计画布像素**）：夹到该面板的上下限，值没变就不落盘 */
  state.setSize = (side, value) => {
    const panel = state[side]
    if (!panel) return
    const next = clamp(value, panel.min, panel.max, panel.size)
    if (next === panel.size) return
    panel.size = next
    scheduleSave()
  }

  /** 直接设置折叠状态（持久化） */
  state.setCollapsed = (side, value) => {
    const panel = state[side]
    if (!panel) return
    const next = Boolean(value)
    if (next === panel.collapsed) return
    panel.collapsed = next
    scheduleSave()
  }

  /** 折叠 / 展开 */
  state.toggle = (side) => {
    state.setCollapsed(side, !state[side].collapsed)
  }

  /** 恢复设计稿默认（保持当前折叠状态不变） */
  state.reset = () => {
    SIDES.forEach((side) => {
      state[side].size = defaults[side].size
    })
    scheduleSave()
  }

  const observable = Vue.observable(state)
  store.pages[page] = observable
  return observable
}

/**
 * 折叠后下方面板要内缩到哪：展开时「面板宽 + 两侧各 30」，
 * 折叠后只剩屏边 30 —— 与首页属性表 460 → 30 的口径完全一致。
 */
export function panelInset (layout, side) {
  const panel = layout[side]
  if (!panel) return PANEL_GAP
  return panel.collapsed ? PANEL_COLLAPSED_INSET : panel.size + PANEL_GAP * 2
}

/**
 * 折叠位移距离（设计画布像素）：面板宽 + 屏边 30。
 * 侧栏折叠是整体滑出屏幕，滑少了会露出半块面板，滑多了会拖慢过渡。
 */
export function panelSlideDistance (layout, side) {
  const panel = layout[side]
  return (panel ? panel.size : 400) + PANEL_GAP
}
