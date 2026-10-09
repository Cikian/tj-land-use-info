import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { getDictItems } from '@/components/dict/JDictSelectUtil'

/**
 * 浏览器原生下载（文件流）不能带自定义请求头，而 jeecg 的 JwtFilter 同时支持
 * 从 query string 的 token 参数取令牌，所以这里统一拼上 token。
 * （与 api/land/archive.js、ledger.js、escalation.js 的 buildDownloadUrl 完全一致）
 */
function buildDownloadUrl (path, params) {
  const query = []
  Object.keys(params || {}).forEach(key => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  })
  const token = Vue.ls.get(ACCESS_TOKEN)
  if (token) {
    query.push(`token=${encodeURIComponent(token)}`)
  }
  const base = window._CONFIG['domianURL'] || ''
  return `${base}${path}${query.length ? '?' + query.join('&') : ''}`
}

/**
 * 竣工验收项目历史工程资料数字化档案接口（方案 2.3.2 第 8 项）
 *
 * 后端：org.jeecg.modules.land.archive.completion.controller.CompletionArchiveController
 * 权限码：land:completion:list / add / edit / delete / export（均为按钮权限）
 *
 * 关键约定：
 *  1. 数字化状态（未数字化/数字化中/已数字化）是代码枚举，不走字典；
 *     项目类型与保管期限走字典（措辞可能被中心调整）；
 *  2. 档案编号默认由后端生成（JG-{yyyy}-{4位}），允许用户手工改写；
 *  3. 扫描件不在本模块上传 —— 统一走档案管理模块，本模块只通过 linkArchive 记指针；
 *  4. 存量历史项目不一定有对应宗地/配套项目，landId/facilityId/crzdbh 都允许为空。
 */
export const completionUrl = {
  list: '/land/archive/completion/list',
  queryById: '/land/archive/completion/queryById',
  stat: '/land/archive/completion/stat',
  countByStatus: '/land/archive/completion/countByStatus',
  add: '/land/archive/completion/add',
  edit: '/land/archive/completion/edit',
  changeStatus: '/land/archive/completion/status',
  delete: '/land/archive/completion/delete',
  deleteBatch: '/land/archive/completion/deleteBatch',
  generateNo: '/land/archive/completion/generateNo',
  checkNo: '/land/archive/completion/checkNo',
  relatedArchives: '/land/archive/completion/relatedArchives',
  archivePick: '/land/archive/completion/archive/pick',
  linkArchive: '/land/archive/completion/linkArchive',
  unlinkArchive: '/land/archive/completion/unlinkArchive',
  exportXls: '/land/archive/completion/exportXls'
}

/* ==========================================================================
 * 一、接口封装（16 个）
 * ========================================================================== */

/** 档案分页列表（档案页 / 查询共用同一套条件） */
export function queryCompletionPage (params) {
  return getAction(completionUrl.list, params)
}

/** 档案详情（含数字化进度 + 关联扫描件列表） */
export function queryCompletionById (id) {
  return getAction(completionUrl.queryById, { id })
}

/** 汇总与 6 组分类统计 */
export function queryCompletionStat (params) {
  return getAction(completionUrl.stat, params || {})
}

/** 各数字化状态计数（顶部 tab 角标，固定 3 项） */
export function queryCompletionCountByStatus (params) {
  return getAction(completionUrl.countByStatus, params || {})
}

/** 新增历史档案 */
export function addCompletion (data) {
  return postAction(completionUrl.add, data)
}

/** 编辑历史档案 */
export function editCompletion (data) {
  return putAction(completionUrl.edit, data)
}

/** 变更数字化状态 */
export function changeDigitizeStatus (id, status) {
  return postAction(`${completionUrl.changeStatus}?id=${encodeURIComponent(id)}&status=${encodeURIComponent(status)}`)
}

/** 删除历史档案 */
export function deleteCompletion (id) {
  return deleteAction(completionUrl.delete, { id })
}

