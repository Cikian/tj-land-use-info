import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { getDictItems } from '@/components/dict/JDictSelectUtil'

/**
 * 浏览器原生下载（文件流）不能带自定义请求头，而 jeecg 的 JwtFilter 同时支持
 * 从 query string 的 token 参数取令牌，所以这里统一拼上 token。
 * （与 api/land/archive.js 的 buildDownloadUrl 完全一致，见设计文档 5.7「下载不带 token」）
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

/* ==========================================================================
 * 一、接口地址（设计文档 4.2：共 24 个）
 * ========================================================================== */
export const escalationUrl = {
  // ---- 项目（9）----
  projectList: '/land/escalation/project/list',
  projectQueryById: '/land/escalation/project/queryById',
  projectAdd: '/land/escalation/project/add',
  projectEdit: '/land/escalation/project/edit',
  projectDelete: '/land/escalation/project/delete',
  projectDeleteBatch: '/land/escalation/project/deleteBatch',
  projectGenNo: '/land/escalation/project/genProjectNo',
  projectCheckNo: '/land/escalation/project/checkNo',
  projectExportXls: '/land/escalation/project/exportXls',
  // ---- 材料（5）----
  materialList: '/land/escalation/material/list',
  materialSave: '/land/escalation/material/save',
  materialDelete: '/land/escalation/material/delete',
  materialDownload: '/land/escalation/material/download',
  materialPreview: '/land/escalation/material/preview',
  // ---- 意见记录（2）----
  recordList: '/land/escalation/record/list',
  recordAdd: '/land/escalation/record/add',
  // ---- 台账（3）----
  ledgerList: '/land/escalation/ledger/list',
  ledgerCountByStatus: '/land/escalation/ledger/countByStatus',
  ledgerExportXls: '/land/escalation/ledger/exportXls',
  // ---- 统计（5）----
  statByResult: '/land/escalation/stat/byResult',
  statByDept: '/land/escalation/stat/byDept',
  statByXzqh: '/land/escalation/stat/byXzqh',
  statByMonth: '/land/escalation/stat/byMonth',
  statByType: '/land/escalation/stat/byType'
}

/* ==========================================================================
 * 二、24 个接口封装
 * ========================================================================== */

/* ---------------- 项目（9） ---------------- */

/** 项目分页列表（录入 / 查询 共用） */
export function queryProjectList (params) {
  return getAction(escalationUrl.projectList, params)
}

/** 项目详情（含材料列表 materials、意见记录 records 时直接取用） */
export function queryProjectById (id) {
  return getAction(escalationUrl.projectQueryById, { id })
}

/** 新增项目（body 带 materials） */
export function addProject (data) {
  return postAction(escalationUrl.projectAdd, data)
}

/** 编辑项目（body 带 materials） */
export function editProject (data) {
  return putAction(escalationUrl.projectEdit, data)
}

/** 删除项目 */
export function deleteProject (id) {
  return deleteAction(escalationUrl.projectDelete, { id })
}

