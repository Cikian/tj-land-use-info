import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction, uploadAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { getDictItems } from '@/components/dict/JDictSelectUtil'

/**
 * 浏览器原生下载（文件流）不能带自定义请求头，而 jeecg 的 JwtFilter 同时支持
 * 从 query string 的 token 参数取令牌，所以这里统一拼上 token。
 * （与 api/land/archive.js、api/land/escalation.js 的 buildDownloadUrl 完全一致）
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
 * 道路设施验收及移交资料台账接口（方案 2.3.2 第 7 项）
 *
 * 后端：org.jeecg.modules.land.archive.ledger.controller.RoadAcceptanceLedgerController
 * 权限码：land:ledger:list / add / edit / delete / archive / export（均为按钮权限）
 *
 * 关键约定：
 *  1. ★ 13 类资料的勾选值以 0/1 整型提交（与库里 tinyint(1) 一一对应）；
 *  2. 台账编号默认由后端生成（YS-{yyyy}-{4位}），允许用户手工改写；
 *  3. 「缺少某类资料」筛选传的是资料的驼峰属性名（如 hasJgwj），后端会白名单校验；
 *  4. 资料原件不在这里上传 —— 统一走档案管理模块，台账只通过 linkArchive 记指针。
 */
export const ledgerUrl = {
  list: '/land/archive/ledger/list',
  queryById: '/land/archive/ledger/queryById',
  stat: '/land/archive/ledger/stat',
  countByStatus: '/land/archive/ledger/countByStatus',
  materials: '/land/archive/ledger/materials',
  add: '/land/archive/ledger/add',
  edit: '/land/archive/ledger/edit',
  changeStatus: '/land/archive/ledger/status',
  delete: '/land/archive/ledger/delete',
  deleteBatch: '/land/archive/ledger/deleteBatch',
  generateNo: '/land/archive/ledger/generateNo',
  checkNo: '/land/archive/ledger/checkNo',
  relatedArchives: '/land/archive/ledger/relatedArchives',
  archivePick: '/land/archive/ledger/archive/pick',
  linkArchive: '/land/archive/ledger/linkArchive',
  unlinkArchive: '/land/archive/ledger/unlinkArchive',
  exportXls: '/land/archive/ledger/exportXls',
  // ---- Excel 批量补录（模块 C）----
  importTemplate: '/land/archive/ledger/import/template',
  importPreview: '/land/archive/ledger/import/preview',
  importConfirm: '/land/archive/ledger/import/confirm'
}

/* ==========================================================================
 * 一、接口封装（17 个）
 * ========================================================================== */

/** 台账分页列表（台账页 / 查询共用同一套条件） */
export function queryLedgerPage (params) {
  return getAction(ledgerUrl.list, params)
}

/** 台账详情（含 13 类资料统计 + 关联档案列表） */
export function queryLedgerById (id) {
  return getAction(ledgerUrl.queryById, { id })
}

/** 汇总与 8 组分类统计 */
export function queryLedgerStat (params) {
  return getAction(ledgerUrl.stat, params || {})
}

/** 各状态计数（顶部 tab 角标，固定 4 项） */
export function queryLedgerCountByStatus (params) {
  return getAction(ledgerUrl.countByStatus, params || {})
}

/** ★13 类资料定义（表头来源，与后端 LedgerMaterial 同源；失败时用本地兜底） */
export function queryLedgerMaterials () {
  return getAction(ledgerUrl.materials, {})
}

/** 新增台账 */
export function addLedger (data) {
  return postAction(ledgerUrl.add, data)
}

/** 编辑台账 */
export function editLedger (data) {
  return putAction(ledgerUrl.edit, data)
}

/** 变更状态 */
export function changeLedgerStatus (id, status) {
  return postAction(`${ledgerUrl.changeStatus}?id=${encodeURIComponent(id)}&status=${encodeURIComponent(status)}`)
}

/** 删除台账 */
export function deleteLedger (id) {
  return deleteAction(ledgerUrl.delete, { id })
}

