import {
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  buildJavaDownloadUrl
} from '@/api/manageJava'

/**
 * 档案管理 · 道路交付及养护协议移交事项 接口（方案 2.3.2 第 6 项）
 *
 * 后端：org.jeecg.modules.land.archive.handover.controller.RoadHandoverController（16 个接口）
 *
 * 【重要】这些接口在 **Java 业务后端**（window._CONFIG.VUE_DATA_JAVA_URL，
 * 例如 http://127.0.0.1:9802/api），不在中台（VUE_APP_API_BASE_URL，4548）上，
 * 所以统一用 `@/api/manageJava` 的请求方法，**不要**改成 `@/api/manage`。
 * 与 admin-client/src/api/land/handover.js 一一对应（共用同一套后端），
 * 两边如有一侧改动接口，请同步另一侧，避免契约漂移。
 *
 * 【与「道路验收及移交资料台账」（第 7 项）的分工】
 *   台账 = 1340 条道路的**普查**（一条一行，管 13 类资料齐不齐）；
 *   本模块 = 只登记**真正发生/在办的移交事项**（管协议签了没、养护期从哪天到哪天、谁接收管养）。
 *   两者通过 facilityId / crzdbh 互相关联，但不互相写入。
 *
 * 【三个「@RequestParam 的 POST」为什么把参数拼在 URL 上】
 *   javaPostAction(url, data) 的第二个参数是 **JSON body**，而 Spring 的
 *   `@RequestParam` 只从 query string / 表单里取值，body 里的 JSON 绑不上去。
 *   所以 status / linkArchive / unlinkArchive 这三个写操作把参数拼在 query 上，
 *   与 admin-client 的写法保持一致（DELETE 的 javaDeleteAction 本身就是拼 query 的，无需特殊处理）。
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

/* ============================ 查询 ============================ */

/**
 * 移交事项分页列表。
 * 条件见清单 §6.3.6（道路名称 / 行政区划 / 出让宗地编号 / 关联配套项目 / 移交类型 /
 * 状态 / 协议签订日期区间 / 移交日期区间 等），与统计、导出共用同一套条件。
 * @param {object} params 查询条件 + pageNo + pageSize
 */
export function queryHandoverList (params) {
  return javaGetAction(handoverUrl.list, params)
}

/** 移交事项详情（含 relatedArchives 关联档案列表） */
export function queryHandoverById (id) {
  return javaGetAction(handoverUrl.queryById, { id })
}

/** 汇总指标 + 6 组分类统计（与列表同一套查询条件） */
export function queryHandoverStat (params) {
  return javaGetAction(handoverUrl.stat, params || {})
}

/** 各状态计数（固定返回 3 项，数量为 0 也返回） */
export function queryHandoverCountByStatus (params) {
  return javaGetAction(handoverUrl.countByStatus, params || {})
}

/* ============================ 增删改 ============================ */

/** 新增移交事项（移交编号留空时后端自动生成 YJ-{yyyy}-{4位}） */
export function addHandover (data) {
  return javaPostAction(handoverUrl.add, data)
}

/** 编辑移交事项（字段可清空：后端用显式 SQL 覆盖，不受 NOT_NULL 策略限制） */
export function editHandover (data) {
  return javaPutAction(handoverUrl.edit, data)
}

/** 变更状态（待移交 / 移交中 / 已移交，无流转顺序校验） */
export function changeHandoverStatus (id, status) {
  return javaPostAction(
    `${handoverUrl.changeStatus}?id=${encodeURIComponent(id)}&status=${encodeURIComponent(status)}`
  )
}

/** 删除移交事项（逻辑删除，不影响档案与配套项目） */
export function deleteHandover (id) {
  return javaDeleteAction(handoverUrl.delete, { id })
}

/** 批量删除 */
export function deleteHandoverBatch (ids) {
  return javaDeleteAction(handoverUrl.deleteBatch, {
    ids: Array.isArray(ids) ? ids.join(',') : ids
  })
}

/* ============================ 移交编号 ============================ */

/** 预生成移交编号 YJ-{yyyy}-{4位}（仅预览不落库，允许手工改写） */
export function generateHandoverNo (year) {
  return javaGetAction(handoverUrl.generateNo, year ? { year } : {})
}

/** 移交编号唯一校验（编辑时带 id 排除自身） */
export function checkHandoverNo (params) {
  return javaGetAction(handoverUrl.checkNo, params)
}

/* ============================ 关联档案（只读 t_archive） ============================ */

/** 某条移交事项对应的全部档案（按配套项目ID优先、出让宗地编号兜底反查） */
export function queryHandoverArchives (id, limit) {
  return javaGetAction(handoverUrl.relatedArchives, limit ? { id, limit } : { id })
}

/**
 * 挑档案（只读查询）
 * @param {{facilityId?: string, crzdbh?: string, keyword?: string, limit?: number}} params
 */
export function pickHandoverArchives (params) {
  return javaGetAction(handoverUrl.archivePick, params || {})
}

/** 把某个档案设为该事项的关联档案（只写本表的 archive_id 指针，不改档案数据） */
export function linkHandoverArchive (id, archiveId) {
  return javaPostAction(
    `${handoverUrl.linkArchive}?id=${encodeURIComponent(id)}&archiveId=${encodeURIComponent(archiveId)}`
  )
}

/** 取消关联（只清本表指针，不删除档案本身） */
export function unlinkHandoverArchive (id) {
  return javaPostAction(`${handoverUrl.unlinkArchive}?id=${encodeURIComponent(id)}`)
}

/* ============================ 导出 ============================ */

/**
 * 按当前查询条件导出 Excel（浏览器原生下载）。
 * 用 window.open 而不是 axios：需要浏览器的原生下载行为，
 * 且 buildJavaDownloadUrl 会把令牌拼在 query 上（JwtFilter 支持从 query 取 token）。
 * @param {object} params 与列表同一套查询条件
 */
export function exportHandoverXls (params) {
  window.open(buildJavaDownloadUrl(handoverUrl.exportXls, params), '_blank')
}

export { buildJavaDownloadUrl }

export default {
  handoverUrl,
  buildJavaDownloadUrl,
  queryHandoverList,
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
  exportHandoverXls
}
