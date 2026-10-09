import {
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  buildJavaDownloadUrl,
} from '@/api/manageJava'

/**
 * 提级论证管理 · 接口层（24 个接口）
 * ===============================================================
 * 对应方案 2.3.3（项目录入 / 查询统计 / 资料及台账管理 / 提级论证审批）
 * 与《提级论证管理-详细实施设计.md》4.2 的接口清单。
 *
 * 【本文件在做什么 / 为什么长这样】
 *   admin-client 里这一套已经端到端联调通过（code/new/admin-client/src/api/land/escalation.js），
 *   本文件是它在大屏工程（stargis-client-web）里的对应物，**接口地址与入参口径一一相同**，
 *   只有两点差异，都是本工程的既有约定：
 *
 *   1. 请求方法换通道：这些接口（/land/**、/sys/common/**）在 **Java 业务后端**
 *      （window._CONFIG.VUE_DATA_JAVA_URL，例如 http://127.0.0.1:9802/api），
 *      不在中台（VUE_APP_API_BASE_URL）。admin-client 的 domianURL 恰好就是 Java 后端，
 *      所以它用 @/api/manage 的 getAction 就能通；本工程的 domianURL 指向中台，
 *      必须改用 `@/api/manageJava` 的 java*Action（详见该文件顶部的说明）。
 *   2. 下载地址构造改用 buildJavaDownloadUrl（令牌拼在 query 上、基址取 Java 后端），
 *      行为与 admin-client 的 buildDownloadUrl 完全一致。
 *
 * 【返回结构】统一 `{ success, code, message, result }`；
 *   列表类 result = `{ records, total }`；统计类 result = `[{ name, count }]`；
 *   台账状态计数 result = `[{ status, count }]`（注意键名是 status 而不是 name，见 4.2）。
 *
 * 【导出与下载】一律走 buildJavaDownloadUrl + window.open：
 *   原生下载不带自定义请求头，而 Java 端 JwtFilter 支持从 query string 的 token 取值。
 *
 * ★ 与 admin-client 共用同一套后端，改动接口时必须同步两个仓库，
 *   否则会出现「一边能存、另一边筛不出来」的契约漂移。
 */

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
  // ---- 意见记录（2，append-only：没有编辑/删除接口）----
  recordList: '/land/escalation/record/list',
  recordAdd: '/land/escalation/record/add',
  // ---- 台账（3）----
  ledgerList: '/land/escalation/ledger/list',
  ledgerCountByStatus: '/land/escalation/ledger/countByStatus',
  ledgerExportXls: '/land/escalation/ledger/exportXls',
  // ---- 统计（5），返回 NameCount 数组 ----
  statByResult: '/land/escalation/stat/byResult',
  statByDept: '/land/escalation/stat/byDept',
  statByXzqh: '/land/escalation/stat/byXzqh',
  statByMonth: '/land/escalation/stat/byMonth',
  statByType: '/land/escalation/stat/byType',
}

/* ==========================================================================
 * 二、24 个接口封装
 * ========================================================================== */

/* ---------------- 项目（9） ---------------- */

/** 项目分页列表（录入 / 查询统计 / 审批左栏共用） */
export function queryProjectList (params) {
  return javaGetAction(escalationUrl.projectList, params)
}

/** 项目详情（含材料列表 materials、意见记录 records 时直接取用，不用再调子接口） */
export function queryProjectById (id) {
  return javaGetAction(escalationUrl.projectQueryById, { id })
}

/** 新增项目（body 带 materials） */
export function addProject (data) {
  return javaPostAction(escalationUrl.projectAdd, data)
}

/** 编辑项目（body 带 materials，服务端按 id 做增/改/删三向合并） */
export function editProject (data) {
  return javaPutAction(escalationUrl.projectEdit, data)
}

/** 删除项目 */
export function deleteProject (id) {
  return javaDeleteAction(escalationUrl.projectDelete, { id })
}

/** 批量删除项目（后端约定 ids 为逗号分隔字符串，不是数组） */
export function deleteProjectBatch (ids) {
  return javaDeleteAction(escalationUrl.projectDeleteBatch, {
    ids: Array.isArray(ids) ? ids.join(',') : ids,
  })
}

