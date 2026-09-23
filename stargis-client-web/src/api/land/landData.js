import { javaDeleteAction, javaGetAction, javaPostAction, javaPutAction } from '@/api/manageJava'

/**
 * 数据管理 · 宗地 / 配套项目 基础查询接口
 *
 * 后端：org.jeecg.modules.land.data.controller.LandDataController
 * 权限：land:archive:* / land:docReceive:* / land:docSend:*（任一即可）
 *
 * 【重要】接口在 **Java 业务后端**（window._CONFIG.VUE_DATA_JAVA_URL），
 * 必须用 `@/api/manageJava`，不能用走中台的 `@/api/manage`。
 *
 * 用途：档案管理与收发文管理的「两级联动下拉」——
 *   第一步 选出让宗地（t_land）
 *   第二步 联动选该宗地下的配套项目（xj_kjkfb_supporting_facilities）
 *
 * 与 admin-client/src/api/land/landData.js 一一对应（共用同一套后端）。
 */
export const landDataUrl = {
  landOptions: '/land/data/land/options',
  landById: '/land/data/land/queryById',
  xzqhOptions: '/land/data/land/xzqhOptions',
  facilityOptions: '/land/data/facility/options',
  facilitySearch: '/land/data/facility/search',
  facilityById: '/land/data/facility/queryById',
  landDashboard: '/land/data/dashboard',
  facilityDashboard: '/land/data/facility/dashboard',
  warningDetail: '/land/data/facility/warning/detail',
  landList: '/land/data/land/list',
  landAdd: '/land/data/land/add',
  landEdit: '/land/data/land/edit',
  landDelete: '/land/data/land/delete'
}

/**
 * 宗地下拉（远程搜索）
 * @param {{keyword?: string, xzqh?: string, limit?: number}} params
 */
export function queryLandOptions (params) {
  return javaGetAction(landDataUrl.landOptions, params)
}

/**
 * 宗地详情（选中后带出地块名称、行政区划、项目分类等）
 * @param {{id?: string, crzdbh?: string}} params
 */
export function queryLandById (params) {
  return javaGetAction(landDataUrl.landById, params)
}

/** 行政区划下拉（由已有宗地数据聚合，无需单独维护字典） */
export function queryXzqhOptions () {
  return javaGetAction(landDataUrl.xzqhOptions, {})
}

/**
 * 配套项目下拉（必须先选宗地）
 * @param {{crzdbh: string, keyword?: string, limit?: number}} params
 */
export function queryFacilityOptions (params) {
  return javaGetAction(landDataUrl.facilityOptions, params)
}

/**
 * 配套项目全局搜索（不限宗地，供档案查询条件使用）
 * @param {{keyword?: string, limit?: number}} params
 */
export function searchFacilityOptions (params) {
  return javaGetAction(landDataUrl.facilitySearch, params)
}

/** 配套项目详情 */
export function queryFacilityById (id) {
  return javaGetAction(landDataUrl.facilityById, { id })
}

/** 首页宗地统计与行政区排行。 */
export function queryLandDashboard () {
  return javaGetAction(landDataUrl.landDashboard, {}, window._CONFIG.VUE_DATA_JAVA_URL)
}

/** 首页配套统计、行政区排行与预警。 */
export function queryFacilityDashboard () {
  return javaGetAction(landDataUrl.facilityDashboard, {}, window._CONFIG.VUE_DATA_JAVA_URL)
}

/** 首页某个行政区的预警配套明细。 */
export function queryWarningDetail (params) {
  return javaGetAction(landDataUrl.warningDetail, params)
}

/** 经营性用地分页列表。 */
export function queryLandList (params) {
  return javaGetAction(landDataUrl.landList, params)
}

/** 新增经营性用地。出让宗地编号必填且不可重复。 */
export function addLand (data) {
  return javaPostAction(landDataUrl.landAdd, data)
}

/** 编辑经营性用地。 */
export function editLand (data) {
  return javaPutAction(landDataUrl.landEdit, data)
}

/** 删除经营性用地（逻辑删除）。 */
export function deleteLand (id) {
  return javaDeleteAction(landDataUrl.landDelete, { id })
}

export default {
  landDataUrl,
  queryLandOptions,
  queryLandById,
  queryXzqhOptions,
  queryFacilityOptions,
  searchFacilityOptions,
  queryFacilityById,
  queryLandDashboard,
  queryFacilityDashboard,
  queryWarningDetail,
  queryLandList,
  addLand,
  editLand,
  deleteLand
}
