import {
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  buildJavaDownloadUrl
} from '@/api/manageJava'

/**
 * 档案管理 · 道路设施验收及移交资料台账 接口（方案 2.3.2 第 7 项）
 * ===============================================================
 * 后端：org.jeecg.modules.land.archive.ledger.controller.RoadAcceptanceLedgerController
 * 权限码：land:ledger:list / add / edit / delete / archive / export / import
 *
 * 【为什么用 manageJava 而不是 @/api/manage】
 * 这些接口在 **Java 业务后端**（window._CONFIG.VUE_DATA_JAVA_URL，例如
 * http://127.0.0.1:9802/api），不在中台（VUE_APP_API_BASE_URL）上。
 * `@/api/manage` 走的是中台的 domianURL，请求会 404；而且它会给每个请求塞
 * `parameter.access_token`，触发 request.js 多打一次中台探针接口。
 * 详见 src/api/manageJava.js 的文件头注释。
 *
 * 【与 admin-client 的关系】
 * 本文件与 admin-client/src/api/land/ledger.js **一一对应，共用同一套后端**：
 * 接口路径、参数名、取值口径必须保持同步，否则会出现「一边能存、另一边筛不出来」
 * 的契约漂移。差异只有两处，都是大屏端的环境要求：
 *   1. 请求方法换成 manageJava 的 javaXxxAction（见上）；
 *   2. 模块内的枚举 / 展示辅助挪到 views/screen/archive/modules/ledger/constants.js
 *      （与档案模块「api 只放接口、常量收敛到 constants.js」的分工一致）。
 *
 * 关键约定：
 *  1. ★ 13 类资料的勾选值以 0/1 整型提交（与库里 tinyint(1) 一一对应）；
 *  2. 台账编号默认由后端生成（YS-{yyyy}-{4位}），允许用户手工改写；
 *  3. 「缺少某类资料」筛选传的是资料的驼峰属性名（如 hasJgwj），后端白名单校验；
 *  4. 资料原件不在台账上传 —— 统一走档案管理模块，台账只通过 linkArchive 记指针。
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
  // ---- Excel 批量补录 ----
  importTemplate: '/land/archive/ledger/import/template',
  importPreview: '/land/archive/ledger/import/preview',
  importConfirm: '/land/archive/ledger/import/confirm'
}

/* ==========================================================================
 * 一、查询（5 个）
 * ========================================================================== */

/** 台账分页列表（列表 / 统计 / 导出共用同一套条件） */
export function queryLedgerPage (params) {
  return javaGetAction(ledgerUrl.list, params)
}

/** 台账详情（含 13 类资料统计 + 关联档案列表） */
export function queryLedgerById (id) {
  return javaGetAction(ledgerUrl.queryById, { id })
}

/** 汇总与 8 组分类统计（指标卡 + 13 类资料归集分布都从这里取） */
export function queryLedgerStat (params) {
  return javaGetAction(ledgerUrl.stat, params || {})
}

/** 各状态计数（状态页签角标，固定返回 4 项） */
export function queryLedgerCountByStatus (params) {
  return javaGetAction(ledgerUrl.countByStatus, params || {})
}

/**
 * ★13 类资料定义（矩阵表头来源，与后端 LedgerMaterial 同源）。
 * 调用方拿不到时用 constants.js 的 LEDGER_MATERIALS_FALLBACK 兜底，
 * 这样「后端加一类资料」不会出现「表头 13 列、数据 14 列」的错位。
 */
export function queryLedgerMaterials () {
  return javaGetAction(ledgerUrl.materials, {})
}

/* ==========================================================================
 * 二、增删改与编号（7 个）
 * ========================================================================== */

/** 新增台账 */
export function addLedger (data) {
  return javaPostAction(ledgerUrl.add, data)
}

/** 编辑台账（后端用显式 SQL 覆盖，允许把字段清空） */
export function editLedger (data) {
  return javaPutAction(ledgerUrl.edit, data)
}

/**
 * 变更状态（未验收 / 验收中 / 已验收 / 已移交）。
 *
 * 后端的 /status、/linkArchive、/unlinkArchive 都是 `@RequestParam`（query string），
 * 不是 JSON body，而 javaPostAction 的第二个参数只会进 body，
 * 所以这里把参数拼在 URL 上（与 admin-client 的写法一致）。
 */
