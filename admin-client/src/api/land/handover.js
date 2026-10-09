import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { getDictItems } from '@/components/dict/JDictSelectUtil'

/**
 * 浏览器原生下载（文件流）不能带自定义请求头，而 jeecg 的 JwtFilter 同时支持
 * 从 query string 的 token 参数取令牌，所以这里统一拼上 token。
 * （与 api/land/archive.js、api/land/ledger.js 的 buildDownloadUrl 一致）
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
 * 道路交付及养护协议移交事项接口（方案 2.3.2 第 6 项）
 *
 * 后端：org.jeecg.modules.land.archive.handover.controller.RoadHandoverController
 * 权限码：land:handover:list / add / edit / delete / archive / export
 *
 * 台账（第 7 项，1340 条道路普查）与移交事项（第 6 项，只登记真正发生/在办的移交）的分工：
 * 本模块管「交付/养护协议/正式移交办理到哪一步、协议签了没、养护期从哪天到哪天」。
 */
export const handoverUrl = {
  list: '/land/archive/handover/list',
  queryById: '/land/archive/handover/queryById',
  stat: '/land/archive/handover/stat',
  countByStatus: '/land/archive/handover/countByStatus',
  add: '/land/archive/handover/add',
  edit: '/land/archive/handover/edit',
  changeStatus: '/land/archive/handover/status',
  delete: '/land/archive/handover/delete',
  deleteBatch: '/land/archive/handover/deleteBatch',
  generateNo: '/land/archive/handover/generateNo',
  checkNo: '/land/archive/handover/checkNo',
  relatedArchives: '/land/archive/handover/relatedArchives',
  archivePick: '/land/archive/handover/archive/pick',
  linkArchive: '/land/archive/handover/linkArchive',
  unlinkArchive: '/land/archive/handover/unlinkArchive',
  exportXls: '/land/archive/handover/exportXls'
}

/* ==========================================================================
 * 一、接口封装（16 个）
 * ========================================================================== */

export function queryHandoverPage (params) {
  return getAction(handoverUrl.list, params)
}

export function queryHandoverById (id) {
  return getAction(handoverUrl.queryById, { id })
}

export function queryHandoverStat (params) {
  return getAction(handoverUrl.stat, params || {})
}

export function queryHandoverCountByStatus (params) {
  return getAction(handoverUrl.countByStatus, params || {})
}

export function addHandover (data) {
  return postAction(handoverUrl.add, data)
}

export function editHandover (data) {
  return putAction(handoverUrl.edit, data)
}

export function changeHandoverStatus (id, status) {
  return postAction(`${handoverUrl.changeStatus}?id=${encodeURIComponent(id)}&status=${encodeURIComponent(status)}`)
}

export function deleteHandover (id) {
  return deleteAction(handoverUrl.delete, { id })
}

