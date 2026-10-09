/**
 * 登录态统一出口（中台 + Java 业务后端 jeecg）
 * ===============================================================
 * 【为什么需要这个文件】
 * 本系统有两套互相独立的登录态：
 *
 *   1. 中台：令牌放在 localStorage 的 ACCESS_TOKEN / REFRESH_TOKEN，
 *      以 query / body 的 `access_token` 传给 /app/** 接口；
 *   2. Java 业务后端（jeecg）：令牌放在 JEECG_ACCESS_TOKEN，
 *      以请求头 `X-Access-Token` 传给 {VUE_DATA_JAVA_URL}/** 接口。
 *
 * 只要其中任意一套失效（过期 / 被踢 / 令牌为空），页面继续留着也没有任何意义：
 * 用户点什么都只会不断弹「登录已过期」。所以这里把「退出到登录页」收口成一条路径：
 *
 *   logoutAndGoToLogin(提示文案)
 *     → 弹一次提示（可选）
 *     → dispatch('Logout')：同步清掉两套本地登录态，并尽力通知两个后端注销
 *     → 整页跳到登录页（顺带丢掉 URL 上的单点登录参数）
 *
 * 调用方：
 *   · src/utils/request.js —— 接口返回鉴权失败（中台 / jeecg 都会命中）
 *   · 顶栏用户区的「退出登录」（手动退出走同一条路径）
 */

import Vue from 'vue'
import store from '@/store'
import {
  ACCESS_TOKEN,
  USER_NAME,
  USER_INFO,
  UI_CACHE_DB_DICT_DATA,
  TENANT_ID,
  CACHE_INCLUDED_ROUTES,
  JEECG_ACCESS_TOKEN,
  JEECG_USER_INFO,
  JEECG_DICT_ITEMS,
  OAUTH2_LOGIN_PAGE_PATH
} from '@/store/mutation-types'

/** 账号口令登录页（与 permission.js 的白名单一致） */
export const LOGIN_PAGE_PATH = '/user/login'

/** 中台的刷新令牌键名，Logout 里也是这个字面量 */
const REFRESH_TOKEN = 'REFRESH_TOKEN'

/**
 * 失效提示至少可见的时长（毫秒）。
 * 登录态失效时人还没离开页面，得先让用户看清「为什么被弹出来」再跳登录页。
 * 必须小于下面兜底跳转的 1500ms。
 */
const NOTICE_VISIBLE_MS = 1200

/**
 * 是否已经发起过「登录态失效 → 跳登录页」的处置。
 * 一次页面加载里只处置一次：令牌失效时往往并发好几个请求，
 * 不节流会连续弹提示、连续打注销接口。
 * 跳转后页面会整页重载，这个标记自然重置。
 */
let handlingSessionExpiry = false

/** 供 request 层判断「正在退出」，退出过程中不再刷提示 */
export function isHandlingSessionExpiry () {
  return handlingSessionExpiry
}

/**
 * 是否 OAuth2APP 环境（企业微信 / 钉钉容器）。
 * 与 utils/util.js 的 isOAuth2AppEnv 判断一致，这里内联一份，
 * 避免 session.js 反向往 util.js 拉依赖（util.js 依赖较重，会加重循环引用）。
 */
function isOAuth2AppEnv () {
  return /wxwork|dingtalk/i.test(navigator.userAgent)
}

/** 失效时应该落到哪个登录页 */
export function loginPagePath () {
  return isOAuth2AppEnv() ? OAUTH2_LOGIN_PAGE_PATH : LOGIN_PAGE_PATH
}

/**
 * 清空两套登录态的本地存储与 Vuex（只动本地，不发任何请求，重复调用安全）。
 * 正常路径由 store 的 Logout 完成清理，这里是兜底：
 * 万一 dispatch 抛异常，也要保证本地令牌被清掉，不能留下半登录态。
 */
