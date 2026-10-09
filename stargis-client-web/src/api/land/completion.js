import {
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  buildJavaDownloadUrl
} from '@/api/manageJava'

/**
 * 档案管理 · 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
 *
 * 后端：org.jeecg.modules.land.archive.completion.controller.CompletionArchiveController
 * 权限码：land:completion:list / add / edit / delete / export
 *
 * 【重要】这些接口在 **Java 业务后端**（window._CONFIG.VUE_DATA_JAVA_URL，例如
 * http://127.0.0.1:9802/api），不在中台（VUE_APP_API_BASE_URL，4548）上。
 * 所以统一用 `@/api/manageJava` 的请求方法，**不要**换成 `@/api/manage`
 * （它会额外往中台发一次场景刷新请求，见 components/screen/README.md 末节）。
 *
 * 与 admin-client 的 src/api/land/completion.js 共用同一套后端、一一对应，
 * 两边改接口必须同步，否则会出现「一边能存、另一边筛不出来」的契约漂移。
 * 差异只有两处（都是本工程的既定约定，不是漏写）：
 *   1. 请求层走 manageJava（jeecg 令牌在 X-Access-Token 头里）；
 *   2. 字典不走前端 getDictItems（大屏不依赖中台字典接口），
 *      两个字典的取值写死在 modules/completion/constants.js 并注明对应关系。
 *
 * 关键约定：
 *  1. 数字化状态（未数字化/数字化中/已数字化）是**代码枚举**，不走字典；
 *     项目类型与保管期限走**字典**（措辞可能被中心调整）；
 *  2. 档案编号默认由后端生成（JG-{yyyy}-{4位}），允许用户手工改写；
 *  3. 扫描件不在本模块上传 —— 统一走档案管理模块，本模块只通过 linkArchive 记一个指针；
 *  4. 存量历史项目不一定有对应宗地/配套项目，landId / facilityId / crzdbh 都允许为空，
 *     因此关联扫描件的查找是「配套项目ID 优先、出让宗地编号兜底」。
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
 * 一、接口封装（与后端 16 个接口一一对应）
 * ========================================================================== */

/** 档案分页列表（与统计、导出共用同一套查询条件；每条带 seq 与 digitizePercent） */
export function queryCompletionList (params) {
  return javaGetAction(completionUrl.list, params)
}

/** 档案详情（含数字化进度 + 关联扫描件列表 relatedArchives） */
export function queryCompletionById (id) {
  return javaGetAction(completionUrl.queryById, { id })
}

/** 汇总与 6 组分类统计（总数/已挂扫描件/总页数/总文件数/投资额 + 状态·区划·类型·期限·档案年度·竣工年度） */
export function queryCompletionStat (params) {
  return javaGetAction(completionUrl.stat, params || {})
}

/** 各数字化状态计数（固定 3 项，数量为 0 也返回 —— KPI 卡与状态下拉都靠它拿到确定的 0） */
export function queryCompletionCountByStatus (params) {
  return javaGetAction(completionUrl.countByStatus, params || {})
}

/** 新增历史档案 */
export function addCompletion (data) {
  return javaPostAction(completionUrl.add, data)
}

/** 编辑历史档案（字段允许清空：后端走显式 SQL 覆盖，不受 MyBatis-Plus NOT_NULL 策略限制） */
export function editCompletion (data) {
  return javaPutAction(completionUrl.edit, data)
}

/**
 * ★ 本模块的三个 POST 写接口（status / linkArchive / unlinkArchive）后端用的是
 * `@RequestParam`，参数必须拼在 **query string** 上：`javaPostAction` 只接受
 * (url, body)，把参数放进 body 会得到 400「Required String parameter 'id' is not present」。
 *
 * 注意别照抄档案模块的 `changeArchiveStatus` —— 它的后端是 `@RequestBody`，
 * 两个模块在这里**故意不一致**，抄错就是 400 且报错信息不指向真正原因。
 */
function withQuery (path, params) {
  const query = Object.keys(params || {})
    .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  return query ? `${path}?${query}` : path
}

/** 变更数字化状态（未数字化 / 数字化中 / 已数字化，无流转顺序校验） */
export function changeDigitizeStatus (id, status) {
  return javaPostAction(withQuery(completionUrl.changeStatus, { id, status }), {})
}

/** 删除历史档案（逻辑删除） */
export function deleteCompletion (id) {
  return javaDeleteAction(completionUrl.delete, { id })
}

/** 批量删除 */
export function deleteCompletionBatch (ids) {
  return javaDeleteAction(completionUrl.deleteBatch, {
    ids: Array.isArray(ids) ? ids.join(',') : ids
  })
}

/** 预生成档案编号 JG-{yyyy}-{4位}（仅预览，允许手工改写） */
export function generateArchiveNo (year) {
  return javaGetAction(completionUrl.generateNo, year ? { year } : {})
}

/** 档案编号唯一校验（编辑时带 id 排除自身） */
export function checkArchiveNo (params) {
  return javaGetAction(completionUrl.checkNo, params)
}

/** 某条档案的关联扫描件（配套项目ID 优先、出让宗地编号兜底） */
export function queryRelatedArchives (id, limit) {
  return javaGetAction(completionUrl.relatedArchives, limit ? { id, limit } : { id })
}

/** 挑档案（关联扫描件时用；可按配套项目/宗地过滤，也可按关键词全局搜） */
export function pickArchives (params) {
  return javaGetAction(completionUrl.archivePick, params || {})
}

/** 关联扫描件（只写本模块的 archiveId / archiveCount，不改档案表） */
export function linkCompletionArchive (id, archiveId) {
  return javaPostAction(withQuery(completionUrl.linkArchive, { id, archiveId }), {})
}

/** 取消关联（只清本模块的指针，不删除档案本身） */
export function unlinkCompletionArchive (id) {
  return javaPostAction(withQuery(completionUrl.unlinkArchive, { id }), {})
}

/**
 * 按当前查询条件导出档案信息 Excel（需求原文：「可快速导出档案信息」）。
 * 用浏览器原生下载而不是 axios：需要原生下载行为，URL 里由 manageJava 拼上 token
 * （jeecg 的 JwtFilter 支持从 query string 取令牌）。
 */
export function exportCompletionXls (params) {
  window.open(buildJavaDownloadUrl(completionUrl.exportXls, params), '_blank')
}

export { buildJavaDownloadUrl }

export default {
  completionUrl,
  buildJavaDownloadUrl,
  queryCompletionList,
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
  exportCompletionXls
}
