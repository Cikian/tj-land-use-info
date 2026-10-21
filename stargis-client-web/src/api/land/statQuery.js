import {
  buildJavaDownloadUrl,
  javaGetAction,
  javaPostAction
} from '@/api/manageJava'

/**
 * 查询统计（方案 2.3.1（五）5-01 / 5-02）接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.stat.query.controller.StatQueryController
 *       + org.jeecg.modules.land.stat.export.StatQueryExportServiceImpl
 *
 * 【为什么全部走 manageJava】
 * 本工程有两个后端：中台 4548（`domianURL`）与 Java 9802（`VUE_DATA_JAVA_URL`）。
 * `@/api/manage` 走中台，打到 4548 会 404 —— 只要接口是 jeecg（Spring Boot）提供的，
 * 就必须用 `manageJava`（见 AGENTS.md §3.6 第 2 条）。
 *
 * 【两套查询入口，一个口径】
 *   GET  /land · /facility            基础条件（地块名称/区划/年份/受让人/项目分类…）
 *   POST /page                       高级查询（field / operator / value / relation 数组）
 * 两者最终都落到同一个 Service，所以「基础查询」和「高级查询」的数字不可能分叉。
 */
export const statQueryUrl = {
  columns: '/land/stat/query/columns',
  options: '/land/stat/query/options',
  land: '/land/stat/query/land',
  landStats: '/land/stat/query/land/stats',
  landDetail: '/land/stat/query/land/detail',
  facility: '/land/stat/query/facility',
  facilityStats: '/land/stat/query/facility/stats',
  page: '/land/stat/query/page',
  summary: '/land/stat/query/summary',
  exportXls: '/land/stat/query/exportXls'
}

/**
 * 字段元数据（条件下拉 / 自定义列 / 导出列共用）
 * @param {'land'|'facility'} dataType
 * @returns {Promise} data 为 QueryColumnVO[]：{ key, label, type, unit, defaultColumn, operators }
 */
export function queryColumns (dataType) {
  return javaGetAction(statQueryUrl.columns, { dataType })
}

/**
 * 条件下拉的候选值（旧系统 queryFieldContent 的去重值清单）
 * @param {'land'|'facility'} dataType
 * @returns {Promise} data 形如 { dkmc: [], xzqh: [], srr: [], crsjYears: [] }
 */
export function queryOptions (dataType) {
  return javaGetAction(statQueryUrl.options, { dataType })
}

/**
 * 经营性用地分页明细
 * @param {object} params 已拍平的条件（多值用逗号连接）
 */
export function queryLandPage (params) {
  return javaGetAction(statQueryUrl.land, params)
}

/** 经营性用地「分析结果」 */
export function queryLandStats (params) {
  return javaGetAction(statQueryUrl.landStats, params)
}

/** 地块配套详情（点结果行里的地块编号触发） */
export function queryLandDetail (crzdbh) {
  return javaGetAction(statQueryUrl.landDetail, { crzdbh })
}

/** 用地配套项目分页明细 */
export function queryFacilityPage (params) {
  return javaGetAction(statQueryUrl.facility, params)
}

/** 用地配套项目「分析结果」 */
export function queryFacilityStats (params) {
  return javaGetAction(statQueryUrl.facilityStats, params)
}

/**
 * 高级查询分页明细
 * @param {object} payload { dataType, conditions: [{field,operator,value,relation}], columns, pageNo, pageSize }
 */
export function queryAdvancedPage (payload) {
  return javaPostAction(statQueryUrl.page, payload)
}

/** 高级查询「分析结果」 */
export function queryAdvancedSummary (payload) {
  return javaPostAction(statQueryUrl.summary, payload)
}

/**
 * 导出地址（Excel：结果表 + 统计指标两个 sheet）。
 *
 * ★ 导出走 `window.open(带 token 的下载地址)`，与档案导出同一套；
 *   因此条件必须能塞进查询串 —— 高级查询条件用 `conditionsJson`（JSON 字符串）。
 *
 * @param {object} params { dataType, ...条件, columns: 'a,b,c', conditionsJson }
 */
export function buildExportUrl (params) {
  return buildJavaDownloadUrl(statQueryUrl.exportXls, params)
}