/** 批量删除历史档案 */
export function deleteCompletionBatch (ids) {
  return deleteAction(completionUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 预生成档案编号 JG-{yyyy}-{4位}（仅预览，允许手工改写） */
export function generateArchiveNo (year) {
  return getAction(completionUrl.generateNo, year ? { year } : {})
}

/** 档案编号唯一校验（编辑时带 id 排除自身） */
export function checkArchiveNo (params) {
  return getAction(completionUrl.checkNo, params)
}

/** 某条档案的关联扫描件（配套项目ID 优先、宗地编号兜底） */
export function queryRelatedArchives (id, limit) {
  return getAction(completionUrl.relatedArchives, limit ? { id, limit } : { id })
}

/** 挑档案（关联时用；可按配套项目/宗地过滤或全局搜） */
export function pickArchives (params) {
  return getAction(completionUrl.archivePick, params || {})
}

/** 关联扫描件 */
export function linkCompletionArchive (id, archiveId) {
  return postAction(`${completionUrl.linkArchive}?id=${encodeURIComponent(id)}&archiveId=${encodeURIComponent(archiveId)}`)
}

/** 取消关联扫描件 */
export function unlinkCompletionArchive (id) {
  return postAction(`${completionUrl.unlinkArchive}?id=${encodeURIComponent(id)}`)
}

/** 按当前查询条件导出档案信息 Excel（需求原文「可快速导出档案信息」） */
export function exportCompletionXls (params) {
  window.open(buildDownloadUrl(completionUrl.exportXls, params), '_blank')
}

/* ==========================================================================
 * 二、模块内共享常量（状态 / 字典 / 配色 / 展示小工具）
 * ========================================================================== */

/** 数字化状态（固定 3 值，不入字典；与后端 DigitizeStatus 逐字一致） */
export const DIGITIZE_STATUS = ['未数字化', '数字化中', '已数字化']

/** 字典 code */
export const DICT = {
  projectType: 'land_completion_project_type',
  retention: 'land_completion_retention'
}

/** 扫描分辨率候选值（DPI；标准档位，可自由填其它整数） */
export const SCAN_DPI_OPTIONS = [200, 300, 400, 600]

/** 配套设施类别候选值（与 t_archive / 台账模块的口径一致，仅作录入提示） */
export const PTSSLB_OPTIONS = ['道路', '市政排水', '给水', '中水', '燃气', '路灯', '绿化', '交通设施', '其他']

/* ---------------- 字典 ---------------- */

const dictCache = {}

/** 读取字典项并缓存（失败退化为空数组，不阻塞页面） */
export function loadDictItems (dictCode) {
  if (dictCache[dictCode]) {
    return Promise.resolve(dictCache[dictCode])
  }
  return getDictItems(dictCode).then(items => {
    dictCache[dictCode] = items || []
    return dictCache[dictCode]
  }).catch(() => {
    dictCache[dictCode] = []
    return dictCache[dictCode]
  })
}

/** 字典值 → 文本（字典没加载出来时原样返回，不会出现空白列） */
export function dictText (dictCode, value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const items = dictCache[dictCode] || []
  const hit = items.filter(item => String(item.value) === String(value))[0]
  return hit ? (hit.text || hit.label || value) : value
}

/** 一次性把本模块 2 个字典预热到缓存里 */
export function loadAllDicts () {
  return Promise.all([loadDictItems(DICT.projectType), loadDictItems(DICT.retention)])
}

/* ---------------- 配色（★ 只用 antd 预设色，无强调色返回 undefined） ---------------- */

/**
 * 数字化状态 → a-tag 颜色。
 * ★ 绝不能返回 'default'：它不是 antd 预设色，会被当成自定义色 →
 *   白字 + 非法背景色被丢弃 → 白底白字看不见（见《档案管理-实现说明》7.5）。
 */
export function digitizeStatusColor (status) {
  if (status === '已数字化') {
    return 'green'
  }
  if (status === '数字化中') {
    return 'blue'
  }
  // 未数字化 / 未知 → undefined（默认灰底）
  return undefined
}

/** 数字化状态色板（统计图、进度条与左侧小圆点复用） */
export function digitizeDotColor (status) {
  if (status === '已数字化') {
    return '#10b981'
  }
  if (status === '数字化中') {
    return '#2e7cf6'
  }
  return '#94a3b8'
}

/** 档案状态 → a-tag 颜色（关联扫描件列表里的 t_archive.status） */
export function archiveStatusColor (status) {
  if (status === '已归档') {
    return 'green'
  }
  if (status === '审核中') {
    return 'orange'
  }
  if (status === '归档中') {
    return 'blue'
  }
  return undefined
}

/* ---------------- 展示小工具 ---------------- */

/**
 * 数字化状态 → 进度百分比。
 * 口径与后端 CompletionSupport.digitizePercent 完全一致（0 / 50 / 100），
 * 只用于进度条视觉表达，不参与任何统计。
 */
export function digitizePercent (status) {
  if (status === '已数字化') {
    return 100
  }
  if (status === '数字化中') {
    return 50
  }
  return 0
}

/** 投资额（万元）→ 千分位文本 */
export function formatAmount (value) {
  const num = Number(value)
  if (!value && value !== 0) {
    return ''
  }
  if (isNaN(num)) {
    return String(value)
  }
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 页数 → 千分位文本（历史档案动辄几万页） */
export function formatCount (value) {
  const num = Number(value)
  if (!value && value !== 0) {
    return ''
  }
  return isNaN(num) ? String(value) : num.toLocaleString('zh-CN')
}

/** 字节 → 可读大小（关联扫描件列表用） */
export function formatSize (bytes) {
  const value = Number(bytes)
  if (!value) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(1)} ${units[unit]}`
}

export { buildDownloadUrl }

export default {
  completionUrl,
  buildDownloadUrl,
  queryCompletionPage,
  queryCompletionById,
  queryCompletionStat,
  queryCompletionCountByStatus,
  addCompletion,
  editCompletion,
  changeDigitizeStatus,
  deleteCompletion,
  deleteCompletionBatch,
  generateArchiveNo,
  checkArchiveNo,
  queryRelatedArchives,
  pickArchives,
  linkCompletionArchive,
  unlinkCompletionArchive,
  exportCompletionXls,
  loadDictItems,
  dictText,
  loadAllDicts,
  digitizeStatusColor,
  digitizeDotColor,
  archiveStatusColor,
  digitizePercent,
  formatAmount,
  formatCount,
  formatSize
}
