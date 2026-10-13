/**
 * 地块选取会话（「选取地块」全屏地图模式的共享状态）
 * ==================================================================
 * 【为什么需要它，而不是让表单直接传 props】
 *   「选取地块」的入口在**表单弹窗**里（LandFormModal / FacilityFormModal），
 *   而表单弹窗经 ScreenModal 挂到了 document.body；全屏地图模式的载体
 *   ScreenPickStage 也挂在 body（由 views/screen/index.vue 渲染，那一层才拿得到
 *   真实的 Cesium 地图）。两者是**兄弟节点**，谁也不在谁的组件树里，
 *   表单没法用 props / $emit 直接跟覆盖层说话。
 *
 *   本工程已有的跨组件通信手段是 `$bus`（utils/vueBus.js），但它**不保存状态**：
 *   如果只是 emit 一个事件，那么「先挂了覆盖层才点按钮」没问题，
 *   而「页面刷新时深链直接带着 ?pick=1 进来」就没人在听那个事件。
 *   所以这里用一个极小的响应式（Vue 2 的 Vue.observable）单例同时承担两件事：
 *     · 状态：谁发起的、当前是不是处于选取模式；
 *     · 通道：状态一变，挂在 body 的覆盖层自己就渲染出来了，不依赖事件时序。
 *
 * 【为什么只允许一个发起者】
 *   存量缺陷：本模块的表单弹窗、详情弹窗都是**常驻渲染**的（例如
 *   LandEntryPanel 一直渲染着 `land-form-modal`），再加一个永远监听 $bus 的
 *   全局覆盖层，就会出现「N 个监听者抢同一份数据、点一次按钮弹 N 个同名弹窗」
 *   （见 facility 检索面板里那句「@change 两个输入框都会触发」的备注）。
 *   因此这里用**令牌**做唯一性：start 时发一个 token，覆盖层只认可当前 token
 *   的发起者，结束/失败时表单必须 verbatim 把 token 交回来（isIssuer(token) 校验），
 *   「不是自己的会话」一律忽略 —— 从根上避免多个表单同时打开时互相打断。
 *
 * 【边界】本文件只保存「选取会话」这一点点状态，不放业务数据：
 *   画好的图形属于调用方（表单），由 ScreenPickStage 通过 done 事件抛回。
 *   ★ 地图绘制选取功能**尚未实现**（drawTools 目前是空实现占位），
 *   所以 done 的 payload 暂时恒为空 —— 表单收到空结果时保留字段原值。
 */
import Vue from 'vue'

/**
 * 一个选取会话。
 * @typedef {object} PickSession
 * @property {boolean} active     是否处于「选取地块」全屏模式
 * @property {string}  token      会话令牌（唯一标志本次选取，由 start 生成）
 * @property {string}  source     发起方标识：'land' 经营性用地 / 'facility' 配套项目
 * @property {string}  title      按钮下方的一句话说明（谁在选什么）
 * @property {string}  issuer     发起组件实例的唯一 id（inspect 出问题时定位用）
 */

let seq = 0

/** 生成会话令牌：时间戳 + 自增序号，保证同一毫秒内多次 start 也不会重复 */
function nextToken (source) {
  seq += 1
  return `${source}-${Date.now().toString(36)}-${seq}`
}

/** 共享状态。用 Vue.observable（Vue 2.6+）而不是自建发布订阅：少一个要维护的坑 */
const state = Vue.observable({
  /** 是否有正在进行的选取会话 —— ScreenPickStage 只认这一个字段 */
  active: false,
  /** 当前会话令牌；没有会话时为空串 */
  token: '',
  /** 发起方（'land' / 'facility'），覆盖层用来写提示文案 */
  source: '',
  /** 发起方给出的说明文案（可为空，覆盖层有兜底文案） */
  title: '',
  /** 发起方标识（排查「到底是谁开的选取模式」时用，不参与任何判断） */
  issuer: ''
})

/**
 * 开始一次选取会话。
 *
 * 返回值语义有两种情况，调用方**必须**用它覆盖自己保存的 pickToken：
 *   · 没有会话 → 新建一个会话，返回新令牌；
 *   · 已有会话 → **返回那个会话的令牌**（不抢占）。
 *     这样「按钮被连点两下」不会换掉会话（否则第一次那个令牌就作废了）。
 *
 * ⚠ 已知边界（2026-10-13 审查指出）：当**另一个**表单已经开着会话时，
 *   本方法会把别人的令牌返给它。若两个表单交替使用，可能留下陈旧令牌。
 *   因此调用方在保存返回值之后，必须用 `isPickIssuer(pickToken)` 复查；
 *   本工程的两个调用方（LandFormModal / FacilityFormModal）在 closePick 与
 *   事件回调里都做了这一步，见那边的注释。
 * @param {{ source?: string, title?: string, issuer?: string }} [options]
 * @returns {string} 本次会话的令牌（表单要拿它去 end / done / isPickIssuer）
 */
export function startPick (options) {
  if (state.active) return state.token
  const opts = options || {}
  state.active = true
  state.token = nextToken(opts.source || 'unknown')
  state.source = opts.source || ''
  state.title = opts.title || ''
  state.issuer = opts.issuer || ''
  return state.token
}

/**
 * 结束一次选取会话（取消选取 / 选择完成都走它）。
 * ★ 令牌校验是必须的：不是自己发起的会话不许关，
 *   否则「两个表单都开着时，后一个的取消会关掉前一个的选取模式」。
 * @param {string} token startPick 返回的令牌
 * @returns {boolean} 是否真的结束了（false = 令牌不对，调用方应忽略）
 */
export function endPick (token) {
  if (!state.active) return false
  if (token && token !== state.token) return false
  state.active = false
  state.token = ''
  state.source = ''
  state.title = ''
  state.issuer = ''
  return true
}

/**
 * 令牌是不是当前会话的发起者。
 * 表单在收到覆盖层抛回的结果时用它过滤掉「别人的会话」。
 * @param {string} token
 * @returns {boolean}
 */
export function isPickIssuer (token) {
  return !!state.active && !!token && state.token === token
}

/** 当前会话令牌（没有会话时为空串）；给「点了按钮才发现会话已经结束了」这类兜底用 */
export function currentPickToken () {
  return state.token
}

/**
 * 直接判断「此刻是不是正在选取模式」。
 * ⚠ 只在**非渲染路径**（事件回调、mounted 等）里用它；
 *   模板里请用 pickState.active，否则拿不到 Vue 的依赖收集、界面不会更新。
 * @returns {boolean}
 */
export function isPicking () {
  return state.active
}

export const pickState = state

export default { startPick, endPick, isPickIssuer, currentPickToken, isPicking, pickState }