export function changeLedgerStatus (id, status) {
  const query = `?id=${encodeURIComponent(id)}&status=${encodeURIComponent(status)}`
  return javaPostAction(ledgerUrl.changeStatus + query, {})
}

/** 删除台账（逻辑删除） */
export function deleteLedger (id) {
  return javaDeleteAction(ledgerUrl.delete, { id })
}

/** 批量删除台账 */
export function deleteLedgerBatch (ids) {
  return javaDeleteAction(ledgerUrl.deleteBatch, {
    ids: Array.isArray(ids) ? ids.join(',') : ids
  })
}

/** 预生成台账编号 YS-{yyyy}-{4位}（仅预览，允许手工改写） */
export function generateLedgerNo (year) {
  return javaGetAction(ledgerUrl.generateNo, year ? { year } : {})
}

/** 台账编号唯一校验（编辑时带 id 排除自身） */
export function checkLedgerNo (params) {
  return javaGetAction(ledgerUrl.checkNo, params)
}

/* ==========================================================================
 * 三、关联档案（4 个，只读档案表 / 只写台账自己的 archive_id）
 * ========================================================================== */

/** 某条台账的关联档案（配套项目ID 优先、出让宗地编号兜底） */
export function queryRelatedArchives (id, limit) {
  return javaGetAction(ledgerUrl.relatedArchives, limit ? { id, limit } : { id })
}

/** 挑档案（关联时用；可按配套项目 / 宗地过滤，或按关键词全局搜） */
export function pickArchives (params) {
  return javaGetAction(ledgerUrl.archivePick, params || {})
}

/** 关联档案（写台账的 archive_id + archive_count，不修改档案表） */
export function linkLedgerArchive (id, archiveId) {
  const query = `?id=${encodeURIComponent(id)}&archiveId=${encodeURIComponent(archiveId)}`
  return javaPostAction(ledgerUrl.linkArchive + query, {})
}

/** 取消关联档案（只清台账的指针，不删除档案本身） */
export function unlinkLedgerArchive (id) {
  return javaPostAction(`${ledgerUrl.unlinkArchive}?id=${encodeURIComponent(id)}`, {})
}

/* ==========================================================================
 * 四、导出与批量补录（4 个）
 * ========================================================================== */

/**
 * 按当前查询条件导出台账 Excel。
 * 用 window.open 走浏览器原生下载：文件较大、需要原生下载行为，
 * 而原生下载带不了自定义请求头 —— buildJavaDownloadUrl 会把 jeecg 令牌
 * 拼在 query string 上（Java 端 JwtFilter 支持从 token 参数取令牌）。
 */
export function exportLedgerXls (params) {
  window.open(buildJavaDownloadUrl(ledgerUrl.exportXls, params), '_blank')
}

/** 下载批量补录模板（含「填表说明」sheet，空单元格 = 不修改） */
export function downloadLedgerTemplate () {
  window.open(buildJavaDownloadUrl(ledgerUrl.importTemplate, {}), '_blank')
}

/**
 * 批量补录 - 预览校验（★ 不写库）。
 *
 * 【为什么可以走 javaPostAction 发 multipart】
 * manageJava 的 javaPostAction 只设 X-Access-Token / X-Sign / X-TIMESTAMP 三个头，
 * **不设 Content-Type**（vue 的 axios 实例也没有默认 Content-Type），所以传 FormData 时
 * axios 会自己带上 `multipart/form-data; boundary=...`；
 * request.js 的请求拦截器只在 FormData 含 `access_token` 字段时才动它（本请求没有），
 * signMd5Utils.getSign 对 FormData 取不到可枚举键、退化为空对象签名，也不会抛错。
 * 由此不必为了一个上传接口破坏「业务请求一律走 manageJava」的约定。
 *
 * @param {File} file .xlsx 文件
 * @returns {Promise} result 为 LedgerImportResultVO（totalRows / matchedRows / errors / warnings…）
 */
export function previewLedgerImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return javaPostAction(ledgerUrl.importPreview, formData)
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
  return javaPostAction(ledgerUrl.importConfirm, formData)
}

export { buildJavaDownloadUrl }

export default {
  ledgerUrl,
  buildJavaDownloadUrl,
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
  confirmLedgerImport
}