/** 批量删除项目 */
export function deleteProjectBatch (ids) {
  return deleteAction(escalationUrl.projectDeleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 预生成项目编号 TJ-{yyyy}-{4位}（仅预览，允许手工改写） */
export function genProjectNo (year) {
  return getAction(escalationUrl.projectGenNo, year ? { year } : {})
}

/** 项目编号唯一校验（编辑时带上 id 排除自身） */
export function checkProjectNo (params) {
  return getAction(escalationUrl.projectCheckNo, params)
}

/** 按查询条件导出项目 Excel */
export function exportProjectXls (params) {
  window.open(buildDownloadUrl(escalationUrl.projectExportXls, params), '_blank')
}

/* ---------------- 材料（5） ---------------- */

/** 某项目的材料列表 */
export function queryMaterialList (projectId) {
  return getAction(escalationUrl.materialList, { projectId })
}

/** 保存材料（项目已落库时直接挂到 projectId 下） */
export function saveMaterial (data) {
  return postAction(escalationUrl.materialSave, data)
}

/** 删除材料 */
export function deleteMaterial (id) {
  return deleteAction(escalationUrl.materialDelete, { id })
}

/** 下载材料（浏览器直接下载） */
export function downloadMaterial (id) {
  window.open(buildDownloadUrl(escalationUrl.materialDownload, { id }), '_blank')
}

/** 在线预览材料 */
export function previewMaterial (id) {
  window.open(buildDownloadUrl(escalationUrl.materialPreview, { id }), '_blank')
}

/* ---------------- 意见记录（2，append-only：无编辑/删除接口） ---------------- */

/** 某项目的意见记录（时间倒序） */
export function queryRecordList (projectId) {
  return getAction(escalationUrl.recordList, { projectId })
}

/**
 * 登记审核意见
 * @param {{projectId:string, recordType?:string, action?:string, opinion:string,
 *          attachmentIds?:string, status?:string}} data
 *        status 仅在「同时更新办理状态」勾选时传，默认不传（不自动改状态）
 */
export function addRecord (data) {
  return postAction(escalationUrl.recordAdd, data)
}

/* ---------------- 台账（3） ---------------- */

/** 台账分页列表（14 列口径） */
export function queryLedgerList (params) {
  return getAction(escalationUrl.ledgerList, params)
}

/** 台账各办理状态计数（顶部状态 tab 的角标） */
export function queryLedgerCountByStatus (params) {
  return getAction(escalationUrl.ledgerCountByStatus, params || {})
}

/** 按当前条件导出台账 Excel（14 列） */
export function exportLedgerXls (params) {
  window.open(buildDownloadUrl(escalationUrl.ledgerExportXls, params), '_blank')
}

/* ---------------- 统计（5），返回 NameCount 数组 ---------------- */

/** 按论证结果分布（饼图） */
export function queryStatByResult (params) {
  return getAction(escalationUrl.statByResult, params)
}

/** 按申报单位排行（条形图） */
export function queryStatByDept (params) {
  return getAction(escalationUrl.statByDept, params)
}

/** 按行政区划分布（条形图） */
export function queryStatByXzqh (params) {
  return getAction(escalationUrl.statByXzqh, params)
}

/** 按申报时间趋势（折线图，name 为 yyyy-MM） */
export function queryStatByMonth (params) {
  return getAction(escalationUrl.statByMonth, params)
}

/** 按项目类型分布（设计文档 4.2 的统计接口，页面作为补充信息展示） */
export function queryStatByType (params) {
  return getAction(escalationUrl.statByType, params)
}

/* ==========================================================================
 * 三、模块内共享常量与展示工具
 *
 * 设计文档 5.2 只列了 12 个文件，没有为「枚举 / 配色 / 字典文本」单独留文件；
 * 为避免在 4 个页面里各抄一份导致口径漂移，这里集中放在本模块唯一的 api 文件里，
 * 由各页面 import（不新增第 13 个文件，也不改动既有模块）。
 * ========================================================================== */

/** 办理状态（固定 4 值，不入字典；与设计文档 2.2 一致） */
export const STATUS_OPTIONS = ['未办理', '办理中', '已办结', '已归档']

/** 字典 code（设计文档 3.3） */
export const DICT = {
  projectType: 'land_escalation_project_type',
  argResult: 'land_escalation_arg_result',
  materialType: 'land_escalation_material_type',
  yn: 'yn'
}

/** 意见正文上限（原型硬性要素：0/1000 实时计数） */
export const RECORD_MAX_LENGTH = 1000

/** 5 个快捷短语（点击回填意见框，可再编辑） */
export const RECORD_QUICK_PHRASES = ['同意', '基本同意', '请补充材料', '需进一步论证', '不同意']

/** 记录类型（t_escalation_record.record_type） */
export const RECORD_TYPES = ['审核意见', '补充说明', '其他']

/** 材料上传白名单与上限（设计文档 3.2 第 9 条 / Q5：50MB） */
export const MATERIAL_MAX_SIZE_MB = 50
export const MATERIAL_ALLOWED_EXT = ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png']
export const MATERIAL_ALLOWED_EXT_TEXT = 'pdf / doc / docx / jpg / jpeg / png'

/**
 * 办理状态 → a-tag 颜色。
 * ★ 只用 antd 预设色；无强调色时返回 undefined（默认灰底），
 *   绝不能返回 'default' —— 它不是预设色，会被当成自定义色导致白底白字（见设计文档 5.7）。
 */
export function statusColor (status) {
  if (status === '办理中') {
    return 'blue'
  }
  if (status === '已办结') {
    return 'green'
  }
  if (status === '已归档') {
    return 'cyan'
  }
  // 未办理 / 未知 → undefined（默认灰）
  return undefined
}

/** 论证结果 → a-tag 颜色（通过=绿、基本通过=蓝、需补充材料=橙、需进一步论证=紫、不通过=红） */
export function argResultColor (result) {
  if (result === '通过') {
    return 'green'
  }
  if (result === '基本通过') {
    return 'blue'
  }
  if (result === '需补充材料') {
    return 'orange'
  }
  if (result === '需进一步论证') {
    return 'purple'
  }
  if (result === '不通过') {
    return 'red'
  }
  return undefined
}

/** 状态色板（审批/台账的左侧小圆点、统计图配色复用） */
export function statusDotColor (status) {
  if (status === '办理中') {
    return '#2e7cf6'
  }
  if (status === '已办结') {
    return '#10b981'
  }
  if (status === '已归档') {
    return '#06b6d4'
  }
  return '#94a3b8'
}

/**
 * 意见结论短语 → a-tag 颜色（与 5 个快捷短语一一对应；
 * 与 arg_result 的取值不同，所以单独一套，仍然只用 antd 预设色）
 */
export function actionColor (action) {
  if (action === '同意') {
    return 'green'
  }
  if (action === '基本同意') {
    return 'blue'
  }
  if (action === '请补充材料') {
    return 'orange'
  }
  if (action === '需进一步论证') {
    return 'purple'
  }
  if (action === '不同意') {
    return 'red'
  }
  return undefined
}

/* ---------------- 字典（land 域第一个用 sys_dict 的子模块） ---------------- */

const dictCache = {}

/**
 * 读取字典项并缓存（优先走后端的字典缓存工具，失败退化为空数组，不阻塞页面）。
 * @returns {Promise<Array<{text:string,label:string,value:string}>>}
 */
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

/**
 * 字典值 → 文本（表格列展示用；字典没加载出来时原样返回，不会出现空白列）
 */
export function dictText (dictCode, value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const items = dictCache[dictCode] || []
  const hit = items.filter(item => String(item.value) === String(value))[0]
  return hit ? (hit.text || hit.label || value) : value
}

/** 一次性把本模块 4 个字典都预热到缓存里（页面 created 时调用一次） */
export function loadAllDicts () {
  return Promise.all([DICT.projectType, DICT.argResult, DICT.materialType, DICT.yn].map(loadDictItems))
}

/* ---------------- 其它展示工具 ---------------- */

/** 字节 → 可读大小 */
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

/** 取扩展名（小写，无扩展名返回空串） */
export function resolveExt (fileName) {
  if (!fileName) {
    return ''
  }
  const dot = String(fileName).lastIndexOf('.')
  return dot < 0 ? '' : String(fileName).substring(dot + 1).toLowerCase()
}

/** 意见正文摘要（台账「最新审核意见」列） */
export function opinionSummary (text, max) {
  const limit = max || 40
  if (!text) {
    return ''
  }
  const value = String(text).replace(/\s+/g, ' ').trim()
  return value.length > limit ? `${value.substring(0, limit)}…` : value
}

export { buildDownloadUrl }

export default {
  escalationUrl,
  buildDownloadUrl,
  queryProjectList,
  queryProjectById,
  addProject,
  editProject,
  deleteProject,
  deleteProjectBatch,
  genProjectNo,
  checkProjectNo,
  exportProjectXls,
  queryMaterialList,
  saveMaterial,
  deleteMaterial,
  downloadMaterial,
  previewMaterial,
  queryRecordList,
  addRecord,
  queryLedgerList,
  queryLedgerCountByStatus,
  exportLedgerXls,
  queryStatByResult,
  queryStatByDept,
  queryStatByXzqh,
  queryStatByMonth,
  queryStatByType,
  loadDictItems,
  dictText,
  loadAllDicts,
  statusColor,
  argResultColor,
  actionColor,
  formatSize,
  resolveExt,
  opinionSummary
}