/** 批量删除台账 */
export function deleteLedgerBatch (ids) {
  return deleteAction(ledgerUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 预生成台账编号 YS-{yyyy}-{4位}（仅预览，允许手工改写） */
export function generateLedgerNo (year) {
  return getAction(ledgerUrl.generateNo, year ? { year } : {})
}

/** 台账编号唯一校验（编辑时带 id 排除自身） */
export function checkLedgerNo (params) {
  return getAction(ledgerUrl.checkNo, params)
}

/** 某条台账的关联档案（配套项目ID 优先、宗地编号兜底） */
export function queryRelatedArchives (id, limit) {
  return getAction(ledgerUrl.relatedArchives, limit ? { id, limit } : { id })
}

/** 挑档案（关联时用；可按配套项目/宗地过滤或全局搜） */
export function pickArchives (params) {
  return getAction(ledgerUrl.archivePick, params || {})
}

/** 关联档案 */
export function linkLedgerArchive (id, archiveId) {
  return postAction(`${ledgerUrl.linkArchive}?id=${encodeURIComponent(id)}&archiveId=${encodeURIComponent(archiveId)}`)
}

/** 取消关联档案 */
export function unlinkLedgerArchive (id) {
  return postAction(`${ledgerUrl.unlinkArchive}?id=${encodeURIComponent(id)}`)
}

/** 按当前查询条件导出台账 Excel（浏览器直接下载） */
export function exportLedgerXls (params) {
  window.open(buildDownloadUrl(ledgerUrl.exportXls, params), '_blank')
}

/* ---------------- Excel 批量补录（模块 C） ---------------- */

/**
 * 下载批量补录模板（浏览器直接下载）。
 *
 * 模板两个 sheet：补录表 + 填表说明；空单元格表示「不修改」而不是清空。
 */
export function downloadLedgerTemplate () {
  window.open(buildDownloadUrl(ledgerUrl.importTemplate, {}), '_blank')
}

/**
 * 批量补录 - 预览校验（★ 不写库）。
 *
 * @param {File} file .xlsx 文件
 * @returns {Promise} result 里含 totalRows / matchedRows / errors / warnings
 */
export function previewLedgerImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadAction(ledgerUrl.importPreview, formData)
}

/**
 * 批量补录 - 确认入库。
 *
 * @param {File} file .xlsx 文件
 * @param {boolean} skipErrorRows true = 跳过有错误的行只导入正确的行；
 *                                false（默认）= 有错就整批不入库
 */
export function confirmLedgerImport (file, skipErrorRows) {
  const formData = new FormData()
  formData.append('file', file)
  if (skipErrorRows) {
    formData.append('skipErrorRows', 'true')
  }
  return uploadAction(ledgerUrl.importConfirm, formData)
}

/* ==========================================================================
 * 二、模块内共享常量（状态 / 13 类资料 / 字典 / 配色）
 *
 * 这些常量在 4 个页面与组件里都要用，集中放在本模块唯一的 api 文件里，
 * 避免各页面各抄一份导致口径漂移（与提级论证模块的处理一致）。
 * ========================================================================== */

/** 台账状态（固定 4 值，不入字典；与后端 LedgerStatus 逐字一致） */
export const LEDGER_STATUS = ['未验收', '验收中', '已验收', '已移交']

/** 功能区取值（旧数据 xzqh 里混入的非行政区值，与迁移脚本的拆分口径一致） */
export const GNQ_OPTIONS = ['生态城', '经开区', '高新区', '保税区', '土地发展中心']

/** 配套设施类别（旧库 ptsslb 里与道路相关的取值） */
export const PTSSLB_OPTIONS = ['道路', '市政道路', '道路及管线']

/** 字典 code */
export const DICT = {
  acceptanceType: 'land_road_acceptance_type',
  acceptanceResult: 'land_road_acceptance_result'
}

/**
 * ★ 13 类资料定义（本地兜底）。
 *
 * 页面挂载时会调 {@link queryLedgerMaterials} 用后端返回的定义覆盖这里的 label，
 * 因此「后端加一类资料」时页面表头会自动跟上；本列表只保证接口不可用时页面仍能渲染。
 *
 * key     —— 提交给后端的驼峰属性名（0/1）
 * column  —— 数据库列名（仅用于排查与文档对照，前端不拼 SQL）
 * label   —— 中文名
 * source  —— 迁移来源说明（鼠标悬停提示）
 */
export const LEDGER_MATERIALS_FALLBACK = [
  { seq: 1, key: 'hasSgxk', column: 'has_sgxk', label: '施工许可证', source: '旧配套表 sgxk=是（旧附件目录 11-施工许可）' },
  { seq: 2, key: 'hasYsbg', column: 'has_ysbg', label: '竣工验收报告', source: '旧配套表 sfjg=是 推断（已竣工 ⇒ 必有竣工验收报告）' },
  { seq: 3, key: 'hasJgtc', column: 'has_jgtc', label: '竣工图测', source: '旧配套表 sjjgsj 非空推断（有实际竣工日期 ⇒ 应有竣工图测）' },
  { seq: 4, key: 'hasZljdbg', column: 'has_zljdbg', label: '质量监督报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 5, key: 'hasCljybg', column: 'has_cljybg', label: '材料检验报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 6, key: 'hasAjbg', column: 'has_ajbg', label: '安全监督报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 7, key: 'hasGhyshgz', column: 'has_ghyshgz', label: '规划验收合格证', source: '旧流程「建设工程规划验收合格证核发」产物，待补录' },
  { seq: 8, key: 'hasJgbaba', column: 'has_jgbaba', label: '竣工验收备案表', source: '旧流程「建设工程竣工验收备案」产物，待补录' },
  { seq: 9, key: 'hasDazxys', column: 'has_dazxys', label: '档案专项验收意见', source: '旧流程「建设项目档案专项验收」产物，待补录' },
  { seq: 10, key: 'hasDlyjd', column: 'has_dlyjd', label: '道路工程移交单', source: '旧流程「道路工程移交」产物，待补录' },
  { seq: 11, key: 'hasYhxy', column: 'has_yhxy', label: '养护协议', source: '方案 2.3.2 第 6 项（道路交付及养护协议移交）的产物，待补录' },
  { seq: 12, key: 'hasJgwj', column: 'has_jgwj', label: '竣工文件', source: '旧配套表 jgwj=是（旧附件目录 13-竣工文件）' },
  { seq: 13, key: 'hasYjwj', column: 'has_yjwj', label: '移交文件', source: '旧配套表 yjwj=是（旧附件目录 14-移交文件）' }
]

/** 13 类资料总数（「资料 3/13」的分母） */
export const MATERIAL_TOTAL = LEDGER_MATERIALS_FALLBACK.length

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
  return Promise.all([loadDictItems(DICT.acceptanceType), loadDictItems(DICT.acceptanceResult)])
}

/* ---------------- 配色（★ 只用 antd 预设色，无强调色返回 undefined） ---------------- */

/**
 * 状态 → a-tag 颜色。
 * ★ 绝不能返回 'default'：它不是 antd 预设色，会被当成自定义色 →
 *   白字 + 非法背景色被丢弃 → 白底白字看不见（见《档案管理-实现说明》7.5）。
 */
export function statusColor (status) {
  if (status === '已移交') {
    return 'green'
  }
  if (status === '已验收') {
    return 'blue'
  }
  if (status === '验收中') {
    return 'orange'
  }
  // 未验收 / 未知 → undefined（默认灰底）
  return undefined
}

/** 状态色板（统计图与左侧小圆点复用） */
export function statusDotColor (status) {
  if (status === '已移交') {
    return '#10b981'
  }
  if (status === '已验收') {
    return '#2e7cf6'
  }
  if (status === '验收中') {
    return '#f59e0b'
  }
  return '#94a3b8'
}

/** 验收结果 → a-tag 颜色（合格=绿、整改后合格=橙、不合格=红） */
export function acceptanceResultColor (result) {
  if (result === '合格') {
    return 'green'
  }
  if (result === '整改后合格') {
    return 'orange'
  }
  if (result === '不合格') {
    return 'red'
  }
  return undefined
}

/* ---------------- 展示小工具 ---------------- */

/** 资料归集摘要：「3/13」 */
export function materialSummary (record) {
  if (!record) {
    return `0/${MATERIAL_TOTAL}`
  }
  const count = record.materialCount === null || record.materialCount === undefined
    ? countMaterials(record)
    : Number(record.materialCount)
  return `${count}/${MATERIAL_TOTAL}`
}

/** 按 13 个勾选列数一遍（后端没给 materialCount 时的兜底） */
export function countMaterials (record) {
  if (!record) {
    return 0
  }
  return LEDGER_MATERIALS_FALLBACK.reduce((sum, item) => sum + (Number(record[item.key]) === 1 ? 1 : 0), 0)
}

/** 该台账缺失的资料名列表（详情页「待补资料」提示用） */
export function missingMaterialLabels (record) {
  if (!record) {
    return []
  }
  return LEDGER_MATERIALS_FALLBACK
    .filter(item => Number(record[item.key]) !== 1)
    .map(item => item.label)
}

/**
 * ★「移交类」资料的键（判断状态与资料是否自相矛盾时用）。
 *
 * 这三类资料是「已移交」状态的直接凭证：状态置成已移交，却没有移交单/养护协议/移交文件，
 * 台账就自相矛盾了。台账是给中心看进度的，这类矛盾必须显式提示而不是静默放过。
 */
export const HANDOVER_MATERIAL_KEYS = ['hasDlyjd', 'hasYhxy', 'hasYjwj']

/**
 * 资料与状态的一致性检查（返回提示语，无问题时返回空串）。
 *
 * <p>★ 只做「提示」，不做硬校验：台账的验收与移交都是线下办完才回系统登记，
 * 资料扫描件往往滞后于状态（现实里常见「先移交、后补资料」）。
 * 因此这里不能在保存时阻断，只在列表与详情上给一个黄色提示，
 * 让中心一眼看出哪些记录还需要补资料。
 *
 * 三条规则：
 *  1. 状态「已移交」但移交类资料（移交单/养护协议/移交文件）未归集齐 → 提示缺哪些；
 *  2. 状态「已验收」但没有竣工验收报告 → 提示；
 *  3. 反过来：已归集「移交文件」但状态还不是「已移交」→ 提示状态可能漏改。
 *
 * @param {object} record 台账行
 * @param {Array} [materials] 资料定义（默认用本地兜底列表；页面可传后端返回的定义）
 * @returns {string} 提示语，空串表示一致
 */
export function statusMaterialWarning (record, materials) {
  if (!record) {
    return ''
  }
  const defs = (materials && materials.length) ? materials : LEDGER_MATERIALS_FALLBACK
  const labelOf = key => {
    const hit = defs.filter(item => item.key === key)[0]
    return hit ? hit.label : key
  }
  const status = record.status
  if (status === '已移交') {
    const missing = HANDOVER_MATERIAL_KEYS.filter(key => Number(record[key]) !== 1)
    if (missing.length) {
      return `状态为「已移交」，但「${missing.map(labelOf).join('、')}」尚未归集`
    }
  }
  if (status === '已验收' && Number(record.hasYsbg) !== 1) {
    return '状态为「已验收」，但「竣工验收报告」尚未归集'
  }
  if (status !== '已移交' && Number(record.hasYjwj) === 1) {
    return `已归集「${labelOf('hasYjwj')}」，但状态仍是「${status || '未验收'}」，请确认是否漏改状态`
  }
  return ''
}

/**
 * 台账 → 档案查询页的下钻链接（双向跳转的「台账 → 档案」方向）。
 *
 * 档案查询页（ArchiveQuery）会读 $route.query 作为初始条件，因此这里带上档案号即可。
 */
export function archiveQueryUrl (record) {
  const params = []
  if (record && record.archiveNo) {
    params.push(`archiveNo=${encodeURIComponent(record.archiveNo)}`)
  } else if (record && record.ptxmmc) {
    params.push(`ptxmmc=${encodeURIComponent(record.ptxmmc)}`)
  }
  return `/land/archive/query${params.length ? '?' + params.join('&') : ''}`
}

/**
 * 台账页的下钻链接（双向跳转的「档案/配套项目 → 台账」方向）。
 *
 * @param {{facilityId?:string, crzdbh?:string, archiveId?:string, status?:string}} params
 */
export function ledgerPageUrl (params) {
  const query = []
  Object.keys(params || {}).forEach(key => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  })
  return `/land/archive/ledger${query.length ? '?' + query.join('&') : ''}`
}

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

export { buildDownloadUrl }

export default {
  ledgerUrl,
  buildDownloadUrl,
  queryLedgerPage,
  queryLedgerById,
  queryLedgerStat,
  queryLedgerCountByStatus,
  queryLedgerMaterials,
  addLedger,
  editLedger,
  changeLedgerStatus,
  deleteLedger,
  deleteLedgerBatch,
  generateLedgerNo,
  checkLedgerNo,
  queryRelatedArchives,
  pickArchives,
  linkLedgerArchive,
  unlinkLedgerArchive,
  exportLedgerXls,
  downloadLedgerTemplate,
  previewLedgerImport,
  confirmLedgerImport,
  loadDictItems,
  dictText,
  loadAllDicts,
  statusColor,
  statusDotColor,
  acceptanceResultColor,
  materialSummary,
  countMaterials,
  missingMaterialLabels,
  statusMaterialWarning,
  archiveQueryUrl,
  ledgerPageUrl,
  formatSize
}