export function clearLoginState () {
  const keys = [
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    USER_NAME,
    USER_INFO,
    UI_CACHE_DB_DICT_DATA,
    CACHE_INCLUDED_ROUTES,
    TENANT_ID,
    JEECG_ACCESS_TOKEN,
    JEECG_USER_INFO,
    JEECG_DICT_ITEMS
  ]
  keys.forEach((key) => {
    try {
      Vue.ls.remove(key)
    } catch (e) {
      // localStorage 不可用（隐私模式等）时忽略
    }
  })
  try {
    store.commit('SET_TOKEN', '')
    store.commit('SET_JEECG_TOKEN', '')
    store.commit('SET_JEECG_INFO', {})
    store.commit('SET_JEECG_DICT_ITEMS', {})
  } catch (e) {
    // ignore
  }
}

/**
 * 整页跳转到登录页。
 *
 * 【为什么带上 `?_=时间戳`】
 * 路由是 hash 模式，只改 `#/...` 属于同文档内跳转，浏览器不会重新加载页面，
 * 于是 Vuex 里的登录态、Cesium 地图、以及「本次已处置失效」的标记都会留下来。
 * 这里刻意在 `?` 上带一个一次性参数，强制浏览器真正重新加载一次：
 * 既保证退出后是干净的应用实例，也保证本地标记随页面一起重置。
 *
 * 【为什么只保留 origin + pathname】
 * 单点登录入口形如 `/?access_token=xx&sceneid=xx&username=xx&refresh_token=xx`，
 * permission.js 会把这四个参数重新写回本地。若跳转时把它们带上，
 * 就会出现「刚退出又被登进去」的死循环，所以必须整段丢掉。
 */
export function goToLoginPage () {
  const location = window.location
  const base = location.protocol + '//' + location.host + location.pathname
  const target = base + '?_=' + Date.now() + '#' + loginPagePath()
  try {
    location.replace(target)
  } catch (e) {
    location.href = target
  }
}

/** 提示一次「登录态已失效」 */
function notifySessionExpired (description) {
  try {
    if (Vue.prototype.$Jnotification && Vue.prototype.$Jnotification.error) {
      Vue.prototype.$Jnotification.error({
        message: '系统提示',
        description,
        duration: 4
      })
    }
  } catch (e) {
    // 提示失败不影响退出流程
  }
}

/**
 * 退出到登录页（登录态失效 / 手动退出共用）。
 *
 * @param {string} [description] 需要提示给用户的文案；不传则静默退出（手动退出时不打扰）
 * @param {number} [fallbackDelay] 兜底跳转延时（毫秒）：
 *        注销接口可能很慢（axios 超时 60s），不能等它，超时也要跳登录页
 */
export function logoutAndGoToLogin (description, fallbackDelay) {
  if (handlingSessionExpiry) {
    return
  }
  handlingSessionExpiry = true

  const startedAt = Date.now()
  if (description) {
    notifySessionExpired(description)
  }

  let jumped = false
  const doJump = () => {
    if (jumped) return
    jumped = true
    goToLoginPage()
  }
  /**
   * 失效提示要留出可读时间：注销请求在本机往往几十毫秒就回来了，
   * 立刻跳转会让「登录已过期」只闪一下（用户只看到页面被刷掉，不知道原因）。
   * 手动退出没有提示，不需要这个等待。
   */
  const jump = () => {
    const wait = description ? NOTICE_VISIBLE_MS - (Date.now() - startedAt) : 0
    if (wait > 0) {
      setTimeout(doJump, wait)
    } else {
      doJump()
    }
  }

  try {
    // Logout 会**同步**清掉两套本地登录态（Vuex + localStorage），
    // 再异步通知中台 /app/outApi 与 jeecg /sys/logout 注销；两边都以旧令牌调用，
    // 因此必须在它内部先取令牌、后清理，不能在这里提前清。
    const task = store.dispatch('Logout')
    if (task && typeof task.then === 'function') {
      task.then(jump, jump)
    } else {
      jump()
    }
  } catch (e) {
    clearLoginState()
    jump()
  }

  // 兜底：注销请求挂住（网络中断、后端无响应）时也必须离开当前页面
  setTimeout(doJump, typeof fallbackDelay === 'number' ? fallbackDelay : 1500)
}
