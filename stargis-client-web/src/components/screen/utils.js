/**
 * 大屏基础组件 —— 公共工具方法
 * 纯函数，无副作用，便于单测与复用。
 */

/** 是否开启了「减少动态效果」的系统偏好 */
export function prefersReducedMotion () {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 数值安全转换，非法值回退到 fallback */
export function toNumber (value, fallback = 0) {
  const num = typeof value === 'number' ? value : parseFloat(value)
  return Number.isFinite(num) ? num : fallback
}

/** 区间夹取 */
export function clamp (value, min, max) {
  return Math.min(Math.max(value, min), max)
}

/**
 * 千分位格式化
 * @param {number|string} value
 * @param {number} precision 小数位
 * @param {boolean} grouped 是否使用千分位
 */
export function formatNumber (value, precision = 0, grouped = true) {
  const num = toNumber(value)
  const fixed = num.toFixed(precision)
  if (!grouped) return fixed
  const [int, decimal] = fixed.split('.')
  const withGroup = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return decimal ? `${withGroup}.${decimal}` : withGroup
}

/** 百分比格式化：0.165 -> '16.5%'（入参即为百分数时不需乘以 100） */
export function formatPercent (value, precision = 2) {
  return `${toNumber(value).toFixed(precision)}%`
}

/** 两位序号：1 -> '01' */
export function padIndex (index, length = 2) {
  return String(index).padStart(length, '0')
}

/** 生成稳定的组件唯一 id（用于 SVG 渐变 / clipPath 的 id 隔离） */
let uidSeed = 0
export function uid (prefix = 'screen') {
  uidSeed += 1
  return `${prefix}-${uidSeed}`
}

/** 请求动画帧，兼容降级 */
export function raf (fn) {
  if (typeof window !== 'undefined' && window.requestAnimationFrame) {
    return window.requestAnimationFrame(fn)
  }
  return setTimeout(fn, 16)
}

/** 取消动画帧 */
export function caf (handle) {
  if (handle == null) return
  if (typeof window !== 'undefined' && window.cancelAnimationFrame) {
    window.cancelAnimationFrame(handle)
  } else {
    clearTimeout(handle)
  }
}

/** 缓出函数，用于数字滚动 */
export function easeOutCubic (t) {
  return 1 - Math.pow(1 - t, 3)
}