/** 预生成项目编号 TJ-{yyyy}-{4位}（仅预览、不落库，允许用户手工改写） */
export function genProjectNo (year) {
  return javaGetAction(escalationUrl.projectGenNo, year ? { year } : {})
}

/** 项目编号唯一校验（编辑时带 id 排除自身）；result === false 表示已存在 */
export function checkProjectNo (params) {
  return javaGetAction(escalationUrl.projectCheckNo, params)
}

/** 按查询条件导出项目 Excel（浏览器原生下载） */
export function exportProjectXls (params) {
  window.open(buildJavaDownloadUrl(escalationUrl.projectExportXls, params), '_blank')
}

/* ---------------- 材料（5） ---------------- */

/** 某项目的材料列表（审批页 / 详情页的只读材料表） */
export function queryMaterialList (projectId) {
  return javaGetAction(escalationUrl.materialList, { projectId })
}

/**
 * 保存材料。
 * 项目已落库时直接挂到 projectId 下（审批页上传即存库）；
 * 录入向导里材料是随主表一起提交的，不走这个接口。
 */
export function saveMaterial (data) {
  return javaPostAction(escalationUrl.materialSave, data)
}

/** 删除材料 */
export function deleteMaterial (id) {
  return javaDeleteAction(escalationUrl.materialDelete, { id })
}

/** 下载材料（服务端会校验文件是否还在磁盘上） */
export function downloadMaterial (id) {
  window.open(buildJavaDownloadUrl(escalationUrl.materialDownload, { id }), '_blank')
}

/** 在线预览材料（服务端在 previewPath 为空时回源 storePath） */
export function previewMaterial (id) {
  window.open(buildJavaDownloadUrl(escalationUrl.materialPreview, { id }), '_blank')
}

/* ---------------- 意见记录（2，append-only） ---------------- */

/** 某项目的意见记录（服务端按时间倒序返回） */
export function queryRecordList (projectId) {
  return javaGetAction(escalationUrl.recordList, { projectId })
}

/**
 * 登记审核意见。
 * @param {{projectId:string, recordType?:string, action?:string, opinion:string,
 *          attachmentIds?:string, status?:string}} data
 *        ★ status 只在「同时更新办理状态」被选中时才传：
 *          不传 = 不改办理状态（这是默认行为，见设计文档 2.3「不做意见驱动的状态流转」）。
 */
export function addRecord (data) {
  return javaPostAction(escalationUrl.recordAdd, data)
}

/* ---------------- 台账（3） ---------------- */

/** 台账分页列表（14 列口径，字段见 EscalationLedgerVO） */
export function queryLedgerList (params) {
  return javaGetAction(escalationUrl.ledgerList, params)
}

/**
 * 台账各办理状态计数（顶部状态页签的角标）。
 * ★ 返回 `[{ status, count }]` —— 键名是 **status**，
 *   与统计接口的 NameCount（name/count）不同，normalize 时要分开处理。
 */
export function queryLedgerCountByStatus (params) {
  return javaGetAction(escalationUrl.ledgerCountByStatus, params || {})
}

/** 按当前条件导出台账 Excel（14 列，与列表同一套条件） */
export function exportLedgerXls (params) {
  window.open(buildJavaDownloadUrl(escalationUrl.ledgerExportXls, params), '_blank')
}

/* ---------------- 统计（5），返回 NameCount 数组 ---------------- */

/** 按论证结果分布（环形图） */
export function queryStatByResult (params) {
  return javaGetAction(escalationUrl.statByResult, params)
}

/** 按申报单位排行（排行条） */
export function queryStatByDept (params) {
  return javaGetAction(escalationUrl.statByDept, params)
}

/** 按行政区划分布（标签条形列表） */
export function queryStatByXzqh (params) {
  return javaGetAction(escalationUrl.statByXzqh, params)
}

/** 按申报时间趋势（折线图，name 为 yyyy-MM） */
export function queryStatByMonth (params) {
  return javaGetAction(escalationUrl.statByMonth, params)
}

/** 按项目类型分布（查询统计页作为补充信息展示） */
export function queryStatByType (params) {
  return javaGetAction(escalationUrl.statByType, params)
}

export { buildJavaDownloadUrl }

export default {
  escalationUrl,
  buildJavaDownloadUrl,
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
}
