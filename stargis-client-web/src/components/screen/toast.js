/**
 * 大屏消息提示 —— 命令式服务
 * ---------------------------------------------------------------
 * 大屏固定使用青绿暗色主题，antd 的 `$message` 是浅色气泡，观感不统一；
 * 这个文件把 ScreenToast.vue 挂成一个常驻单例，并暴露命令式 API：
 *
 *   在 Vue 组件内：
 *     this.$screenToast.success('保存成功')
 *     this.$screenToast.error(res.message)
 *
 *   在非组件代码（例如 api 层 / 路由守卫）内：
 *     import { toast } from '@/components/screen/toast'
 *     toast.warning('已开始导出，请稍候…')
 *
 * 单例挂载在 document.body，而不是大屏浮层里：
 *   浮层整体是 pointer-events: none 且有 overflow 裁剪，
 *   提示条如果挂在浮层内会被裁掉，所以和 ScreenPopover 一样挂 body。
 */

import Vue from 'vue'
import ScreenToast from './ScreenToast.vue'

let instance = null

/** 惰性创建并挂载单例 */
function getInstance () {
  if (instance) return instance

  const Ctor = Vue.extend(ScreenToast)
  instance = new Ctor()
  instance.$mount()

  if (typeof document !== 'undefined' && document.body) {
    document.body.appendChild(instance.$el)
  }
  return instance
}

/**
 * 展示一条提示
 * @param {string} message 文案
 * @param {'info'|'success'|'warning'|'danger'} tone 语气
 * @param {number} duration 自动关闭时间（ms），传 0 表示不自动关闭
 * @returns {number|null} 提示 id
 */
export function show (message, tone = 'info', duration = 2600) {
  if (message === undefined || message === null || message === '') return null
  return getInstance().push(message, tone, duration)
}

/** 命令式 API（可在任意位置 import 使用） */
export const toast = {
  info: (message, duration) => show(message, 'info', duration),
  success: (message, duration) => show(message, 'success', duration),
  warning: (message, duration) => show(message, 'warning', duration),
  error: (message, duration) => show(message, 'danger', duration),
  show,
  clear: () => getInstance().clear(),
}

/** 判断接口返回是否成功，失败时自动弹出后端的 message —— 各页面统一用这个收敛提示逻辑 */
export function assertSuccess (res, fallback = '操作失败') {
  if (res && res.success) return true
  toast.error((res && res.message) || fallback)
  return false
}

/** Vue 插件：注册 this.$screenToast */
const ScreenToastPlugin = {
  install (VueCtor) {
    VueCtor.prototype.$screenToast = toast
  },
}

export default ScreenToastPlugin
