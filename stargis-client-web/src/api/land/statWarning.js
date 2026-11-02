import {
  buildJavaDownloadUrl,
  javaGetAction,
  javaPostAction
} from '@/api/manageJava'

/**
 * 分级预警（D-603）· 大屏端「预警信息查询」接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.stat.warning.controller.WarningController
 *       统一前缀 `/land/stat/warning`，契约见 `docs/分级预警-实现契约.md` §5。
 *
 * 【为什么全部走 manageJava】
 * 本工程有三个后端地址（见 public/static/config.js）：中台 4548、
 * Java 业务后端 9802/api、JNI 分析服务 38190。`@/api/manage` 走 `domianURL`（中台），
 * 打到 4548 会 404；本文件的接口全部由 jeecg（Spring Boot）提供，
 * 所以一律用 `@/api/manageJava`（AGENTS.md §3.6 第 2 条）。
 *
 * ★ **不要显式传 baseURL**：基址由 `javaBaseUrl()`（VUE_BUSINESS_JAVA_URL）统一给出。
 *   历史上把 `VUE_DATA_JAVA_URL` 当业务基址传的地方，现在都会打到 38190 的 JNI 服务上。
 *
 * 【字段名逐字对齐契约】yjType / levelId / levelCode / xzqh / dkmc / ptxmmc / crzdbh /
 *   keyword / pageNo / pageSize —— 这里不再做别名映射，前后端同名，漂移一眼能看出来。
 */
export const statWarningUrl = {
  list: '/land/stat/warning/list',
  summary: '/land/stat/warning/summary',
  options: '/land/stat/warning/options',
  recalc: '/land/stat/warning/recalc',
  export: '/land/stat/warning/export'
}

/**
 * 预警记录分页
 * @param {object} params 已拍平的条件（空值不下发）
 * @returns {Promise} result 为 jeecg 的 IPage：{ records, total, size, current }
 *   （★ 是 records/total，不是 rows/total —— 与查询统计域 `/land/stat/query/*` 的返回体不同）
 */
export function listWarning (params) {
  return javaGetAction(statWarningUrl.list, params)
}

/**
 * 按等级 / 分类汇总（筛选条件与 list 完全一致，分页参数会被后端忽略）
 * result 为 WarningSummaryVO：{ total, byLevel: [{ levelId, levelCode, levelName, count }],
 *   byType: [{ yjType, count }], calcTime }
 */
export function summaryWarning (params) {
  return javaGetAction(statWarningUrl.summary, params)
}

/**
 * 下拉选项：levels（等级，含 minDays/maxDays）/ yjTypes（三分类）/ problemTypes（字典 land_process_issue）
 * 三个页签下拉与详情里的「问题类型」都以它为准，前端不另抄一份枚举。
 */
export function warningOptions () {
  return javaGetAction(statWarningUrl.options, {})
}

/**
 * 手动重算预警（大屏页签上的「重新计算」）
 * @param {object} data `{ ptId }`；ptId 为 null / 空 = 全量重算（只扫有环节进度的配套）
 * @returns {Promise} result 为 RecalcResultVO：{ created, updated, removed, scannedFacilities,
 *   scannedProcesses, levelMissing, elapsedMs, calcTime }
 */
export function recalcWarning (data) {
  return javaPostAction(statWarningUrl.recalc, data || {})
}

/**
 * 导出地址（xlsx 文件流，忽略分页）
 *
 * ★ 返回的是**完整 URL 字符串**而不是 Promise：调用方 `window.open(...)` 直接下载。
 *   原生下载带不了自定义请求头，`buildJavaDownloadUrl` 会把 jeecg 令牌拼进查询串
 *   （Java 端 JwtFilter 支持 query 参数 token），与档案 / 查询统计导出同一套做法；
 *   所以**不要**改成 axios 取 blob 自己存盘 —— 那样会绕过这个令牌机制。
 */
export function exportWarning (params) {
  return buildJavaDownloadUrl(statWarningUrl.export, params)
}