export function deleteHandoverBatch (ids) {
  return deleteAction(handoverUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 预生成移交编号 YJ-{yyyy}-{4位}（仅预览，允许手工改写） */
export function generateHandoverNo (year) {
  return getAction(handoverUrl.generateNo, year ? { year } : {})
}

/** 移交编号唯一校验（编辑时带 id 排除自身） */
export function checkHandoverNo (params) {
  return getAction(handoverUrl.checkNo, params)
}

/** 某条移交事项的关联档案 */
export function queryHandoverArchives (id, limit) {
  return getAction(handoverUrl.relatedArchives, limit ? { id, limit } : { id })
}

/** 挑档案 */
export function pickHandoverArchives (params) {
  return getAction(handoverUrl.archivePick, params || {})
}

export function linkHandoverArchive (id, archiveId) {
  return postAction(`${handoverUrl.linkArchive}?id=${encodeURIComponent(id)}&archiveId=${encodeURIComponent(archiveId)}`)
}

export function unlinkHandoverArchive (id) {
  return postAction(`${handoverUrl.unlinkArchive}?id=${encodeURIComponent(id)}`)
}

/** 按当前查询条件导出 Excel（浏览器直接下载） */
export function exportHandoverXls (params) {
  window.open(buildDownloadUrl(handoverUrl.exportXls, params), '_blank')
}

/* ==========================================================================
 * 二、共享常量与展示工具
 * ========================================================================== */

/** 状态（固定 3 值，不入字典；与后端 HandoverStatus 逐字一致） */
export const HANDOVER_STATUS = ['待移交', '移交中', '已移交']

/** 功能区取值（与台账、迁移脚本口径一致） */
export const GNQ_OPTIONS = ['生态城', '经开区', '高新区', '保税区', '土地发展中心']

/** 道路等级常见取值（旧库 dldj 里出现的写法；只作下拉候选，允许自由填写） */
export const DLDJ_OPTIONS = ['城市主干道', '城市主干路', '城市次干路', '城市支路', '快速路', '支路']

/** 字典 code */
export const DICT = {
  handoverType: 'land_road_handover_type'
}

const dictCache = {}

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

export function dictText (dictCode, value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const items = dictCache[dictCode] || []
  const hit = items.filter(item => String(item.value) === String(value))[0]
  return hit ? (hit.text || hit.label || value) : value
}

export function loadAllDicts () {
  return Promise.all([loadDictItems(DICT.handoverType)])
}

/* ---------------- 配色（★ 只用 antd 预设色，无强调色返回 undefined） ---------------- */

export function statusColor (status) {
  if (status === '已移交') {
    return 'green'
  }
  if (status === '移交中') {
    return 'orange'
  }
  // 待移交 / 未知 → undefined（默认灰底）
  return undefined
}

export function statusDotColor (status) {
  if (status === '已移交') {
    return '#10b981'
  }
  if (status === '移交中') {
    return '#f59e0b'
  }
  return '#94a3b8'
}

/* ---------------- 业务提示 ---------------- */

/**
 * 该移交事项是否「协议信息缺失」（协议编号与协议签订日期都为空）。
 *
 * 迁移来的 84 条全部属于这种：旧库只有「是否移交=是」这一个标志位，
 * 协议编号/日期、移交日期、养护期都没有 → 需要在页面上显式提示去补录，
 * 否则这批数据会一直「看起来已移交、实际没有任何协议凭证」。
 */
export function missingAgreement (record) {
  if (!record) {
    return false
  }
  return !record.agreementNo && !record.agreementDate
}

/**
 * 养护期提示：已过期 / 90 天内到期 / 正常；无养护截止日期返回空串。
 */
export function maintenanceWarning (record) {
  if (!record || !record.maintenanceEnd) {
    return ''
  }
  const end = new Date(String(record.maintenanceEnd).replace(/-/g, '/'))
  if (isNaN(end.getTime())) {
    return ''
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.floor((end.getTime() - today.getTime()) / 86400000)
  if (days < 0) {
    return `养护期已于 ${record.maintenanceEnd} 到期（逾期 ${-days} 天），请确认是否续签`
  }
  if (days <= 90) {
    return `养护期将于 ${record.maintenanceEnd} 到期（还有 ${days} 天）`
  }
  return ''
}

/** 台账/移交事项 之间互跳用的链接（档案/配套项目 → 本模块的落点） */
export function handoverPageUrl (params) {
  const query = []
  Object.keys(params || {}).forEach(key => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  })
  return `/land/archive/handover${query.length ? '?' + query.join('&') : ''}`
}

export { buildDownloadUrl }

export default {
  handoverUrl,
  buildDownloadUrl,
  queryHandoverPage,
  queryHandoverById,
  queryHandoverStat,
  queryHandoverCountByStatus,
  addHandover,
  editHandover,
  changeHandoverStatus,
  deleteHandover,
  deleteHandoverBatch,
  generateHandoverNo,
  checkHandoverNo,
  queryHandoverArchives,
  pickHandoverArchives,
  linkHandoverArchive,
  unlinkHandoverArchive,
  exportHandoverXls,
  loadDictItems,
  dictText,
  loadAllDicts,
  statusColor,
  statusDotColor,
  missingAgreement,
  maintenanceWarning,
  handoverPageUrl
}
